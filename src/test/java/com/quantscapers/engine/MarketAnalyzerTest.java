package com.quantscapers.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.quantscapers.api.MappingItem;
import com.quantscapers.api.PriceQuote;
import com.quantscapers.api.VolumeStats;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Worked Example A from RUNELITE_PLUGIN_SPEC.md §8, hand-verified:
 * limit 8000, high 1087/low 1001, 24h avgHigh 1080/avgLow 1000 at 600k/600k
 * volume each side, no 5m feed, nature rune 195.
 */
public class MarketAnalyzerTest {

    private static MappingItem mapping(int id, String name, Integer limit, Integer highalch) {
        MappingItem m = new MappingItem();
        m.setId(id);
        m.setName(name);
        m.setLimit(limit);
        m.setHighalch(highalch);
        return m;
    }

    private static PriceQuote quote(Integer high, Long highTime, Integer low, Long lowTime) {
        PriceQuote q = new PriceQuote();
        q.setHigh(high);
        q.setHighTime(highTime);
        q.setLow(low);
        q.setLowTime(lowTime);
        return q;
    }

    private static VolumeStats stats(Double avgHigh, long hiVol, Double avgLow, long loVol) {
        VolumeStats s = new VolumeStats();
        s.setAvgHighPrice(avgHigh);
        s.setHighPriceVolume(hiVol);
        s.setAvgLowPrice(avgLow);
        s.setLowPriceVolume(loVol);
        return s;
    }

    @Test
    public void workedExampleA_matchesHandComputedValues() {
        long nowMs = 1_800_000_000_000L;
        long nowSec = nowMs / 1000;

        List<MappingItem> mapping = Collections.singletonList(mapping(1, "Test Item", 8000, 0));
        Map<Integer, PriceQuote> latest = new HashMap<>();
        latest.put(1, quote(1087, nowSec - 180, 1001, nowSec - 240));
        Map<Integer, VolumeStats> stats24h = new HashMap<>();
        stats24h.put(1, stats(1080.0, 600_000, 1000.0, 600_000));

        List<AnalyzedItem> result = MarketAnalyzer.analyze(mapping, latest, stats24h, null, 195, nowMs);

        assertEquals(1, result.size());
        AnalyzedItem it = result.get(0);

        assertEquals(21, it.getTax());
        assertEquals(1_200_000L, it.getVol24h());
        assertEquals(9.6, it.getEft(), 0.0001);
        assertEquals(520_000L, it.getRealisticProfit());
        assertEquals(1_625_000.0, it.getGpHour(), 0.5);
        assertEquals(6.4935, it.getRoi(), 0.001);
        assertEquals(8_008_000L, it.getFullLimitCost());
        assertNull(it.getVol5m());

        Verdict verdict = VerdictEngine.verdict(it, null);
        assertEquals(Verdict.Rating.BUY, verdict.getRating());

        Ticket ticket = TicketBuilder.build(it);
        assertEquals(8000L, ticket.getQty());
        assertEquals(1002, ticket.getBuyAt());
        assertEquals(1086, ticket.getSellAt());
        assertEquals(504_000L, ticket.getProfit());
        assertEquals(10, ticket.getWaitMin());
    }

    @Test
    public void taxFloor_zeroBelow50gp() {
        AnalyzedItem it = analyzeSingleFlip(49, 1, 100_000, 100_000);
        assertEquals(0, it.getTax());
    }

    @Test
    public void taxFloor_oneAt50gp() {
        AnalyzedItem it = analyzeSingleFlip(50, 1, 100_000, 100_000);
        assertEquals(1, it.getTax());
    }

    @Test
    public void taxFloor_cappedAt5Million() {
        AnalyzedItem it = analyzeSingleFlip(300_000_000, 1, 100_000, 100_000);
        assertEquals(5_000_000, it.getTax());
    }

    @Test
    public void eft_9999WhenNoVolume() {
        AnalyzedItem it = analyzeSingleFlip(1087, 1001, 0, 0);
        assertEquals(9999.0, it.getEft(), 0.0001);
    }

    @Test
    public void itemMissingFromLatest_isSkipped() {
        List<MappingItem> mapping = Collections.singletonList(mapping(1, "Ghost", 100, 0));
        List<AnalyzedItem> result = MarketAnalyzer.analyze(
            mapping, Collections.emptyMap(), Collections.emptyMap(), null, 195, 0L);
        assertTrue(result.isEmpty());
    }

    @Test
    public void missingLimitAndHighalch_defaultToZero() {
        long nowSec = 1_800_000_000L;
        List<MappingItem> mapping = Collections.singletonList(mapping(1, "No Limit Data", null, null));
        Map<Integer, PriceQuote> latest = new HashMap<>();
        latest.put(1, quote(100, nowSec, 90, nowSec));
        List<AnalyzedItem> result = MarketAnalyzer.analyze(
            mapping, latest, Collections.emptyMap(), null, 195, nowSec * 1000);
        AnalyzedItem it = result.get(0);
        assertEquals(0, it.getLimit());
        assertEquals(0, it.getHighalch());
        assertEquals(0L, it.getRealisticProfit());
    }

    private static AnalyzedItem analyzeSingleFlip(int high, int low, long hiVol, long loVol) {
        long nowSec = 1_800_000_000L;
        List<MappingItem> mapping = Collections.singletonList(mapping(1, "Test", 1000, 0));
        Map<Integer, PriceQuote> latest = new HashMap<>();
        latest.put(1, quote(high, nowSec, low, nowSec));
        Map<Integer, VolumeStats> stats24h = new HashMap<>();
        stats24h.put(1, stats((double) high, hiVol, (double) low, loVol));
        return MarketAnalyzer.analyze(mapping, latest, stats24h, null, 195, nowSec * 1000).get(0);
    }
}
