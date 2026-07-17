package com.quantscapers.ui.util;

import java.util.Locale;

/** Direct port of formatGP() in engine.html. */
public final class GpFormat {
    private GpFormat() {}

    public static String format(long n) {
        if (n < 0) {
            return "-" + format(-n);
        }
        if (n >= 1_000_000_000L) {
            return String.format(Locale.US, "%.2fB", n / 1e9);
        }
        if (n >= 1_000_000L) {
            return String.format(Locale.US, "%.2fM", n / 1e6);
        }
        if (n >= 1_000L) {
            return String.format(Locale.US, "%.1fk", n / 1e3);
        }
        return Long.toString(n);
    }

    public static String withCommas(long n) {
        return String.format(Locale.US, "%,d", n);
    }
}
