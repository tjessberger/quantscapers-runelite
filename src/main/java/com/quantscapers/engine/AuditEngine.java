package com.quantscapers.engine;

import com.quantscapers.api.TimeseriesPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Deep-probe audit: hit rate against 1h timeseries history plus a 7-day
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
        int hits1d = (int) last24.stream()
            .filter(p -> p.getAvgHighPrice() != null && p.getAvgHighPrice() >= threshold)
            .count();
        int hits7d = (int) last168.stream()
            .filter(p -> p.getAvgHighPrice() != null && p.getAvgHighPrice() >= threshold)
            .count();
        int likelihood = (int) Math.min(100, Math.round((hits1d / 24.0) * 60 + (hits7d / 168.0) * 40));

        double avgNow = last24.stream().mapToDouble(AuditEngine::highOrZero).sum() / Math.max(1, last24.size());
        double avgPrev = prevWindow.stream().mapToDouble(AuditEngine::highOrZero).sum() / 24.0;
        double trend = avgPrev > 0 ? ((avgNow - avgPrev) / avgPrev) * 100 : 0;

        String verdict;
        if (hits1d >= 8 && trend > 0.5) {
            verdict = "Strong Move";
        } else if (trend < -1.5) {
            verdict = "Slipping";
        } else if (hits1d == 0 && hits7d > 2) {
            verdict = "Peak/Stagnant";
        } else if (hits1d > 15) {
            verdict = "Hyper Active";
        } else {
            verdict = "Stable";
        }

        List<Double> marginSamples = new ArrayList<>();
        for (TimeseriesPoint p : last168) {
            if (p.getAvgHighPrice() != null && p.getAvgLowPrice() != null) {
                double avgHigh = p.getAvgHighPrice();
                double avgLow = p.getAvgLowPrice();
                double tax = Math.min(Math.floor(avgHigh * Constants.TAX_RATE), Constants.TAX_CAP_GP);
                marginSamples.add(avgHigh - avgLow - tax);
            }
        }
        double avgM = marginSamples.isEmpty() ? 0
            : marginSamples.stream().mapToDouble(Double::doubleValue).sum() / marginSamples.size();
        double cv;
        if (avgM > 0) {
            double variance = marginSamples.stream()
                .mapToDouble(m -> Math.pow(m - avgM, 2))
                .sum() / marginSamples.size();
            cv = Math.sqrt(variance) / avgM;
        } else {
            cv = 9;
        }

        Character stabilityGrade = null;
        if (marginSamples.size() >= 24) {
            if (cv < 0.15) stabilityGrade = 'A';
            else if (cv < 0.30) stabilityGrade = 'B';
            else if (cv < 0.50) stabilityGrade = 'C';
            else if (cv < 0.80) stabilityGrade = 'D';
            else stabilityGrade = 'F';
        }

        double[] sparkData = last24.stream()
            .mapToDouble(AuditEngine::highOrZero)
            .filter(v -> v > 0)
            .toArray();

        return AuditResult.builder()
            .ts(nowMs)
            .hits1d(hits1d)
            .hits7d(hits7d)
            .trend(trend)
            .verdict(verdict)
            .likelihood(Math.min(100, likelihood))
            .stabilityGrade(stabilityGrade)
            .sparkData(sparkData)
            .failed(false)
            .build();
    }

    public static AuditResult failed(long nowMs) {
        return AuditResult.builder()
            .ts(nowMs)
            .verdict("Audit Failed")
            .likelihood(0)
            .hits1d(0)
            .hits7d(0)
            .failed(true)
            .build();
    }

    private static double highOrZero(TimeseriesPoint p) {
        return p.getAvgHighPrice() != null ? p.getAvgHighPrice() : 0;
    }
}
