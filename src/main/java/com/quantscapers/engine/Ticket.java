package com.quantscapers.engine;

import lombok.Value;

@Value
public class Ticket {
    long qty;
    int buyAt;
    int sellAt;
    long profit;
    int waitMin;
}
