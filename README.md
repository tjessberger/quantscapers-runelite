# QuantScapers

An in-client **OSRS Economy Toolset** for RuneLite.

QuantScapers helps you understand the Grand Exchange before you act: scan
profitable flips and alchs, spot liquid market movers, research individual
items, and keep a local Watchlist of prices that matter to you.

![QuantScapers screenshot](screenshot.png)

## What you can do

- **Overview the market.** See a local Market Pulse plus 24-hour Top Gainer,
  Top Loser, and Most Traded items. Movers require meaningful daily trading
  volume, so thinly traded noise does not dominate the board.
- **Scan flips.** Find opportunities using profit, ROI, budget, fill-time,
  volume, and freshness filters. Each item receives a clear BUY, DECENT,
  RISKY, or AVOID verdict with an explanation.
- **Scan alchs.** Switch to Alch View to rank high-alchemy opportunities by
  profit, ROI, or GP per hour using the same scheduled price snapshot.
- **Research items.** Search the current market by item name, expand an item
  for its price details and trade ticket, or run a Deep-Probe Audit.
- **Use the Watchlist.** Track up to 25 items locally, compare prices and
  margin now against when you saved them, and audit a saved item directly.
- **Explore more on the web.** The in-plugin `QUANTSCAPERS.COM` menu links to
  the market overview, item table, and gear indices. Follow
  [@Quantscapers on X](https://x.com/Quantscapers) for updates.

## How it works

QuantScapers is decision support, not automation.

- **Order tickets** provide buy price, sell price, quantity, after-tax profit,
  and a walk-away timer for you to enter manually.
- **Trust guards** flag stale quotes, possible price manipulation, and cooling
  momentum before a candidate is presented as a clean flip.
- **Deep-Probe Audits** use an item's hourly history to calculate sell-price
  hit rate and a seven-day margin-stability grade.
- **Notifications** are optional and can be limited to Flip candidates, Alch
  candidates, or both.

## Data source and responsible use

QuantScapers uses the [OSRS Wiki Real-time Prices API](https://prices.runescape.wiki/osrs/).

- It refreshes market data every 30 seconds while the panel is open and pauses
  when it is closed.
- It uses only the existing Wiki API endpoints; switching tabs, searching,
  opening the Watchlist, and using the market overview do not create extra
  price requests.
- Deep-Probe Audit calls are on demand or reserved for Top Picks, with the
  automatic audit budget capped at three calls per 30 minutes.
- Every request includes a User-Agent with the contact email you configure in
  RuneLite settings. The plugin makes no API requests until a valid contact
  email is set.

Your contact email is saved locally and is sent only as part of the Wiki API
User-Agent. QuantScapers has no telemetry, account system, cloud sync, or
first-party data collection.

## No Grand Exchange automation

QuantScapers never reads, writes, places, edits, or cancels Grand Exchange
offers. It does not perform actions for you. Any ticket values are guidance
for you to enter yourself; copy actions use your clipboard only.

## Development

Run the test suite:

```powershell
.\gradlew.bat test
```

## License

BSD 2-Clause. See [LICENSE](LICENSE).
