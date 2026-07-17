package com.quantscapers.ui.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GpFormatTest {
    @Test
    public void matchesSpecWorkedExamples() {
        assertEquals("1.3k", GpFormat.format(1_250));
        assertEquals("520.0k", GpFormat.format(520_000));
        assertEquals("1.63M", GpFormat.format(1_625_000));
        assertEquals("2.15B", GpFormat.format(2_147_483_647L));
        assertEquals("999", GpFormat.format(999));
        assertEquals("-1.63M", GpFormat.format(-1_625_000));
    }
}
