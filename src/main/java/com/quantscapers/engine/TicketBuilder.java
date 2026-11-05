package com.quantscapers.engine;

/**
 * Exact GE order instructions for the trade strategy: undercut/overcut by
 * 1gp, quantity capped by buy limit and real daily volume. Direct
 * transcription of buildTicket() in engine.html (trade branch only — the
 * alch variant is deferred to v1.1, see RUNELITE_PLUGIN_SPEC.md Appendix A).
 */
public final class TicketBuilder {
    private TicketBuilder() {}

    public static Ticket build(AnalyzedItem item) {
        long qty = Math.min(item.getLimit(), item.getVol24h());
        if (qty <= 0) {
            return null;
        }
        long buyAt = item.getLow() + 1;
        long sellAt = Math.max(item.getHigh() - 1, 0);
        long tax = Constants.geTax(sellAt);
        long perUnit = sellAt - buyAt - tax;
        if (perUnit <= 0) {
            return null;
        }
        long profit = perUnit * qty;
        int waitMin = (int) Math.max(5, Math.ceil(item.getEft()));
        return new Ticket(qty, buyAt, sellAt, profit, waitMin);
    }
}
