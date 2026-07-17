package com.quantscapers.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConstantsTest {
    @Test
    public void isPlausibleEmailAcceptsRealLookingAddresses() {
        assertTrue(Constants.isPlausibleEmail("player@example.com"));
        assertTrue(Constants.isPlausibleEmail("  player@example.com  ")); // trims
        assertTrue(Constants.isPlausibleEmail("first.last+tag@sub.example.co"));
    }

    @Test
    public void isPlausibleEmailRejectsGarbageThatUsedToPassTheGate() {
        assertFalse(Constants.isPlausibleEmail(null));
        assertFalse(Constants.isPlausibleEmail(""));
        assertFalse(Constants.isPlausibleEmail("   "));
        assertFalse(Constants.isPlausibleEmail("x"));
        assertFalse(Constants.isPlausibleEmail("asdf"));
        assertFalse(Constants.isPlausibleEmail("no-at-sign.com"));
        assertFalse(Constants.isPlausibleEmail("no-domain@"));
        assertFalse(Constants.isPlausibleEmail("@no-local.com"));
    }
}
