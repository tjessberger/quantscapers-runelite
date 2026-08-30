package com.quantscapers.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class MarketIntelligenceTest {
    private static AnalyzedItem item(String name, int high, int low, double avgHigh, double avgLow,
                                     long volume, Long vol5m, double avg5mHigh) {
        return item(name, high, low, avgHigh, avgLow, volume, vol5m, avg5mHigh, 0L, 0L);
    }

    private static AnalyzedItem item(String name, int high, int low, double avgHigh, double avgLow,
                                     long volume, Long vol5m, double avg5mHigh,
                                     long analyzedAtMs, long quoteTimeSec) {
        return AnalyzedItem.builder()
            .id(name.hashCode())
            .name(name)
            .high(high)
            .low(low)
            .avg24hHigh(avgHigh)
            .avg24hLow(avgLow)
            .vol24h(volume)
            .vol5m(vol5m)
            .avg5mHigh(avg5mHigh)
            .analyzedAtMs(analyzedAtMs)
            .highTime(quoteTimeSec)
            .lowTime(quoteTimeSec)
            .build();
    }

    @Test
    public void summarizesLiquidBreadthAndCategories() {
        AnalyzedItem gainer = item("Gainer", 120, 100, 110, 100, 10_000L, 100L, 120);
        AnalyzedItem loser = item("Loser", 90, 80, 100, 100, 8_000L, 100L, 90);
        AnalyzedItem active = item("Active", 200, 200, 200, 200, 50_000L, 100L, 210);
        AnalyzedItem thin = item("Thin", 500, 400, 450, 400, 999L, 100L, 500);

        MarketIntelligence.Briefing briefing = MarketIntelligence.summarize(
            Arrays.asList(gainer, loser, active, thin));

        assertEquals(4, briefing.getTotalItems());
        assertEquals(3, briefing.getLiquidItems());
        assertEquals(1, briefing.getRisingItems());
        assertEquals(1, briefing.getFallingItems());
        assertEquals("MIXED MARKET", briefing.getRegime());
        assertEquals("Gainer", briefing.getGainers().get(0).getItem().getName());
        assertEquals("Loser", briefing.getLosers().get(0).getItem().getName());
        assertEquals("Active", briefing.getMostActive().get(0).getItem().getName());
        assertTrue(briefing.getHeating().stream().anyMatch(r -> r.getItem().getName().equals("Active")));
    }

    @Test
    public void emptyAndIlliquidSnapshotsAreExplicit() {
        assertEquals("NO MARKET DATA", MarketIntelligence.summarize(Collections.emptyList()).getRegime());
        AnalyzedItem thin = item("Thin", 100, 90, 100, 90, 999L, null, 0);
        MarketIntelligence.Briefing briefing = MarketIntelligence.summarize(Collections.singletonList(thin));
        assertEquals(0, briefing.getLiquidItems());
        assertEquals("INSUFFICIENT LIQUID DATA", briefing.getRegime());
    }

    @Test
    public void staleLiquidItemsAreCountedButDoNotDriveSignals() {
        AnalyzedItem stale = item("Stale Gainer", 200, 200, 100, 100, 20_000L,
            100L, 200, 10_000_000L, 1L);
        MarketIntelligence.Briefing briefing = MarketIntelligence.summarize(Collections.singletonList(stale));
        assertEquals(1, briefing.getLiquidItems());
        assertEquals(1, briefing.getStaleItems());
        assertEquals(0, briefing.getRisingItems());
        assertEquals(0, briefing.getFallingItems());
        assertEquals("NO FRESH MARKET DATA", briefing.getRegime());
        assertTrue(briefing.getGainers().isEmpty());
        assertTrue(briefing.getMostActive().isEmpty());
    }

    @Test
    public void customThresholdsRemoveLowValueAndLowVolumeItems() {
        AnalyzedItem target = item("Target", 1_500, 1_500, 1_000, 1_000,
            25_000L, 100L, 1_100);
        AnalyzedItem lowValue = item("Low value", 50, 50, 50, 50,
            25_000L, 100L, 55);
        AnalyzedItem lowVolume = item("Low volume", 2_000, 2_000, 1_000, 1_000,
            500L, 100L, 1_100);

        MarketIntelligence.Briefing briefing = MarketIntelligence.summarize(
            Arrays.asList(target, lowValue, lowVolume),
            new MarketIntelligence.Parameters(10_000L, 1_000, 5.0));

        assertEquals(1, briefing.getLiquidItems());
        assertEquals("Target", briefing.getMostActive().get(0).getItem().getName());
        assertEquals("Target", briefing.getGainers().get(0).getItem().getName());
    }
}
