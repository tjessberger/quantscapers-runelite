# QuantScapers Economy Toolset Navigation

## Current structure

The plugin is organized as an OSRS Economy Toolset, not a flip-only terminal.

```text
QuantScapers header and refresh
QuantScapers.com menu and X @Quantscapers link
Overview | Scanner | Watchlist
Selected, vertically scrollable work area
```

## Overview

- Market Pulse
- 24-hour Top Gainer, Top Loser, and Most Traded boards
- Top Picks when candidates meet the stricter trust criteria

The local movers board uses the already loaded market snapshot and requires
meaningful daily trading volume. It is intentionally a lightweight local
approximation of the website's history-backed movers boards and adds no API
request.

## Scanner

- Flip View and Alch View
- Item search
- Presets, sorting, and expandable filters
- Verdict-led item research, order tickets, and Deep-Probe Audits

## Watchlist

- Locally tracked items
- Then-versus-now price and margin comparison
- Existing audit result plus Audit/Re-Audit access
- Local entry cap and expiry behavior

## Safety boundaries

- Tabs, search, Watchlist actions, and the overview do not fetch price data.
- The plugin uses only the existing OSRS Wiki API requests and limits.
- There is no Grand Exchange automation, telemetry, account system, cloud
  sync, or QuantScapers backend dependency.
- External links use the local browser only.

## Future changes

Keep the website visible in the fixed top strip. Preserve the three work areas
unless a user request clearly warrants a different navigation model. Do not
add new API calls, data sources, or automation without explicit approval and
a fresh compliance review.
