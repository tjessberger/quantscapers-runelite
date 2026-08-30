package com.quantscapers.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * Pure, snapshot-only market briefing. This deliberately uses the feeds already
 * loaded by the regular poll; the Overview must add context without adding API
 * traffic or duplicating execution-focused tools.
 */
public final class MarketIntelligence {
    public static final long MIN_LIQUID_DAILY_VOLUME = 1_000L;
    private static final double DIRECTIONAL_THRESHOLD_PCT = 1.0;
    private static final double MOMENTUM_THRESHOLD_PCT = 2.0;
    private static final int MAX_INSIGHTS = 3;

    private MarketIntelligence() {}

    public static Briefing summarize(List<AnalyzedItem> market) {
        return summarize(market, Parameters.defaults());
    }

    public static Briefing summarize(List<AnalyzedItem> market, Parameters parameters) {
        Parameters filters = parameters == null ? Parameters.defaults() : parameters;
        if (market == null || market.isEmpty()) {
            return Briefing.empty();
        }

        List<SignalRow> rows = market.stream()
            .filter(MarketIntelligence::hasUsableBaseline)
            .map(MarketIntelligence::row)
            .collect(Collectors.toList());
        List<SignalRow> liquid = rows.stream()
            .filter(r -> r.getVolume() >= filters.getMinDailyVolume())
            .filter(r -> currentMidpoint(r.getItem()) >= filters.getMinItemValue())
            .collect(Collectors.toList());

        if (liquid.isEmpty()) {
            return Briefing.builder()
                .totalItems(rows.size())
                .liquidItems(0)
                .regime("INSUFFICIENT LIQUID DATA")
                .summary("No items match the selected thresholds.")
                .gainers(Collections.emptyList())
                .losers(Collections.emptyList())
                .mostActive(Collections.emptyList())
                .heating(Collections.emptyList())
                .cooling(Collections.emptyList())
                .build();
        }

        int stale = (int) liquid.stream().filter(r -> VerdictEngine.isStaleQuote(r.getItem())).count();
        List<SignalRow> freshLiquid = liquid.stream()
            .filter(r -> !VerdictEngine.isStaleQuote(r.getItem()))
            .collect(Collectors.toList());
        int rising = (int) freshLiquid.stream().filter(r -> r.getMovePct() >= filters.getMinMovePct()).count();
        int falling = (int) freshLiquid.stream().filter(r -> r.getMovePct() <= -filters.getMinMovePct()).count();
        double risingShare = freshLiquid.isEmpty() ? 0 : rising / (double) freshLiquid.size();
        double fallingShare = freshLiquid.isEmpty() ? 0 : falling / (double) freshLiquid.size();
        String regime = freshLiquid.isEmpty() ? "NO FRESH MARKET DATA"
            : risingShare >= 0.60 ? "BROADLY RISING"
            : fallingShare >= 0.60 ? "BROADLY FALLING" : "MIXED MARKET";
        long totalVolume = liquid.stream().mapToLong(SignalRow::getVolume).sum();
        long averageVolume = Math.round(totalVolume / (double) liquid.size());

        List<SignalRow> movers = sorted(freshLiquid, Comparator.comparingDouble(SignalRow::getMovePct).reversed());
        List<SignalRow> gainers = take(movers.stream()
            .filter(r -> r.getMovePct() >= filters.getMinMovePct())
            .collect(Collectors.toList()));
        List<SignalRow> losers = take(freshLiquid.stream()
            .filter(r -> r.getMovePct() <= -filters.getMinMovePct())
            .sorted(Comparator.comparingDouble(SignalRow::getMovePct))
            .collect(Collectors.toList()));
        List<SignalRow> active = take(sorted(freshLiquid, Comparator.comparingLong(SignalRow::getVolume).reversed()));
        double momentumThreshold = Math.max(MOMENTUM_THRESHOLD_PCT, filters.getMinMovePct());
        List<SignalRow> heating = take(freshLiquid.stream()
            .filter(r -> r.getMomentumPct() >= momentumThreshold)
            .sorted(Comparator.comparingDouble(SignalRow::getMomentumPct).reversed())
            .collect(Collectors.toList()));
        List<SignalRow> cooling = take(freshLiquid.stream()
            .filter(r -> r.getMomentumPct() <= -momentumThreshold)
            .sorted(Comparator.comparingDouble(SignalRow::getMomentumPct))
            .collect(Collectors.toList()));

        String summary = String.format(Locale.ROOT, "%s · %d%% rising · %d liquid · %d stale",
            regime, Math.round(risingShare * 100), liquid.size(), stale);
        return Briefing.builder()
            .totalItems(rows.size())
            .liquidItems(liquid.size())
            .risingItems(rising)
            .fallingItems(falling)
            .staleItems(stale)
            .averageDailyVolume(averageVolume)
            .regime(regime)
            .summary(summary)
            .gainers(gainers)
            .losers(losers)
            .mostActive(active)
            .heating(heating)
            .cooling(cooling)
            .build();
    }

