package com.quantscapers.engine;

import lombok.Builder;
import lombok.Value;

/**
 * Result of a deep-probe (1h timeseries) audit for one item, cached for
 * AUDIT_TTL_MS. Intentionally holds no colors — those are a rendering
 * concern derived at draw time from {@code verdict}/{@code stabilityGrade}
 * (see QSColors), which also keeps this class trivially Gson-serializable
 * for the plugin's persisted audit cache.
 */
@Value
@Builder
public class AuditResult {
    long ts;
    int hits1d;
    int hits7d;
    double trend;
    String verdict;
    int likelihood;
    Character stabilityGrade; // null when fewer than 24 margin samples
    double[] sparkData;
    boolean failed;
}
