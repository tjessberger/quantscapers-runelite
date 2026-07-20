# RuneLite Plugin Hub — submission prep

Status as of the last prep pass. **No submission has been opened yet** —
this repo is fully ready, but nothing has been pushed to
`runelite/plugin-hub`.

## Done

- Plugin builds clean, tests pass (`./gradlew build`)
- `runelite-plugin.properties`: `displayName`, `author`, `description`,
  `tags`, `plugins=`, `build=standard` all set. `build=standard` is
  correct here — `build.gradle` has no dependencies beyond
  `net.runelite:client` transitives (OkHttp, Gson both come from the
  client, nothing custom declared), so no third-party dependency
  verification step is needed and this repo qualifies for the
  fast-review build mode.
- `LICENSE` (BSD 2-Clause), correct copyright name
- Repo confirmed public: `https://github.com/tjessberger/quantscapers-runelite`
- Hub website icon: `icon.png` at repo root, 48x48 (cap is 48x72),
  present since the initial commit. This is a separate file from the
  in-client sidebar icon
  (`src/main/resources/com/quantscapers/panel_icon.png`, 24x24) —
  don't confuse or overwrite one with the other.
- `screenshot.png` added at repo root (287x896, portrait panel crop) —
  matches `README.md`'s `![screenshot](screenshot.png)` reference
  exactly, including case (renamed from `Screenshot.png` — GitHub/Linux
  CI is case-sensitive, Windows isn't, so this would've broken silently
  if left as-is).

## Network usage notes (for the PR description)

Hub reviewers scrutinize external network calls. This plugin's are
simple and precedented:

- All requests go directly to the OSRS Wiki's public Real-time Prices
  API (`prices.runescape.wiki`, `Constants.API_BASE`) — the same API
  other approved flipping plugins use. There is **no** proxy or
  first-party server involved; the plugin never contacts
  quantscapers.com's backend.
- Per the Wiki's own guidance, every request carries a User-Agent
  identifying the plugin plus the user's contact email. The plugin
  refuses to make any request until the user sets an email in settings
  (`isGated` in `QuantScapersPlugin`), so no anonymous traffic is ever
  sent.
- Polling is capped: 30s while the panel is open, paused when closed;
  timeseries (audit) calls capped at 3 per 30 minutes.
- No player data leaves the client. Requests are item-ID lookups only.
- No GE automation of any kind (see README "No automation" section).

## Submission steps (pending explicit go-ahead)

1. Pin the commit: current `main` HEAD at submission time (the
   `build=standard` properties change and the screenshot must be
   committed and pushed first, so the pinned hash includes them).
2. Fork `runelite/plugin-hub`, branch, add `plugins/quantscapers`:
   ```
   repository=https://github.com/tjessberger/quantscapers-runelite.git
   commit=<40-char hash of main HEAD>
   ```
3. Open the PR with a short description + the network usage notes above.
4. Watch the PR's CI (`build.yml` + "RuneLite Plugin Hub Checks") and
   push fixes to the same PR if either fails — the Hub's full packager
   validation only runs in their CI, it isn't practical to replicate
   locally.
5. Wait for manual review (days to weeks is normal).
