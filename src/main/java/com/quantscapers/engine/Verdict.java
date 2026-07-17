package com.quantscapers.engine;

import java.awt.Color;
import lombok.Value;

@Value
public class Verdict {
    Rating rating;
    String reason;
    Color color;
    Color border;

    public enum Rating { BUY, DECENT, RISKY, AVOID }
}
