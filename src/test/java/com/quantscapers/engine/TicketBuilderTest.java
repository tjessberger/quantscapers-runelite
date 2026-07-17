package com.quantscapers.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TicketBuilderTest {

    private static AnalyzedItem.AnalyzedItemBuilder base() {
        return AnalyzedItem.builder()
            .id(1).name("Test").limit(8000).highalch(0)
            .highTime(1L).lowTime(1L)
            .vol24h(1_200_000).eft(9.6)
            .avg24hHigh(1080).avg24hLow(1000)
            .analyzedAtMs(0L);
    }

    @Test
    public void tightSpread_producesNullTicket() {
        // high=1003, low=1001 -> sellAt=1002, buyAt=1002, tax=floor(1002*0.02)=20, perUnit=-20.
        AnalyzedItem it = base().high(1003).low(1001).build();
        assertNull(TicketBuilder.build(it));
    }

    @Test
    public void zeroVolume_producesNullTicket() {
        AnalyzedItem it = base().high(1100).low(1000).vol24h(0).build();
        assertNull(TicketBuilder.build(it));
    }

    @Test
    public void healthySpread_producesExpectedTicket() {
        AnalyzedItem it = base().high(1087).low(1001).build();
        Ticket t = TicketBuilder.build(it);
        assertEquals(8000L, t.getQty());
        assertEquals(1002, t.getBuyAt());
        assertEquals(1086, t.getSellAt());
        assertEquals(504_000L, t.getProfit());
        assertEquals(10, t.getWaitMin());
    }

    @Test
    public void waitMin_neverBelow5() {
        AnalyzedItem it = base().high(1087).low(1001).eft(0.5).build();
        Ticket t = TicketBuilder.build(it);
        assertEquals(5, t.getWaitMin());
    }
}
