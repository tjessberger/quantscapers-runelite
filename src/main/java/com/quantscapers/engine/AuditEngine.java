package com.quantscapers.engine;

import com.quantscapers.api.TimeseriesPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Price history audit: hit rate against 1h timeseries history plus a 7-day
 * margin-stability grade. Direct transcription of verifyNode() in
 * engine.html. Pure function — network fetch happens elsewhere; on fetch
 * failure the caller should use {@link #failed(long)} instead of calling
 * {@link #audit}.
 */
public final class AuditEngine {
    private AuditEngine() {}

    public static AuditResult audit(List<TimeseriesPoint> points, int targetSellPrice, long nowMs) {
        int n = points.size();
        List<TimeseriesPoint> last24 = points.subList(Math.max(0, n - 24), n);
        List<TimeseriesPoint> last168 = points.subList(Math.max(0, n - 168), n);
        List<TimeseriesPoint> prevWindow = points.subList(
            Math.max(0, n - 48), Math.max(0, n - 24));

        double threshold = targetSellPrice * 0.995;
        int hits1d = countHits(last24, threshold);
        int hits7d = countHits(last168, threshold);
        int likelihood = (int) Math.min(100, Math.round((hits1d / 24.0) * 60 + (hits7d / 168.0) * 40));

        double avgNow = last24.stream().mapToDouble(AuditEngine::highOrZero).sum() / Math.max(1, last24.size());
        double avgPrev = prevWindow.stream().mapToDouble(AuditEngine::highOrZero).sum() / 24.0;
        double trend = avgPrev > 0 ? ((avgNow - avgPrev) / avgPrev) * 100 : 0;

        List<Double> marginSamples = marginSamples(last168);

        double[] sparkData = last24.stream()
            .mapToDouble(AuditEngine::highOrZero)
            .filter(v -> v > 0)
            .toArray();

        return AuditResult.builder()
            .ts(nowMs)
            .hits1d(hits1d)
            .hits7d(hits7d)
            .trend(trend)
            .verdict(verdict(hits1d, hits7d, trend))
            .likelihood(Math.min(100, likelihood))
            .stabilityGrade(stabilityGrade(marginSamples))
            .sparkData(sparkData)
            .failed(false)
            .build();
    }

    public static AuditResult failed(long nowMs) {
        return AuditResult.builder()
            .ts(nowMs)
            .verdict("Audit failed")
            .likelihood(0)
            .hits1d(0)
            .hits7d(0)
            .failed(true)
            .build();
    }

    private static int countHits(List<TimeseriesPoint> points, double threshold) {
        return (int) points.stream()
            .filter(p -> p.getAvgHighPrice() != null && p.getAvgHighPrice() >= threshold)
            .count();
    }

    private static String verdict(int hits1d, int hits7d, double trend) {
        if (hits1d >= 8 && trend > 0.5) return "Strong move";
        if (trend < -1.5) return "Slipping";
        if (hits1d == 0 && hits7d > 2) return "Peak or stagnant";
        if (hits1d > 15) return "Highly active";
        return "Stable";
    }

    private static List<Double> marginSamples(List<TimeseriesPoint> points) {
        List<Double> margins = new ArrayList<>();
        for (TimeseriesPoint point : points) {
            if (point.getAvgHighPrice() == null || point.getAvgLowPrice() == null) continue;
            double high = point.getAvgHighPrice();
            double tax = Math.min(Math.floor(high * Constants.TAX_RATE), Constants.TAX_CAP_GP);
            margins.add(high - point.getAvgLowPrice() - tax);
        }
        return margins;
    }

    private static Character stabilityGrade(List<Double> margins) {
        if (margins.size() < 24) return null;
        double average = margins.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double cv = coefficientOfVariation(margins, average);
        if (cv < 0.15) return 'A';
        if (cv < 0.30) return 'B';
        if (cv < 0.50) return 'C';
        if (cv < 0.80) return 'D';
        return 'F';
    }

    private static double coefficientOfVariation(List<Double> values, double average) {
        if (average <= 0) return 9;
        double variance = values.stream()
            .mapToDouble(value -> Math.pow(value - average, 2))
            .average()
            .orElse(0);
        return Math.sqrt(variance) / average;
    }

    private static double highOrZero(TimeseriesPoint p) {
        return p.getAvgHighPrice() != null ? p.getAvgHighPrice() : 0;
    }
}
