package com.quantscapers.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VerdictEngineTest {

    private static final long NOW_MS = 1_800_000_000_000L;
    private static final long NOW_SEC = NOW_MS / 1000;

    private static AnalyzedItem.AnalyzedItemBuilder base() {
        // A clean, fast, profitable flip: fresh quotes, eft well under 40, roi well over 2%.
        return AnalyzedItem.builder()
            .id(1).name("Test Item").limit(1000).highalch(0)
            .high(1100).low(1000)
            .highTime(NOW_SEC - 60).lowTime(NOW_SEC - 60)
            .tax(22).roi(7.8).eft(10).vol24h(50_000)
            .vol5m(null).avg5mHigh(0).avg5mLow(0)
            .avg24hHigh(1100).avg24hLow(1000)
            .realisticProfit(78_000L).gpHour(500_000).fullLimitCost(1_000_000L)
            .alchMarginPer(0).alchProfit(0L).alchROI(0).alchGpHour(0)
            .analyzedAtMs(NOW_MS);
    }

    @Test
    public void trap_flaggedWhenSpreadFarExceedsNormOnThinVolume() {
        AnalyzedItem it = base().vol24h(500).avg24hHigh(1010).avg24hLow(1000) // avgSpread=10
            .high(1100).low(1000) // spread 100 > 10*3
            .build();
        assertTrue(VerdictEngine.isPossibleTrap(it));
        assertEquals(Verdict.Rating.AVOID, VerdictEngine.verdict(it, null).getRating());
    }

    @Test
    public void trap_notFlaggedWhenVolumeAtOrAbove1000() {
        AnalyzedItem it = base().vol24h(1000).avg24hHigh(1010).avg24hLow(1000)
            .high(1100).low(1000)
            .build();
        assertFalse(VerdictEngine.isPossibleTrap(it));
    }

    @Test
    public void stale_flaggedWhenBothTimestampsOver30MinOld() {
        AnalyzedItem it = base().highTime(NOW_SEC - 2000).lowTime(NOW_SEC - 2000).build();
        assertTrue(VerdictEngine.isStaleQuote(it));
        assertEquals(Verdict.Rating.AVOID, VerdictEngine.verdict(it, null).getRating());
    }

    @Test
    public void stale_notFlaggedWhenATimestampIsMissing() {
        AnalyzedItem it = base().highTime(0).lowTime(NOW_SEC - 2000).build();
        assertNull(VerdictEngine.quoteAgeSec(it));
        assertFalse(VerdictEngine.isStaleQuote(it));
    }

    @Test
    public void riskyFlag_vol5mZero_triggersNothingTradedReason() {
        AnalyzedItem it = base().vol5m(0L).build();
        Verdict v = VerdictEngine.verdict(it, null);
        assertEquals(Verdict.Rating.RISKY, v.getRating());
        assertTrue(v.getReason().startsWith("Nothing traded in the last 5 minutes"));
    }

    @Test
    public void riskyFlag_vol5mNull_doesNotTriggerNothingTradedReason_staysBuy() {
        AnalyzedItem it = base().vol5m(null).build();
        Verdict v = VerdictEngine.verdict(it, null);
        assertEquals(Verdict.Rating.BUY, v.getRating());
    }

    @Test
    public void slowFill_over240MinutesEft_triggersRisky() {
        AnalyzedItem it = base().eft(9999).build();
        Verdict v = VerdictEngine.verdict(it, null);
        assertEquals(Verdict.Rating.RISKY, v.getRating());
        assertEquals("Slow fill — roughly 167h per side.", v.getReason());
    }

    @Test
    public void noProfitAfterTax_isAvoid() {
        AnalyzedItem it = base().roi(-1).realisticProfit(-100L).build();
        assertEquals(Verdict.Rating.AVOID, VerdictEngine.verdict(it, null).getRating());
    }

    @Test
    public void notFreshEnoughForBuy_fallsToDecent() {
        // age 400s > 300s cutoff, but otherwise clean -> DECENT, not BUY, not RISKY.
        AnalyzedItem it = base().highTime(NOW_SEC - 400).lowTime(NOW_SEC - 400).build();
        Verdict v = VerdictEngine.verdict(it, null);
        assertEquals(Verdict.Rating.DECENT, v.getRating());
    }

    @Test
    public void isBestBet_requiresAllGates() {
        AnalyzedItem good = base().eft(20).realisticProfit(2_000_000L).roi(5).build();
        assertTrue(VerdictEngine.isBestBet(good));

        AnalyzedItem tooSlow = base().eft(41).realisticProfit(2_000_000L).roi(5).build();
        assertFalse(VerdictEngine.isBestBet(tooSlow));
    }
}
