package com.quantscapers.engine;

public final class Constants {
    private Constants() {}

    public static final double TAX_RATE = 0.02;
    public static final int TAX_CAP_GP = 5_000_000;
    public static final int TAX_FREE_FLOOR_GP = 50;
    public static final int MINUTES_IN_DAY = 1440;
    public static final int HEARTBEAT_SECONDS = 60; // NEVER lower
    public static final int NATURE_RUNE_ID = 561;
    public static final int NATURE_RUNE_FALLBACK_GP = 195;
    public static final long QUOTE_STALE_SEC = 2700; // 45 min, matches web/worker as of 2026-07-17
    public static final long AUDIT_TTL_MS = 3_600_000; // 1 h
    public static final long FAILED_AUDIT_RETRY_MS = 300_000; // 5 min
    public static final long AUTO_AUDIT_WINDOW_MS = 1_800_000; // 30 min
    public static final int AUTO_AUDIT_MAX_CALLS = 3; // NEVER raise
    // Manual "Audit" button clicks were previously uncapped (serialized by
    // auditInFlight, but not rate-limited) - a user clicking Audit across many
    // cards in a row could fire unlimited timeseries calls. Separate budget from
    // the auto-audit one so Top Picks auditing is never starved by manual use.
    public static final long MANUAL_AUDIT_WINDOW_MS = 600_000; // 10 min
    public static final int MANUAL_AUDIT_MAX_CALLS = 10;
    public static final int MAX_CARDS_RENDERED = 50; // Swing perf cap
    public static final long VAULT_TTL_MS = 172_800_000L; // 48 h hard cap since trackedAt
    public static final long VAULT_DEAD_GRACE_MS = 3_600_000L; // 1 h of continuous dead quotes
    public static final int VAULT_MAX_ENTRIES = 25;

    // AVOID confirmed by both the live verdict AND a fresh (<=AUDIT_TTL_MS) audit's hit
    // rate gets hidden from the leads list for this long, then re-evaluated fresh.
    public static final long SUPPRESSION_DURATION_MS = 7_200_000L; // 2 h
    public static final int SUPPRESSION_HIT_RATE_THRESHOLD = 25; // likelihood % below this confirms

    public static final String WIKI_USER_AGENT = "QuantScapers/1.0 (RuneLite plugin; contact: "
        + "https://github.com/tjessberger/quantscapers-runelite/issues)";
    public static final String API_BASE = "https://prices.runescape.wiki/api/v1/osrs";

    /** GE tax for a sell price, including the tax-free floor and cap. */
    public static int geTax(int sellPrice) {
        if (sellPrice < TAX_FREE_FLOOR_GP) {
            return 0;
        }
        return (int) Math.min(Math.floor(sellPrice * TAX_RATE), TAX_CAP_GP);
    }

}
