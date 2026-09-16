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
        return Math.max(0, Math.max(nowSec - it.getHighTime(), nowSec - it.getLowTime()));
    }

    public static boolean isStaleQuote(AnalyzedItem it) {
        Long age = quoteAgeSec(it);
        return age != null && age > Constants.QUOTE_STALE_SEC;
    }

    /** Missing timestamps are not stale, but they are not fresh enough for recommendations either. */
    public static boolean hasFreshQuotes(AnalyzedItem it) {
        Long age = quoteAgeSec(it);
        return age != null && age <= Constants.QUOTE_STALE_SEC;
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
            && hasFreshQuotes(it)
            && !isPossibleTrap(it);
    }

    /** Returns the single highest-priority tactical signal for the card. */
    public static Signal signal(AnalyzedItem it) {
        if (isPossibleTrap(it)) {
            return new Signal("Possible trap, verify", QSColors.RED_400);
        }
        if (isStaleQuote(it)) {
            return new Signal("Stale quote, verify", QSColors.AMBER_400);
        }

        if (it.getLow() < it.getAvg24hLow() * 0.94) {
            return new Signal("Oversold, snapback possible", QSColors.EMERALD_400);
        } else if (it.getLow() < it.getAvg24hLow() * 0.985) {
            return new Signal("Panic dip, possible entry", QSColors.EMERALD_400);
        } else if (it.getLow() > it.getAvg24hLow() * 1.015) {
            return new Signal("Price bubble, avoid", QSColors.RED_400);
        }

        Double shift = momentumShiftPct(it);
        if (shift != null) {
            if (shift > 2) {
                return new Signal(String.format("Heating up +%.1f%%", shift), QSColors.EMERALD_400);
            } else if (shift < -2) {
                return new Signal(String.format("Cooling off %.1f%%", shift), QSColors.RED_400);
            }
        }

        return new Signal("Stable market", QSColors.SLATE_500);
    }

    /** hist may be null when the item has never been audited. */
    public static Verdict verdict(AnalyzedItem it, AuditResult hist) {
        Verdict blocked = blockingVerdict(it);
        if (blocked != null) return blocked;

        Long age = quoteAgeSec(it);
        String flag = riskFlag(it, hist, age);
        if (flag != null) {
            return new Verdict(Verdict.Rating.RISKY, flag + ".", QSColors.AMBER_400, QSColors.BORDER_AMBER);
        }

        if (isStrongBuy(it, hist, age)) {
            String auditNote = hist != null ? ", " + hist.getLikelihood() + "% sell-price hit rate" : "";
            String reason = String.format("Fresh quotes, ~%d min fill per side, %.1f%% after tax%s.",
                Math.round(it.getEft()), it.getRoi(), auditNote);
            return new Verdict(Verdict.Rating.BUY, reason, QSColors.EMERALD_400, QSColors.BORDER_EMERALD);
        }

        String reason = String.format("%.1f%% after tax, ~%d min fill per side, %s.",
            it.getRoi(), Math.round(it.getEft()), whyNotBuy(it, age));
        return new Verdict(Verdict.Rating.DECENT, reason, QSColors.SLATE_200, QSColors.BORDER);
    }

    private static Verdict avoid(String reason) {
        return new Verdict(Verdict.Rating.AVOID, reason, QSColors.RED_400, QSColors.BORDER_RED);
    }

    private static Verdict blockingVerdict(AnalyzedItem item) {
        if (isPossibleTrap(item)) {
            return avoid("Spread is far above this item's normal range. Possible price manipulation.");
        }
        if (isStaleQuote(item)) {
            return avoid("Quotes are over " + Constants.QUOTE_STALE_SEC / 60
                + " min old. This margin may no longer exist.");
        }
        if (item.getRoi() <= 0 || item.getRealisticProfit() <= 0) {
            return avoid("No profit left after tax at current prices.");
        }
        return null;
    }

    private static boolean isStrongBuy(AnalyzedItem item, AuditResult audit, Long age) {
        return age != null && age <= 300 && item.getEft() <= 40 && item.getRoi() >= 2
            && (audit == null || audit.getLikelihood() >= 50);
    }

    private static String whyNotBuy(AnalyzedItem item, Long age) {
        if (age == null || age > 300) return "quotes are not fully fresh";
        return item.getEft() > 40 ? "fill is slower" : "ROI is thin";
    }

    private static String riskFlag(AnalyzedItem item, AuditResult audit, Long age) {
        if (item.getEft() > 240) {
            return "Slow fill. About " + Math.round(item.getEft() / 60) + "h per side";
        }
        if (hasUnstableMargin(audit)) {
            return "Margin unstable this week (grade " + audit.getStabilityGrade() + ")";
        }
        if (hasLowHitRate(audit)) {
            return "Sell price rarely reached (" + audit.getLikelihood() + "% hit rate)";
        }
        Double shift = momentumShiftPct(item);
        if (shift != null && shift < -2) return "Price is cooling off versus its 24h average";
        if (item.getVol5m() != null && item.getVol5m() == 0) return "No trades in the last 5 minutes";
        if (age != null && age > 600) return "Quotes are aging. Verify before committing";
        return null;
    }

    private static boolean hasUnstableMargin(AuditResult audit) {
        if (audit == null || audit.getStabilityGrade() == null) return false;
        return audit.getStabilityGrade() == 'D' || audit.getStabilityGrade() == 'F';
    }

    private static boolean hasLowHitRate(AuditResult audit) {
        return audit != null && audit.getLikelihood() > 0 && audit.getLikelihood() < 30;
    }

    private static Double momentumShiftPct(AnalyzedItem item) {
        if (item.getVol5m() == null || item.getVol5m() <= 0
            || item.getAvg5mHigh() <= 0 || item.getAvg24hHigh() <= 0) {
            return null;
        }
        return ((item.getAvg5mHigh() / item.getAvg24hHigh()) - 1) * 100;
    }
}
