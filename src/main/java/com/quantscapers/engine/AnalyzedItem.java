package com.quantscapers.engine;

import lombok.Builder;
import lombok.Value;

/**
 * One item after a full analysis pass. Immutable — a fresh instance is
 * built every tick from raw wiki data; nothing here is mutated in place.
 */
@Value
@Builder
public class AnalyzedItem {
    int id;
    String name;
    int limit;
    int highalch;

    long high;
    long low;
    long highTime; // unix seconds, 0 if the API gave null
    long lowTime;  // unix seconds, 0 if the API gave null

    long tax;
    double roi;      // percent
    double eft;       // minutes per side
    long vol24h;

    Long vol5m;        // null when the 5m feed was unavailable this tick
    double avg5mHigh;
    double avg5mLow;
    double avg24hHigh;
    double avg24hLow;

    long realisticProfit;
    double gpHour;
    long fullLimitCost;

    long alchMarginPer;
    long alchProfit;
    double alchROI;
    double alchGpHour;

    long analyzedAtMs;
}
