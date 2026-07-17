package com.quantscapers.engine;

public final class Constants {
    private Constants() {}

    public static final double TAX_RATE = 0.02;
    public static final int TAX_CAP_GP = 5_000_000;
    public static final int TAX_FREE_FLOOR_GP = 50;
    public static final int MINUTES_IN_DAY = 1440;
    public static final int HEARTBEAT_SECONDS = 30; // NEVER lower
    public static final int NATURE_RUNE_ID = 561;
    public static final int NATURE_RUNE_FALLBACK_GP = 195;
    public static final long QUOTE_STALE_SEC = 1800; // 30 min
    public static final long AUDIT_TTL_MS = 3_600_000; // 1 h
    public static final long AUTO_AUDIT_WINDOW_MS = 1_800_000; // 30 min
    public static final int AUTO_AUDIT_MAX_CALLS = 3; // NEVER raise
    public static final int MAX_CARDS_RENDERED = 50; // Swing perf cap
    public static final long VAULT_TTL_MS = 172_800_000L; // 48 h hard cap since trackedAt
    public static final long VAULT_DEAD_GRACE_MS = 3_600_000L; // 1 h of continuous dead quotes
    public static final int VAULT_MAX_ENTRIES = 25;

    // AVOID confirmed by both the live verdict AND a fresh (<=AUDIT_TTL_MS) audit's hit
    // rate gets hidden from the leads list for this long, then re-evaluated fresh.
    public static final long SUPPRESSION_DURATION_MS = 7_200_000L; // 2 h
    public static final int SUPPRESSION_HIT_RATE_THRESHOLD = 25; // likelihood % below this confirms

    private static final String USER_AGENT_PREFIX = "QuantScapers RuneLite plugin";
    public static final String API_BASE = "https://prices.runescape.wiki/api/v1/osrs";

    /** Every install should carry its own contact so wiki maintainers can reach that user, not a shared default. */
    public static String buildUserAgent(String contactEmail) {
        String contact = (contactEmail == null || contactEmail.trim().isEmpty())
            ? "no contact email set" : contactEmail.trim();
        return USER_AGENT_PREFIX + " - " + contact;
    }

    // Deliberately loose - this exists to stop garbage like "x" or "asdf" from
    // satisfying the contact-email gate, not to validate RFC 5322 correctness.
    // A plausible-looking address that happens to be wrong is still a much
    // better fault than an install that's unreachable by construction.
    private static final java.util.regex.Pattern PLAUSIBLE_EMAIL =
        java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public static boolean isPlausibleEmail(String email) {
        return email != null && PLAUSIBLE_EMAIL.matcher(email.trim()).matches();
    }
}
