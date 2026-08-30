package com.quantscapers;

import java.awt.Color;

/** Single source of color truth, shared by the engine (verdict/signal/audit colors) and the UI. */
public final class QSColors {
    private QSColors() {}

    public static final Color BG          = new Color(0x0f, 0x17, 0x2a); // card bg (slate-900)
    public static final Color BG_DEEP     = new Color(0x02, 0x06, 0x17); // inner wells (slate-950)
    public static final Color BG_HOVER    = new Color(0x1e, 0x29, 0x3b);

    public static final Color AMBER_500   = new Color(0xf5, 0x9e, 0x0b);
    public static final Color AMBER_400   = new Color(0xfb, 0xbf, 0x24);
    public static final Color AMBER_600   = new Color(0xd9, 0x77, 0x06);

    public static final Color EMERALD_400 = new Color(0x34, 0xd3, 0x99);
    public static final Color EMERALD_500 = new Color(0x10, 0xb9, 0x81);
    public static final Color EMERALD_600 = new Color(0x05, 0x96, 0x69);

    public static final Color RED_400     = new Color(0xf8, 0x71, 0x71);
    public static final Color RED_500     = new Color(0xef, 0x44, 0x44);

    public static final Color SLATE_200   = new Color(0xe2, 0xe8, 0xf0);
    public static final Color SLATE_400   = new Color(0x94, 0xa3, 0xb8);
    public static final Color SLATE_500   = new Color(0x64, 0x74, 0x8b);

    public static final Color VIOLET_400  = new Color(0xc0, 0x84, 0xfc);
    public static final Color VIOLET_500  = new Color(0xa8, 0x55, 0xf7);

    public static final Color BORDER         = new Color(255, 255, 255, 18);
    public static final Color BORDER_EMERALD = EMERALD_500.darker();
    public static final Color BORDER_AMBER   = AMBER_500.darker();
    public static final Color BORDER_RED     = RED_500.darker();
    public static final Color BORDER_VIOLET  = VIOLET_500.darker();

    public static Color chipBackground(Color verdictColor) {
        return new Color(verdictColor.getRed(), verdictColor.getGreen(), verdictColor.getBlue(), 38);
    }

    /** Matches the verdict labels AuditEngine produces (see engine.AuditEngine#audit). */
    public static Color forAuditVerdict(String verdict) {
        if (verdict == null) return SLATE_400;
        switch (verdict) {
            case "Strong move": return EMERALD_400;
            case "Slipping": return RED_400;
            case "Peak or stagnant": return AMBER_400;
            case "Highly active": return EMERALD_500;
            case "Audit failed": return RED_400;
            case "Stable":
            default: return SLATE_400;
        }
    }

    /** Matches the stability grades AuditEngine produces (A-F, or null when unrated). */
    public static Color forGrade(Character grade) {
        if (grade == null) return null;
        switch (grade) {
            case 'A': return EMERALD_400;
            case 'B': return EMERALD_600;
            case 'C': return AMBER_400;
            case 'D': return AMBER_600;
            case 'F':
            default: return RED_500;
        }
    }
}
