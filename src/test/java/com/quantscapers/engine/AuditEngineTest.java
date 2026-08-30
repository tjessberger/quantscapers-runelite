package com.quantscapers.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.quantscapers.api.TimeseriesPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AuditEngineTest {

    private static TimeseriesPoint point(double avgHigh, Double avgLow) {
        TimeseriesPoint p = new TimeseriesPoint();
        p.setTimestamp(0L);
        p.setAvgHighPrice(avgHigh);
        p.setAvgLowPrice(avgLow);
        return p;
    }

    @Test
    public void allPointsHit_likelihood100_gradeA_constantMargin() {
        // 168 identical points: avgHigh 1000 / avgLow 900 -> margin 80 constant -> cv 0 -> grade A.
        List<TimeseriesPoint> points = new ArrayList<>();
        for (int i = 0; i < 168; i++) points.add(point(1000, 900.0));

        AuditResult r = AuditEngine.audit(points, 1000, 5000L);

        assertEquals(24, r.getHits1d());
        assertEquals(168, r.getHits7d());
        assertEquals(100, r.getLikelihood());
        assertEquals(Character.valueOf('A'), r.getStabilityGrade());
        assertEquals(24, r.getSparkData().length);
    }

    @Test
    public void noPointsHit_likelihoodZero() {
        List<TimeseriesPoint> points = new ArrayList<>();
        for (int i = 0; i < 168; i++) points.add(point(500, 400.0));

        AuditResult r = AuditEngine.audit(points, 1000, 5000L);

        assertEquals(0, r.getHits1d());
        assertEquals(0, r.getHits7d());
        assertEquals(0, r.getLikelihood());
        assertEquals("Stable", r.getVerdict());
    }

    @Test
    public void partialRecentHits_likelihoodMatchesWeightedFormula() {
        // Older 144 points fail; of the most recent 24, the last 12 pass -> hits1d=12, hits7d=12.
        // likelihood = round(12/24*60 + 12/168*40) = round(30 + 2.857) = 33.
        List<TimeseriesPoint> points = new ArrayList<>();
        for (int i = 0; i < 144; i++) points.add(point(500, 400.0));
        for (int i = 0; i < 12; i++) points.add(point(500, 400.0));
        for (int i = 0; i < 12; i++) points.add(point(1000, 900.0));

        AuditResult r = AuditEngine.audit(points, 1000, 5000L);

        assertEquals(12, r.getHits1d());
        assertEquals(12, r.getHits7d());
        assertEquals(33, r.getLikelihood());
    }

    @Test
    public void missingLowPrices_gradeIsNull() {
        List<TimeseriesPoint> points = new ArrayList<>();
        for (int i = 0; i < 168; i++) points.add(point(500, null));

        AuditResult r = AuditEngine.audit(points, 1000, 5000L);

        assertNull(r.getStabilityGrade());
    }

    @Test
    public void fewerThan24MarginSamples_gradeIsNull() {
        List<TimeseriesPoint> points = new ArrayList<>();
        for (int i = 0; i < 10; i++) points.add(point(1000, 900.0));

        AuditResult r = AuditEngine.audit(points, 1000, 5000L);

        assertNull(r.getStabilityGrade());
    }

    @Test
    public void shortHistory_doesNotThrow() {
        List<TimeseriesPoint> points = new ArrayList<>();
        points.add(point(1000, 900.0));

        AuditResult r = AuditEngine.audit(points, 1000, 5000L);

        assertEquals(1, r.getHits1d());
        assertTrue(r.getHits7d() >= r.getHits1d());
    }

    @Test
    public void failed_producesZeroLikelihoodMarker() {
        AuditResult r = AuditEngine.failed(5000L);
        assertTrue(r.isFailed());
        assertEquals(0, r.getLikelihood());
        assertEquals("Audit failed", r.getVerdict());
    }
}
