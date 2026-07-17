package com.quantscapers.ui.util;

import com.quantscapers.QSColors;
import javax.swing.JLabel;
import net.runelite.client.ui.FontManager;

/**
 * A "3m ago" label that recolors as its timestamp ages: fresh (&lt;5min)
 * emerald, aging (&lt;30min) amber, stale (&ge;30min) red. Direct port of
 * formatAge()/ageColor() in engine.html. A unix-seconds value of 0 means
 * "unknown" and renders blank.
 */
public class AgeLabel extends JLabel {
    private long unixSec;
    private final String suffix;

    public AgeLabel(String suffix) {
        this.suffix = suffix;
        setFont(FontManager.getRunescapeSmallFont());
    }

    public void setUnixSec(long unixSec) {
        this.unixSec = unixSec;
    }

    public void refresh(long nowMs) {
        if (unixSec <= 0) {
            setText("");
            return;
        }
        long s = Math.max(0, nowMs / 1000 - unixSec);
        setText(formatAge(s) + suffix);
        setForeground(colorFor(s));
    }

    private static String formatAge(long s) {
        if (s < 60) return s + "s";
        // "min" not "m" - in OSRS, "m" reads as millions (e.g. "5m" gold), not minutes.
        if (s < 3600) return (s / 60) + "min";
        if (s < 86400) return (s / 3600) + "h";
        return (s / 86400) + "d";
    }

    private static java.awt.Color colorFor(long s) {
        if (s < 300) return QSColors.EMERALD_500;
        if (s < 1800) return QSColors.AMBER_500;
        return QSColors.RED_500;
    }
}
