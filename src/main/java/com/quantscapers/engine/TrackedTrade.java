package com.quantscapers.engine;

import lombok.Builder;
import lombok.Value;

/**
 * A user-tracked ("vaulted") item. Holds the buy/sell/tax snapshot from the
 * moment it was tracked so "margin then" stays exact even as prices move —
 * live buy/sell is never stored here, it's derived at render time from the
 * tick's price feed. Intentionally holds no colors (see AuditResult), which
 * keeps this class trivially Gson-serializable for the persisted vault.
 */
@Value
@Builder(toBuilder = true)
public class TrackedTrade {
    int id;
    String name;
    long snapBuy;
    long snapSell;
    int snapTax;
    long trackedAtMs;
    long lastSeenHealthyMs; // persisted dead-quote grace state; advanced only by real fetches
}
