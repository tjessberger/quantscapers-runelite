package com.quantscapers.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConstantsTest {
    @Test
    public void geTaxAppliesFloorAndCap() {
        assertEquals(0, Constants.geTax(49));
        assertEquals(1, Constants.geTax(50));
        assertEquals(5_000_000, Constants.geTax(300_000_000));
    }

    @Test
    public void wikiUserAgentIdentifiesProjectAndContactRoute() {
        assertEquals(
            "QuantScapers/1.0 (RuneLite plugin; contact: https://github.com/tjessberger/quantscapers-runelite/issues)",
            Constants.WIKI_USER_AGENT);
    }
}
