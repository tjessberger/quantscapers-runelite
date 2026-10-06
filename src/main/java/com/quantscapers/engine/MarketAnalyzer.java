package com.quantscapers.engine;

import com.quantscapers.api.MappingItem;
import com.quantscapers.api.PriceQuote;
import com.quantscapers.api.VolumeStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure pipeline: merges the four wiki feeds into a list of {@link
 * AnalyzedItem}. No I/O, no side effects — every formula here is a direct
 * transcription of the pollMarketData analysis block in engine.html v79.
 * Do not change a formula without updating the corresponding value in
 * engine.html; the two must stay numerically identical.
 */
public final class MarketAnalyzer {
    private MarketAnalyzer() {}

    public static List<AnalyzedItem> analyze(
        List<MappingItem> mapping,
        Map<Integer, PriceQuote> latest,
        Map<Integer, VolumeStats> stats24h,
        Map<Integer, VolumeStats> stats5m, // may be null - feed is optional
        long natureRuneGp,
        long nowMs
    ) {
        List<AnalyzedItem> out = new ArrayList<>(mapping.size());
        for (MappingItem item : mapping) {
            PriceQuote p = latest == null ? null : latest.get(item.getId());
            if (p == null || p.getHigh() == null || p.getLow() == null) {
                continue;
            }

            long high = p.getHigh();
            long low = p.getLow();
            int limit = item.limitOrZero();
            int highalch = item.highalchOrZero();

            long tax = Constants.geTax(high);
            long margin = high - low - tax;

            VolumeStats s = stats24h == null ? null : stats24h.get(item.getId());
            long vol24h = s == null ? 0 : s.getHighPriceVolume() + s.getLowPriceVolume();
            double eft = vol24h <= 0 ? 9999.0 : limit / (vol24h / (double) Constants.MINUTES_IN_DAY);

            long effectiveQty = Math.min(limit, vol24h);
            long realisticProfit = (long) margin * effectiveQty;

            double roundTripHrs = Math.max(2 * eft, 5) / 60.0;
            double gpHour = realisticProfit / roundTripHrs;
            double roi = low == 0 ? 0 : (margin / (double) low) * 100;
            long fullLimitCost = (long) low * limit;

            // Strict parity with engine.html's `s?.avgHighPrice || p.high`: JS `||`
            // also falls back on 0, not just null/undefined. The wiki emits null for
            // no-trade windows so 0 shouldn't occur, but the two frontends must agree
            // on the same input no matter what the API sends.
            double avg24hHigh = (s != null && s.getAvgHighPrice() != null && s.getAvgHighPrice() != 0)
                ? s.getAvgHighPrice() : high;
            double avg24hLow = (s != null && s.getAvgLowPrice() != null && s.getAvgLowPrice() != 0)
                ? s.getAvgLowPrice() : low;

            VolumeStats f = stats5m == null ? null : stats5m.get(item.getId());
            Long vol5m = f == null ? null : f.getHighPriceVolume() + f.getLowPriceVolume();
            double avg5mHigh = (f != null && f.getAvgHighPrice() != null) ? f.getAvgHighPrice() : 0;
            double avg5mLow = (f != null && f.getAvgLowPrice() != null) ? f.getAvgLowPrice() : 0;

            long alchMarginPer = highalch > 0 ? highalch - low - natureRuneGp : 0;
            long alchProfit = alchMarginPer > 0 ? (long) alchMarginPer * effectiveQty : 0;
            double alchROI = (alchMarginPer > 0 && low > 0) ? alchMarginPer * 100.0 / low : 0;
            double alchGpHour = alchProfit / roundTripHrs;

            out.add(AnalyzedItem.builder()
                .id(item.getId())
                .name(item.getName())
                .limit(limit)
                .highalch(highalch)
                .high(high)
                .low(low)
                .highTime(p.getHighTime() == null ? 0 : p.getHighTime())
                .lowTime(p.getLowTime() == null ? 0 : p.getLowTime())
                .tax(tax)
                .roi(roi)
                .eft(eft)
                .vol24h(vol24h)
                .vol5m(vol5m)
                .avg5mHigh(avg5mHigh)
                .avg5mLow(avg5mLow)
                .avg24hHigh(avg24hHigh)
                .avg24hLow(avg24hLow)
                .realisticProfit(realisticProfit)
                .gpHour(gpHour)
                .fullLimitCost(fullLimitCost)
                .alchMarginPer(alchMarginPer)
                .alchProfit(alchProfit)
                .alchROI(alchROI)
                .alchGpHour(alchGpHour)
                .analyzedAtMs(nowMs)
                .build());
        }
        return out;
    }

    /**
     * Extracts the live nature rune buy price from /latest, falling back when
     * absent. Mirrors engine.html's `latest.data?.[561]?.low || 195`, where a
     * 0 price (JS-falsy) also falls back — not just a missing one.
     */
    public static long extractNatureRuneGp(Map<Integer, PriceQuote> latest) {
        if (latest == null) {
            return Constants.NATURE_RUNE_FALLBACK_GP;
        }
        PriceQuote q = latest.get(Constants.NATURE_RUNE_ID);
        if (q == null || q.getLow() == null || q.getLow() == 0) {
            return Constants.NATURE_RUNE_FALLBACK_GP;
        }
        return q.getLow();
    }
}
