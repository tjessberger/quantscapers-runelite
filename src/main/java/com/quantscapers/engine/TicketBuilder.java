package com.quantscapers.engine;

/**
 * Exact GE order instructions for the flips strategy: undercut/overcut by
 * 1gp, quantity capped by buy limit and real daily volume. Direct
 * transcription of buildTicket() in engine.html (flips branch only — the
 * alch variant is deferred to v1.1, see RUNELITE_PLUGIN_SPEC.md Appendix A).
 */
public final class TicketBuilder {
    private TicketBuilder() {}

    public static Ticket build(AnalyzedItem item) {
        long qty = Math.min(item.getLimit(), item.getVol24h());
        if (qty <= 0) {
            return null;
        }
        int buyAt = item.getLow() + 1;
        int sellAt = Math.max(item.getHigh() - 1, 0);
        int tax = sellAt < Constants.TAX_FREE_FLOOR_GP
            ? 0
            : (int) Math.min(Math.floor(sellAt * Constants.TAX_RATE), Constants.TAX_CAP_GP);
        int perUnit = sellAt - buyAt - tax;
        if (perUnit <= 0) {
            return null;
        }
        long profit = (long) perUnit * qty;
        int waitMin = (int) Math.max(5, Math.ceil(item.getEft()));
        return new Ticket(qty, buyAt, sellAt, profit, waitMin);
    }
}
