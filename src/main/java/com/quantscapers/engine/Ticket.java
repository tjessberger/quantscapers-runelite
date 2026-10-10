package com.quantscapers.engine;

import lombok.Value;

@Value
public class Ticket {
    long qty;
    long buyAt;
    long sellAt;
    long profit;
    int waitMin;
}
