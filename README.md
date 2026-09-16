# QuantScapers

QuantScapers helps you find Grand Exchange flips and alch opportunities
without automating trades. See what's moving, check an item's margin and price
history, and save items to a local watchlist.

| Market overview | Scanner | Watchlist |
| --- | --- | --- |
| ![Market overview](screenshots/overview.png) | ![Scanner](screenshots/scanner.png) | ![Watchlist](screenshots/watchlist.png) |

## What you can do

- **See what's moving.** Overview shows whether the market is rising, falling,
  or mixed, along with active items and recent momentum. You can filter out
  low-volume or low-value noise and send any item straight to Scanner.
- **Find a flip.** Scanner ranks items by profit, ROI, budget, fill time,
  volume, and price freshness. Each result explains why it looks worth buying
  or why you may want to avoid it.
- **Check alch opportunities.** Alch View ranks items by profit, ROI, or GP per
  hour using the same price snapshot.
- **Look into an item.** Search by name, open its price details and trade
  ticket, or request an hourly price-history audit.
- **Save items for later.** Your local Watchlist holds up to 25 items and shows
  how their prices and margins have changed since you saved them.
- **Jump to the website.** The `QUANTSCAPERS.COM` menu opens our market pages
  and gear indices. Updates are posted at
  [@Quantscapers on X](https://x.com/Quantscapers).

## How it works

QuantScapers does not place trades. It gives you the numbers and leaves the
decision to you.

- Trade tickets show what to buy, what to sell for, how many to trade, and the
  expected profit after tax. A walk-away timer helps keep old ideas from
  hanging around too long.
- Stale prices, unusually wide spreads, and cooling momentum are called out
  before you act.
- When you request an audit, QuantScapers checks the item's hourly price
  history to see how often the target sell price was reached and how steady
  the margin has been over the past week.
- Notifications are optional. You can use them for flips, alch opportunities,
  or both.

## Data source and privacy

QuantScapers uses the [OSRS Wiki Real-time Prices API](https://prices.runescape.wiki/osrs/).

- Wiki market data is off by default. No request is made until you explicitly
  enable it in the plugin settings.
- While the panel is visible, the latest-price snapshot refreshes at most once
  per 60 seconds. All polling stops when the panel is closed.
- It uses only the existing Wiki API endpoints; switching tabs, searching,
  opening the Watchlist, and using the market overview do not create extra
  price requests.
- Five-minute and 24-hour statistics are cached for five minutes, and the item
  mapping is cached for 24 hours. Transient API failures trigger capped
  exponential backoff.
- Price history audits run only when you request one and are capped at ten
  calls per ten minutes.
- Every request includes a fixed descriptive User-Agent linking to the public
  GitHub issue tracker; no personal email is requested. The Wiki API receives
  your IP address as normal HTTP metadata.

QuantScapers sends no RuneScape account data, credentials, GE offers,
telemetry, or cloud sync data, and has no first-party data collection.

## No Grand Exchange automation

QuantScapers never reads, writes, places, edits, or cancels Grand Exchange
offers. It does not perform actions for you. Any ticket values are guidance
for you to enter yourself; copy actions use your clipboard only.

## License

BSD 2-Clause. Copyright © 2026 Tim to Slay. See [LICENSE](LICENSE).
