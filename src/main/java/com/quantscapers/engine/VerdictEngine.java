package com.quantscapers.engine;

import com.quantscapers.QSColors;

/**
 * Guards, tactical signal, and the verdict gate ladder. This is a direct
 * transcription of engine.html's isStaleQuote/isPossibleTrap/isBestBet/
 * getTacticalSignal/getVerdict. Gate order matters — earlier gates win.
 * Do not reorder without a matching change in engine.html.
 */
public final class VerdictEngine {
    private VerdictEngine() {}

    /** Seconds since the older of the two quotes; null when either timestamp is missing (0). */
    public static Long quoteAgeSec(AnalyzedItem it) {
        if (it.getHighTime() == 0 || it.getLowTime() == 0) {
            return null;
        }
        long nowSec = it.getAnalyzedAtMs() / 1000;
        return Math.max(nowSec - it.getHighTime(), nowSec - it.getLowTime());
    }

    public static boolean isStaleQuote(AnalyzedItem it) {
        Long age = quoteAgeSec(it);
        return age != null && age > Constants.QUOTE_STALE_SEC;
    }

    /** Manipulation heuristic: spread far wider than the item's own 24h norm, on thin volume. */
    public static boolean isPossibleTrap(AnalyzedItem it) {
        double avgSpread = it.getAvg24hHigh() - it.getAvg24hLow();
        if (avgSpread <= 0 || it.getVol24h() >= 1000) {
            return false;
        }
        return (it.getHigh() - it.getLow()) > avgSpread * 3;
    }

    public static boolean isBestBet(AnalyzedItem it) {
        // Relaxed 2026-07-17 to match quantscapers.com/worker's isBestBet - the old
        // 40min/1M/3% floor was silently hiding low-volume megarares (Twisted Bow etc.)
        // from Top Picks. Keep this numerically identical to the web/worker copies.
        return it.getEft() <= 90
            && it.getRealisticProfit() >= 500_000
            && it.getRoi() >= 2
            && !isStaleQuote(it)
            && !isPossibleTrap(it);
    }

    /** Returns the single highest-priority tactical signal for the card. */
    public static Signal signal(AnalyzedItem it) {
        if (isPossibleTrap(it)) {
            return new Signal("Possible Trap - Verify", QSColors.RED_400);
        }
        if (isStaleQuote(it)) {
            return new Signal("Stale Quote - Verify", QSColors.AMBER_400);
        }

        if (it.getLow() < it.getAvg24hLow() * 0.94) {
            return new Signal("Oversold - Snapback Likely", QSColors.EMERALD_400);
        } else if (it.getLow() < it.getAvg24hLow() * 0.985) {
            return new Signal("Panic Dip - Good Entry", QSColors.EMERALD_400);
        } else if (it.getLow() > it.getAvg24hLow() * 1.015) {
            return new Signal("Price Bubble - Avoid", QSColors.RED_400);
        }

        if (it.getVol5m() != null && it.getVol5m() > 0 && it.getAvg5mHigh() > 0 && it.getAvg24hHigh() > 0) {
            double shift = ((it.getAvg5mHigh() / it.getAvg24hHigh()) - 1) * 100;
            if (shift > 2) {
                return new Signal(String.format("Heating Up +%.1f%%", shift), QSColors.EMERALD_400);
            } else if (shift < -2) {
                return new Signal(String.format("Cooling Off %.1f%%", shift), QSColors.RED_400);
            }
        }

        return new Signal("Stable Market", QSColors.SLATE_500);
    }

    /** hist may be null when the item has never been audited. */
    public static Verdict verdict(AnalyzedItem it, AuditResult hist) {
        if (isPossibleTrap(it)) {
            return avoid("Spread far above this item's normal — likely price manipulation.");
        }
        if (isStaleQuote(it)) {
            return avoid("Quotes over 45m old — this margin may no longer exist.");
        }
        if (it.getRoi() <= 0 || it.getRealisticProfit() <= 0) {
            return avoid("No profit left after tax at current prices.");
        }

        Long age = quoteAgeSec(it);
        boolean cooling = it.getVol5m() != null && it.getVol5m() > 0
            && it.getAvg5mHigh() > 0 && it.getAvg24hHigh() > 0
            && ((it.getAvg5mHigh() / it.getAvg24hHigh()) - 1) * 100 < -2;

        String flag = null;
        if (it.getEft() > 240) {
            flag = "Slow fill — roughly " + Math.round(it.getEft() / 60) + "h per side";
        } else if (hist != null && hist.getStabilityGrade() != null
            && (hist.getStabilityGrade() == 'D' || hist.getStabilityGrade() == 'F')) {
            flag = "Margin unstable this week (grade " + hist.getStabilityGrade() + ")";
        } else if (hist != null && hist.getLikelihood() > 0 && hist.getLikelihood() < 30) {
            flag = "Sell price rarely reached (" + hist.getLikelihood() + "% hit rate)";
        } else if (cooling) {
            flag = "Price cooling off vs 24h average";
        } else if (it.getVol5m() != null && it.getVol5m() == 0) {
            flag = "Nothing traded in the last 5 minutes";
        } else if (age != null && age > 600) {
            flag = "Quotes aging — verify before committing";
        }
        if (flag != null) {
            return new Verdict(Verdict.Rating.RISKY, flag + ".", QSColors.AMBER_400, QSColors.BORDER_AMBER);
        }

        boolean fresh = age != null && age <= 300 && it.getEft() <= 40 && it.getRoi() >= 2
            && (hist == null || hist.getLikelihood() >= 50);
        if (fresh) {
            String auditNote = hist != null ? " · " + hist.getLikelihood() + "% sell-price hit rate" : "";
            String reason = String.format("Fresh quotes · ~%dmin fill per side · %.1f%% after tax%s.",
                Math.round(it.getEft()), it.getRoi(), auditNote);
            return new Verdict(Verdict.Rating.BUY, reason, QSColors.EMERALD_400, QSColors.BORDER_EMERALD);
        }

        String whyNot = (age == null || age > 300) ? "quotes not fully fresh"
            : it.getEft() > 40 ? "slower fill" : "thin ROI";
        String reason = String.format("%.1f%% after tax · ~%dmin fill per side · %s.",
            it.getRoi(), Math.round(it.getEft()), whyNot);
        return new Verdict(Verdict.Rating.DECENT, reason, QSColors.SLATE_200, QSColors.BORDER);
    }

    private static Verdict avoid(String reason) {
        return new Verdict(Verdict.Rating.AVOID, reason, QSColors.RED_400, QSColors.BORDER_RED);
    }
}
