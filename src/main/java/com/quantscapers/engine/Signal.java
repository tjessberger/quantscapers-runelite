package com.quantscapers.engine;

import java.awt.Color;
import lombok.Value;

/** The single highest-priority tactical signal for an item (see VerdictEngine#signal). */
@Value
public class Signal {
    String label;
    Color color;
}
