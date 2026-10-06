package com.quantscapers.api;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.quantscapers.engine.AnalyzedItem;
import com.quantscapers.engine.MarketAnalyzer;
import com.quantscapers.engine.Ticket;
import com.quantscapers.engine.TicketBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Regression: the wiki /latest feed can contain GE prices above Integer.MAX_VALUE
 * (e.g. {"high":2447000000}); these must deserialize and flow through the engine
 * without NumberFormatException, clamping or dropping the item.
 */
public class LatestResponseGsonTest {

    private final Gson gson = new Gson();

    @Test
    public void priceQuote_deserializesHighAboveIntMax() {
        PriceQuote q = gson.fromJson("{\"high\":2447000000}", PriceQuote.class);
        assertEquals(Long.valueOf(2_447_000_000L), q.getHigh());
    }

    @Test
    public void latestResponse_deserializesWholeFeedWithHugePrices() {
        String json = "{\"data\":{\"20997\":{\"high\":2447000000,\"highTime\":1800000000,"
            + "\"low\":2400000000,\"lowTime\":1800000000},"
            + "\"561\":{\"high\":200,\"highTime\":1800000000,\"low\":195,\"lowTime\":1800000000}}}";
        LatestResponse resp = gson.fromJson(json, LatestResponse.class);
        PriceQuote q = resp.getData().get(20997);
        assertEquals(Long.valueOf(2_447_000_000L), q.getHigh());
        assertEquals(Long.valueOf(2_400_000_000L), q.getLow());
        assertEquals(Long.valueOf(195L), resp.getData().get(561).getLow());
    }

    @Test
    public void hugePrice_flowsThroughAnalyzerAndTicketUnclamped() {
        long nowSec = 1_800_000_000L;
        PriceQuote q = gson.fromJson("{\"high\":2447000000,\"highTime\":" + nowSec
            + ",\"low\":2400000000,\"lowTime\":" + nowSec + "}", PriceQuote.class);
        MappingItem m = new MappingItem();
        m.setId(1);
        m.setName("Megarare");
        m.setLimit(8);
        m.setHighalch(0);
        VolumeStats s = new VolumeStats();
        s.setAvgHighPrice(2_440_000_000.0);
        s.setAvgLowPrice(2_400_000_000.0);
        s.setHighPriceVolume(10);
        s.setLowPriceVolume(10);

        List<AnalyzedItem> out = MarketAnalyzer.analyze(
            Collections.singletonList(m), Map.of(1, q), Map.of(1, s), null, 195, nowSec * 1000);

        assertEquals(1, out.size());
        AnalyzedItem it = out.get(0);
        assertEquals(2_447_000_000L, it.getHigh());
        assertEquals(2_400_000_000L, it.getLow());
        assertEquals(5_000_000L, it.getTax()); // 2% is 48.9M, capped at 5M
        assertEquals(42_000_000L * 8, it.getRealisticProfit()); // (high - low - tax) * min(limit, vol)

        Ticket t = TicketBuilder.build(it);
        assertEquals(2_400_000_001L, t.getBuyAt());
        assertEquals(2_446_999_999L, t.getSellAt());
    }
}