    private static boolean hasUsableBaseline(AnalyzedItem item) {
        double baseline = midpoint(item.getAvg24hHigh(), item.getAvg24hLow());
        double current = currentMidpoint(item);
        return baseline > 0 && Double.isFinite(baseline)
            && current > 0 && Double.isFinite(current);
    }

    private static SignalRow row(AnalyzedItem item) {
        double current = currentMidpoint(item);
        double baseline = midpoint(item.getAvg24hHigh(), item.getAvg24hLow());
        double movePct = ((current / baseline) - 1) * 100.0;
        double momentumPct = 0;
        if (item.getVol5m() != null && item.getVol5m() > 0
            && item.getAvg5mHigh() > 0 && item.getAvg24hHigh() > 0) {
            momentumPct = ((item.getAvg5mHigh() / item.getAvg24hHigh()) - 1) * 100.0;
        }
        return new SignalRow(item, movePct, momentumPct, item.getVol24h());
    }

    private static double midpoint(double high, double low) {
        return (high + low) / 2.0;
    }

    private static double currentMidpoint(AnalyzedItem item) {
        return midpoint(item.getHigh(), item.getLow());
    }

    private static List<SignalRow> sorted(List<SignalRow> input, Comparator<SignalRow> comparator) {
        List<SignalRow> copy = new ArrayList<>(input);
        copy.sort(comparator.thenComparing(r -> r.getItem().getName(), String.CASE_INSENSITIVE_ORDER));
        return copy;
    }

    private static List<SignalRow> take(List<SignalRow> input) {
        return input.size() <= MAX_INSIGHTS
            ? Collections.unmodifiableList(new ArrayList<>(input))
            : Collections.unmodifiableList(new ArrayList<>(input.subList(0, MAX_INSIGHTS)));
    }

    @Value
    public static class SignalRow {
        AnalyzedItem item;
        double movePct;
        double momentumPct;
        long volume;
    }

    /** User-facing Overview thresholds. These are local UI filters, not API parameters. */
    @Value
    public static class Parameters {
        long minDailyVolume;
        int minItemValue;
        double minMovePct;

        public static Parameters defaults() {
            return new Parameters(MIN_LIQUID_DAILY_VOLUME, 0, DIRECTIONAL_THRESHOLD_PCT);
        }
    }

    @Value
    @lombok.Builder
    public static class Briefing {
        int totalItems;
        int liquidItems;
        int risingItems;
        int fallingItems;
        int staleItems;
        long averageDailyVolume;
        String regime;
        String summary;
        List<SignalRow> gainers;
        List<SignalRow> losers;
        List<SignalRow> mostActive;
        List<SignalRow> heating;
        List<SignalRow> cooling;

        public static Briefing empty() {
            return Briefing.builder()
                .regime("NO MARKET DATA")
                .summary("Market data appears after a successful refresh.")
                .gainers(Collections.emptyList())
                .losers(Collections.emptyList())
                .mostActive(Collections.emptyList())
                .heating(Collections.emptyList())
                .cooling(Collections.emptyList())
                .build();
        }
    }
}
