# RuneLite Plugin Hub Submission Package

## Status

- Open submission: <https://github.com/runelite/plugin-hub/pull/14066>
- Current Plugin Hub pin: `3b695e0c098fb51b206d36aadcb3e326c63ae690`
- Current local candidate: `a241ead68adfc8b7a98c5b46601356da091a2a15`
- Approval package: draft only; do not update the PR or its pinned commit until
  the owner explicitly approves the final package.
- The final pinned commit will change after the metadata and screenshot updates
  are committed.

## Recommended PR title

`Add QuantScapers OSRS Economy Toolset`

## Draft PR description

### Summary

QuantScapers is an in-client OSRS Economy Toolset for players who want to
understand Grand Exchange conditions before making their own decisions.

Rather than focusing on a single flip list, QuantScapers combines market
awareness, opportunity scanning, item research, risk checks, and personal
tracking in one RuneLite sidebar:

- **Market Overview:** highlights meaningful 24-hour gainers, losers, and
  actively traded items while filtering out thin-volume noise.
- **Flip Scanner:** ranks candidates using after-tax profit, ROI, volume,
  estimated fill time, price freshness, and budget filters.
- **Alch Scanner:** evaluates high-alchemy opportunities using current item and
  nature-rune prices, including profit, ROI, and estimated GP per hour.
- **Item Research:** provides current prices, volume context, trade tickets,
  freshness indicators, and plain-language BUY, DECENT, RISKY, or AVOID
  verdicts.
- **Deep-Probe Audits:** checks hourly price history for sell-price hit rate and
  seven-day margin stability.
- **Local Watchlist:** lets players save up to 25 items, compare current prices
  with saved values, and reuse completed audits.

The differentiation is the complete decision-support workflow: discover what
is moving, scan for a use case, investigate the item, check execution risk, and
track it locally without leaving RuneLite. QuantScapers does not promise
profits or replace player judgment.

### Player safety and privacy

- No Grand Exchange automation: the plugin never places, edits, or cancels an
  offer.
- No account, paywall, telemetry, cloud sync, player-data collection, or
  QuantScapers backend connection.
- Watchlist data, audit results, and settings remain local in RuneLite.
- Website and X links open in the player's browser only when clicked.
- Trade tickets are guidance for manual entry; copy actions use the local
  clipboard only.

### Network usage

The plugin connects only to the OSRS Wiki Real-time Prices API at
`https://prices.runescape.wiki/api/v1/osrs`.

- No price request is made until the player supplies a valid contact email.
- Every request includes that email in the User-Agent for API identification.
- While the panel is open, the scheduled market snapshot refreshes every 30
  seconds and pauses when the panel is closed.
- The item mapping is cached for 24 hours and the five-minute market feed is
  cached for 60 seconds.
- Tab changes, searches, filters, the Overview, and the Watchlist reuse the
  scheduled snapshot rather than triggering additional price requests.
- Automatic timeseries audits are capped at three calls per 30 minutes.
- User-requested manual audits are capped at ten calls per 10 minutes, and
  completed results are cached locally for reuse.

### Testing

- Unit and integration tests: `./gradlew test`
- Local RuneLite UI testing completed by the plugin owner before the final pin.

## Draft update comment

> Updated the pinned plugin version and submission description before
> maintainer review. QuantScapers has expanded from a flip-focused interface
> into an OSRS Economy Toolset with a market overview, Flip and Alch scanners,
> item research, Deep-Probe Audits, and a local Watchlist. The update does not
> add network destinations, telemetry, accounts, or trade automation. It still
> uses only the OSRS Wiki Real-time Prices API with the existing contact-email
> User-Agent and request limits.

## Final approval checklist

- [x] Product positioning updated from flip tool to OSRS Economy Toolset.
- [x] Plugin metadata describes the complete toolset.
- [x] PR title drafted.
- [x] PR description drafted.
- [x] PR update comment drafted.
- [x] Current Overview, Scanner, and Watchlist screenshots added under
  `screenshots/`.
- [ ] Select or compose a single current primary image for the Plugin Hub
  listing if the submission format requires one.
- [ ] Run `./gradlew test` after the final screenshot and metadata update.
- [ ] Review the complete local diff with the owner.
- [ ] Commit approved files and record the resulting 40-character hash.
- [ ] Update `plugins/quantscapers` in the existing Plugin Hub PR to that hash.
- [ ] Update the existing PR title and description; do not open a replacement.
- [ ] Post the update comment and wait for checks and maintainer review.

## Submission guardrails

- Keep PR #14066 open and ready for review; use draft status only if additional
  feature development resumes.
- Freeze feature scope after the final pin. During review, make only fixes
  required for approval.
- Keep all changes in the existing PR unless a Plugin Hub maintainer explicitly
  requests a replacement.
