package com.quantscapers.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.quantscapers.api.PriceQuote;
import org.junit.Test;

public class VaultPrunerTest {

    private static TrackedTrade trade(long trackedAtMs, long lastSeenHealthyMs) {
        return TrackedTrade.builder()
            .id(2)
            .name("Cannonball")
            .snapBuy(100)
            .snapSell(110)
            .snapTax(2)
            .trackedAtMs(trackedAtMs)
            .lastSeenHealthyMs(lastSeenHealthyMs)
            .build();
    }

    private static PriceQuote quote(Integer high, Long highTime, Integer low, Long lowTime) {
        PriceQuote q = new PriceQuote();
        q.setHigh(high == null ? null : high.longValue());
        q.setHighTime(highTime);
        q.setLow(low == null ? null : low.longValue());
        q.setLowTime(lowTime);
        return q;
    }

    @Test
    public void hardTtl_justUnderCutoff_notEvicted() {
        long now = 1_000_000_000L;
        TrackedTrade t = trade(now - Constants.VAULT_TTL_MS + 60_000, now);
        assertFalse(VaultPruner.pastHardTtl(t, now));
    }

    @Test
    public void hardTtl_justOverCutoff_evicted() {
        long now = 1_000_000_000L;
        TrackedTrade t = trade(now - Constants.VAULT_TTL_MS - 60_000, now);
        assertTrue(VaultPruner.pastHardTtl(t, now));
    }

    @Test
    public void healthyQuote_oneSideFreshWithin30Min() {
        long nowMs = 1_000_000_000L;
        long nowSec = nowMs / 1000;
        // high stale (40 min), low fresh (5 min) -> healthy overall (min of the two ages).
        PriceQuote q = quote(110, nowSec - 2400, 100, nowSec - 300);
        assertTrue(VaultPruner.isQuoteHealthy(q, nowMs));
    }

    @Test
    public void deadQuote_bothSidesStale() {
        // QUOTE_STALE_SEC = 2700 (45min) as of 2026-07-17 - was 1800 (30min).
        long nowMs = 1_000_000_000L;
        long nowSec = nowMs / 1000;
        PriceQuote q = quote(110, nowSec - 2800, 100, nowSec - 2800);
        assertFalse(VaultPruner.isQuoteHealthy(q, nowMs));
    }

    @Test
    public void missingQuote_isDead() {
        assertFalse(VaultPruner.isQuoteHealthy(null, System.currentTimeMillis()));
    }

    @Test
    public void nullSidedQuote_isDead() {
        PriceQuote q = quote(null, null, 100, System.currentTimeMillis() / 1000);
        assertFalse(VaultPruner.isQuoteHealthy(q, System.currentTimeMillis()));
    }

    @Test
    public void deadGrace_underOneHour_notEvicted() {
        long now = 1_000_000_000L;
        TrackedTrade t = trade(now - 10_000, now - Constants.VAULT_DEAD_GRACE_MS + 60_000);
        assertFalse(VaultPruner.pastDeadGrace(t, now));
    }

    @Test
    public void deadGrace_overOneHour_evicted() {
        long now = 1_000_000_000L;
        TrackedTrade t = trade(now - 10_000, now - Constants.VAULT_DEAD_GRACE_MS - 60_000);
        assertTrue(VaultPruner.pastDeadGrace(t, now));
    }

    @Test
    public void gsonRoundTrip_preservesAllFields() {
        Gson gson = new Gson();
        TrackedTrade original = trade(123L, 456L);
        String json = gson.toJson(original);
        TrackedTrade restored = gson.fromJson(json, TrackedTrade.class);
        assertTrue(original.equals(restored));
    }
}
