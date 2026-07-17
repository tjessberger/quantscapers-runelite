package com.quantscapers;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class QuantScapersPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(QuantScapersPlugin.class);
        RuneLite.main(args);
    }
}
