package com.quantscapers.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SuppressionEngineTest {

    // Trap gate: vol24h<1000, spread far above the item's own 24h norm -> AVOID
    // regardless of hist. This is the "upfront data marks AVOID" half of the gate.
    private static AnalyzedItem avoidTrapItem(long nowMs) {
        return AnalyzedItem.builder()
            .id(1).name("Trap item")
            .high(140).low(100)
            .highTime(nowMs / 1000).lowTime(nowMs / 1000)
            .tax(2)
            .roi(30).eft(10)
            .vol24h(500)
            .avg24hHigh(110).avg24hLow(100) // spread 10, actual spread 40 > 10*3
            .realisticProfit(1000)
            .gpHour(1000)
            .fullLimitCost(1000)
            .analyzedAtMs(nowMs)
            .build();
    }

    // Healthy, profitable, non-trap, non-stale item -> never AVOID regardless of hist.
    private static AnalyzedItem healthyItem(long nowMs) {
        return AnalyzedItem.builder()
            .id(2).name("Healthy item")
            .high(1087).low(1001)
            .highTime(nowMs / 1000).lowTime(nowMs / 1000)
            .tax(21)
            .roi(6.5).eft(10)
            .vol24h(1_200_000)
            .avg24hHigh(1080).avg24hLow(1000)
            .realisticProfit(520_000)
            .gpHour(1_625_000)
            .fullLimitCost(8_008_000)
            .analyzedAtMs(nowMs)
            .build();
    }

    private static AuditResult audit(long ts, int likelihood) {
        return AuditResult.builder().ts(ts).hits1d(0).hits7d(0).trend(0)
            .verdict("Stable").likelihood(likelihood).stabilityGrade(null)
            .sparkData(new double[0]).failed(false).build();
    }

    @Test
    public void avoidWithFreshLowHitRateAudit_confirmed() {
        long now = 1_000_000_000L;
        AuditResult hist = audit(now - 60_000, 10); // fresh, well under 25%
        assertTrue(SuppressionEngine.isConfirmedAvoid(avoidTrapItem(now), hist, now));
    }

    @Test
    public void avoidWithNoAudit_notConfirmed() {
        long now = 1_000_000_000L;
        assertFalse(SuppressionEngine.isConfirmedAvoid(avoidTrapItem(now), null, now));
    }

    @Test
    public void avoidWithStaleAudit_notConfirmed() {
        long now = 1_000_000_000L;
        AuditResult hist = audit(now - Constants.AUDIT_TTL_MS - 1000, 10); // just past TTL
        assertFalse(SuppressionEngine.isConfirmedAvoid(avoidTrapItem(now), hist, now));
    }

    @Test
    public void avoidWithHighHitRateAudit_notConfirmed() {
        long now = 1_000_000_000L;
        AuditResult hist = audit(now - 60_000, 80); // fresh but hit rate is fine
        assertFalse(SuppressionEngine.isConfirmedAvoid(avoidTrapItem(now), hist, now));
    }

    @Test
    public void nonAvoidItemWithLowHitRateAudit_notConfirmed() {
        long now = 1_000_000_000L;
        AuditResult hist = audit(now - 60_000, 10); // low hit rate, but item isn't AVOID
        assertFalse(SuppressionEngine.isConfirmedAvoid(healthyItem(now), hist, now));
    }

    @Test
    public void failedAudit_notConfirmed() {
        long now = 1_000_000_000L;
        AuditResult hist = AuditResult.builder().ts(now - 60_000).likelihood(0).failed(true)
            .sparkData(new double[0]).build();
        assertFalse(SuppressionEngine.isConfirmedAvoid(avoidTrapItem(now), hist, now));
    }

    @Test
    public void thresholdBoundary_exactlyAtThreshold_notConfirmed() {
        long now = 1_000_000_000L;
        AuditResult hist = audit(now - 60_000, Constants.SUPPRESSION_HIT_RATE_THRESHOLD);
        assertFalse(SuppressionEngine.isConfirmedAvoid(avoidTrapItem(now), hist, now));
    }

    @Test
    public void thresholdBoundary_oneBelowThreshold_confirmed() {
        long now = 1_000_000_000L;
        AuditResult hist = audit(now - 60_000, Constants.SUPPRESSION_HIT_RATE_THRESHOLD - 1);
        assertTrue(SuppressionEngine.isConfirmedAvoid(avoidTrapItem(now), hist, now));
    }
}
