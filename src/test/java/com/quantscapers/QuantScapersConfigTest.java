package com.quantscapers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class QuantScapersConfigTest {
    @Test
    public void maxBudgetDoesNotExcludeMultiBillionGpBuyLimits() {
        assertEquals(Long.MAX_VALUE, QuantScapersConfig.BudgetCap.MAX.value());
    }
}
