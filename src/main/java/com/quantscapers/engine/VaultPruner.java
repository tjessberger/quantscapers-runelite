package com.quantscapers.engine;

import com.quantscapers.api.PriceQuote;

/**
 * Pure pruning rules for the vault - hard TTL since tracked, plus a dead-quote
 * grace period so a single missed/stale tick doesn't evict an otherwise-good
 * entry. Kept free of RuneLite/Swing/network so it's unit-testable directly.
 */
public final class VaultPruner {
    private VaultPruner() {}

    public static boolean pastHardTtl(TrackedTrade t, long nowMs) {
        return nowMs - t.getTrackedAtMs() > Constants.VAULT_TTL_MS;
    }

    /** True when the quote has a full pair of prices with at least one side inside the stale window. */
    public static boolean isQuoteHealthy(PriceQuote q, long nowMs) {
        if (q == null || q.getHigh() == null || q.getLow() == null
            || q.getHighTime() == null || q.getLowTime() == null) {
            return false;
        }
        long nowSec = nowMs / 1000;
        long age = Math.min(nowSec - q.getHighTime(), nowSec - q.getLowTime());
        return age <= Constants.QUOTE_STALE_SEC;
    }

    public static boolean pastDeadGrace(TrackedTrade t, long nowMs) {
        return nowMs - t.getLastSeenHealthyMs() > Constants.VAULT_DEAD_GRACE_MS;
    }
}
