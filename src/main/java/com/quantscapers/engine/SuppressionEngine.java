package com.quantscapers.engine;

/**
 * Decides whether an AVOID verdict is "confirmed" by historical evidence and should be
 * hidden from the leads list for a while, rather than re-shown every tick as a warning
 * the user has to keep re-reading. Two independent signals must agree:
 *   1. the live verdict is AVOID (spread/volume/staleness gates), and
 *   2. a FRESH audit (within AUDIT_TTL_MS - a stale audit doesn't count) shows a sell-price
 *      hit rate under SUPPRESSION_HIT_RATE_THRESHOLD.
 * An AVOID with no audit, or a low hit rate on a non-AVOID item, is not enough on its own.
 */
public final class SuppressionEngine {
    private SuppressionEngine() {}

    public static boolean isConfirmedAvoid(AnalyzedItem item, AuditResult hist, long nowMs) {
        if (hist == null || hist.isFailed()) {
            return false;
        }
        if (nowMs - hist.getTs() >= Constants.AUDIT_TTL_MS) {
            return false; // stale audit doesn't count as confirmation
        }
        if (hist.getLikelihood() >= Constants.SUPPRESSION_HIT_RATE_THRESHOLD) {
            return false;
        }
        return VerdictEngine.verdict(item, hist).getRating() == Verdict.Rating.AVOID;
    }
}
