# QuantScapers Plugin — Bug Fix & UI Improvement Plan (v1.0.1)

Code review of the full repo as of 2026-07-08. Every item below was verified
against the actual source, not assumed. Work through them **in order** —
P0 items are correctness/compliance, P1 are functional UI bugs, P2 is polish.

## Ground rules for the implementer

1. **Do NOT touch anything in `engine/` math** (MarketAnalyzer, VerdictEngine,
   TicketBuilder, AuditEngine formulas). Those are verified 1:1 ports of the
   web app and covered by tests. The only engine-adjacent change permitted is
   the GpFormat fix in P2.5 (it lives in `ui/util/`).
2. After each item: `gradlew build` must stay green (29 tests). Run it — don't
   assume.
3. Visual verification is done by the OWNER, not you. After all items compile
   and tests pass, stop and tell the owner what to look at (each item's
   "Verify" line). Do not launch the RuneLite client yourself.
4. Never add network calls, lower any interval, or touch the auto-audit cap.
   Several fixes below REDUCE network traffic; none may increase it.

---

## P0-1. `auditCache` is not thread-safe (crash risk)

**Files:** `QuantScapersPlugin.java`

`auditCache` is a plain `HashMap` (line ~73). It is **written** on the
executor thread (`auditItemBlocking`) and **read on the EDT** — `render(...)`
receives the live map reference, stores it as `lastAuditCache`, and iterates
it during `rerenderFromCache()`. A user-triggered audit finishing while the
panel rebuilds = `ConcurrentModificationException` or torn reads. This is a
latent crash that will eventually fire.

**Fix (two parts, do both):**
1. Change the field to `ConcurrentHashMap`:
   `private final Map<Integer, AuditResult> auditCache = new ConcurrentHashMap<>();`
   (import `java.util.concurrent.ConcurrentHashMap`).
2. Never hand the live map to the EDT. At every
   `panel.render(display, topPicks, auditCache, true)` call site (there are
   two: `onConfigChanged` and `tick`), pass a snapshot instead:
   `new HashMap<>(auditCache)`.

**Verify:** build green. Owner: click Audit on a card while the 30s refresh
lands; no client error popup.

## P0-2. No fetch rate limit — panel toggling / manual refresh spams the API

**Files:** `QuantScapersPlugin.java`

`onActivate` fires `requestImmediateRefresh()` (full `/latest`+`/24h` fetch)
every time the sidebar icon is clicked, and the header ⟳ button does the
same, unlimited. Opening/closing the panel five times in ten seconds = five
full fetch cycles. The spec (RUNELITE_PLUGIN_SPEC.md §7.2) required manual
refresh to be ignored when the last fetch is under ~5s old; that was never
implemented. Also, RuneLite may fire `contactEmail` ConfigChanged more than
once while the user edits the field — each one currently triggers a fetch.

**Fix:** in `tick()`, right after the gate check and the `now` /
`panelVisible` lines, add a freshness short-circuit **before** any fetch:

```java
// Data under 10s old is fresh enough for any trigger (manual refresh,
// panel re-open, config event) - re-render from cache instead of refetching.
if (lastAnalysisMs > 0 && now - lastAnalysisMs < 10_000) {
    List<AnalyzedItem> topPicks = computeTopPicks(lastAnalyzed);
    List<AnalyzedItem> display = filterAndSort(lastAnalyzed);
    Map<Integer, AuditResult> snapshot = new HashMap<>(auditCache);
    SwingUtilities.invokeLater(() -> panel.render(display, topPicks, snapshot, true));
    return;
}
```

The scheduled 30s tick is unaffected (30s > 10s). Do NOT make the constant
configurable.

**Verify:** build green. Owner: spam the ⟳ button — the leads list should
still repopulate instantly (from cache) but `T-XXs` aside, no visible harm;
implementer: confirm by reading the code path that no fetch occurs within 10s.

## P0-3. Every audit triggers a full re-render via config churn

**Files:** `QuantScapersPlugin.java`

`auditItemBlocking`'s `finally` calls `persistAuditCache()` →
`config.setAuditCacheJson(...)` → RuneLite fires `ConfigChanged` (group
"quantscapers", key "auditCacheJson") → our `onConfigChanged` treats it as a
filter change and does a full `filterAndSort` + panel rebuild. So every
single audit currently causes TWO rebuilds (this one plus the intended
`auditComplete` one), and the plan's future writes would too.

**Fix (both parts):**
1. In `onConfigChanged`, ignore the persistence key — add right after the
   group check:
   ```java
   if ("auditCacheJson".equals(event.getKey())) {
       return;
   }
   ```
2. Stop persisting on every audit: remove `persistAuditCache()` from
   `auditItemBlocking`'s `finally` (keep `auditInFlight.set(false)` there).
   Persistence in `shutDown()` alone is sufficient — the cache TTL is 1h and
   a crash losing sub-hour audit results is acceptable.

**Verify:** build green. Owner: click Audit — the panel should update once,
not visibly flash twice.

## P0-4. Auto-audit blocks the render for up to 3 network round-trips

**Files:** `QuantScapersPlugin.java` — `tick()`

`runAutoAudit(topPicks)` runs BEFORE the render `invokeLater`, and each
audit is a blocking timeseries fetch. On a tick that spends its 3-call
budget, fresh prices sit invisible for several seconds while audits run.

**Fix:** reorder `tick()` so the render happens first, then audits, then a
second render only if any audit actually ran:

```java
List<AnalyzedItem> topPicks = computeTopPicks(analyzed);
List<AnalyzedItem> display = filterAndSort(analyzed);
Map<Integer, AuditResult> snap1 = new HashMap<>(auditCache);
SwingUtilities.invokeLater(() -> panel.render(display, topPicks, snap1, true));

boolean audited = runAutoAudit(topPicks);   // change return type to boolean
if (audited) {
    Map<Integer, AuditResult> snap2 = new HashMap<>(auditCache);
    SwingUtilities.invokeLater(() -> panel.render(display, topPicks, snap2, true));
}
```

`runAutoAudit` returns true if it called `auditItemBlocking` at least once.
Budget logic inside it is untouched.

**Verify:** build green. Owner: on a tick where Top Picks show "Not yet
audited", prices should appear immediately and audit results pop in a few
seconds later.

---

## P1-1. Filter dropdowns don't reflect preset/settings changes (worst UI bug)

**Files:** `FilterBar.java`, `QuantScapersPanel.java`

Choosing the "Fast Flips" preset writes `maxFillTime=H1, minROI=P1` to
config — but the Fill Time and Min ROI combos still display their old
values, because combos are only initialized at construction. Same problem
when the user changes a filter from RuneLite's settings page. The UI
actively lies about the active filters.

**Fix:**
1. In `FilterBar`, keep the four row-3 combos + the sort combo as fields.
   Add a guard flag and a sync method:
   ```java
   private boolean syncing = false;

   public void syncFromConfig() {
       syncing = true;
       try {
           sortCombo.setSelectedItem(plugin.getConfig().sortBy());
           budgetCombo.setSelectedItem(plugin.getConfig().maxBuyPrice());
           profitCombo.setSelectedItem(plugin.getConfig().minProfit());
           roiCombo.setSelectedItem(plugin.getConfig().minROI());
           fillCombo.setSelectedItem(plugin.getConfig().maxFillTime());
       } finally {
           syncing = false;
       }
   }
   ```
   Every combo ActionListener must start with `if (syncing) return;` so the
   programmatic sync can't echo writes back into config.
2. In `QuantScapersPanel.render(...)`, call `filterBar.syncFromConfig()`
   (render already runs after every ConfigChanged via the plugin).

**Verify:** build green. Owner: pick "Fast Flips" preset → Fill Time combo
should immediately read "<=1h" and Min ROI "1%+".

## P1-2. Countdown label drifts from the real poll schedule

**Files:** `QuantScapersPanel.java`

The `T-XXs` chip is a free-running 1s countdown that resets only on wrap or
manual refresh. The actual `scheduleWithFixedDelay` cadence shifts (delays
measure from tick END, and manual ticks don't move the schedule), so within
minutes the label shows T-15s while a refresh actually lands. It's decorative
fiction.

**Fix:** in `render(...)` (the method the plugin calls after every
successful data tick), reset the countdown: `countdown = Constants.HEARTBEAT_SECONDS;
headerBar.setCountdown(countdown);`. Also replace both hardcoded `30`s in
the panel (`countdown = 30` in `onActivate`, and the `countdown <= 1 ? 30`
wrap) with `Constants.HEARTBEAT_SECONDS` (import `com.quantscapers.engine.Constants`).
Note: after P0-2, cache-served renders also reset the label — acceptable;
it now means "seconds since last data delivery", which is honest.

**Verify:** owner: watch the chip across 2-3 refreshes — new data should
land at or very near T-0 each time.

## P1-3. Gate screen rebuilds every 30s

**Files:** `QuantScapersPanel.java`

While no email is set, every tick calls `showEmailGate()`, which does
`removeAll()` + rebuild + revalidate — 120 pointless rebuilds an hour, and
it also runs while the panel is hidden.

**Fix:** add a `private boolean gateShown = false;` field. At the top of
`showEmailGate()`: `if (gateShown) return;` then set `gateShown = true` at
the end. In `render(...)`, set `gateShown = false` (real data replaces the
gate).

**Verify:** build green; behavior identical to owner, minus waste.

## P1-4. "N LEADS" shows the render cap, not the real match count

**Files:** `QuantScapersPlugin.java`, `QuantScapersPanel.java`, `FilterBar.java`

`filterAndSort` truncates to `MAX_CARDS_RENDERED` (50) before the panel ever
sees the list, so with 130 matches the header says "50 LEADS" — wrong, and
it also hides that a cap exists.

**Fix:** have `filterAndSort` return the full filtered list, and move the
cap to the panel boundary. Simplest: in the plugin, compute
`int totalMatched = filtered.size();` before truncating, and change
`panel.render(...)` to take `totalMatched` as an extra parameter, passed to
`filterBar.setLeadsCount(totalMatched)`. In `setLeadsCount`, when
`n > Constants.MAX_CARDS_RENDERED`, show `n + " LEADS (top 50 shown)"`.
Update both render call sites and the gated/cache-path ones added in P0.

**Verify:** owner: loosen all filters (Budget Max, Min Profit Any, ROI 0.1%)
— count should exceed 50 with the "(top 50 shown)" suffix.

---

## P2-1. No expand/collapse affordance on cards

**Files:** `ItemCardBox.java`

Nothing signals that cards are clickable — the owner himself didn't discover
expansion until told. Add a chevron: in `buildHeader`, add a right-aligned
`JLabel` ("▸" collapsed / "▾" expanded, `SLATE_500`, RunescapeSmall) in
`BorderLayout.EAST` of the title row; flip its text inside the existing
`mouseClicked` toggle. Keep the label a field so the toggle can reach it.

**Verify:** owner: chevron visible on every card, flips on click.

## P2-2. Visual separator between Top Picks and the main list

**Files:** `QuantScapersPanel.java`

The owner read a Top Pick repeating as the first main-list card as a
duplication glitch. Give the main list its own header, mirroring the
TOP PICKS bar: a small `JPanel` (3px `AMBER_500` left accent + "ALL LEADS"
in RunescapeBold `AMBER_400` + subtitle "everything passing your filters"
in `SLATE_500` small font). Build it once in the constructor; in
`rerenderFromCache()`, add it as the first child of `listContainer`
whenever the display list is non-empty AND top picks are visible
(`topPicksBox.isVisible()`); skip it otherwise to save vertical space.

**Verify:** owner: with Top Picks present, the list below starts with an
ALL LEADS divider; with no picks, no divider.

## P2-3. Normalize BoxLayout alignment (subtle ragged layouts)

**Files:** `ItemCardBox.java`, `TopPicksBox.java`, `AuditSummary.java`

JLabel/JPanel default `alignmentX` is 0.5 (center); several children were
explicitly set to 0.0. Mixing them makes BoxLayout offset children
horizontally relative to the widest one — the source of the slightly
ragged left edges visible in screenshots. In every vertical `BoxLayout`
container in these three files, call `setAlignmentX(0f)` (or
`Component.LEFT_ALIGNMENT`) on EVERY child added, including labels created
by helper methods (`smallLabel`, `wrappedLabel`, `wrapped`, `line` — set it
inside the helpers so no call site can forget).

**Verify:** build green. Owner: card/pick-row content shares one flush left
edge.

## P2-4. Audit-skipped feedback + stale cache pruning

**Files:** `QuantScapersPlugin.java`

(a) `requestAudit` silently no-ops when another audit is in flight (CAS
fails) — the button shows "Auditing..." then reverts with no result. Change
`auditItemBlocking` to return `boolean` (false when skipped or failed-CAS),
and in `requestAudit` pass that through so the panel could message it;
minimum viable: log at info and still fire the callback (re-render restores
the button label — current behavior, now documented and intentional).
(b) `auditCache` never evicts: entries older than `AUDIT_TTL_MS` stay in
memory and get re-persisted forever. In `persistAuditCache()`, first remove
entries with `getTs() < now - Constants.AUDIT_TTL_MS`, then serialize.

**Verify:** build green; unit-testable: add a small test that `persist`
prunes (optional).

## P2-5. `GpFormat` mishandles negative values

**Files:** `ui/util/GpFormat.java`, `GpFormatTest.java`

`format(-1_625_000)` falls through every `>=` threshold and returns
"-1625000"; callers that prepend "+" would show "+-1625000". Unreachable
today (filters floor profit at 0) but the planned Alch view will hit it.
Fix: at the top, `if (n < 0) return "-" + format(-n);`. Add a test:
`assertEquals("-1.63M", GpFormat.format(-1_625_000));`.

**Verify:** `gradlew test` green (now 30 tests).

## P2-6. Stop the countdown timer on plugin shutdown

**Files:** `QuantScapersPanel.java`, `QuantScapersPlugin.java`

If the plugin is disabled while the panel is open, the panel's 1s Swing
Timer keeps firing on an orphaned component. Add
`public void shutdown() { if (countdownTimer != null) countdownTimer.stop(); }`
to the panel and call it from `QuantScapersPlugin.shutDown()`.

**Verify:** build green.

---

## Appendix: Web-app parity audit (2026-07-08) — DO NOT "fix" these

The owner compared deals on quantscapers.com/engine.html vs the plugin and
saw differences. A full line-by-line diff of the plugin engine against
engine.html v79 confirmed **all formulas are identical** (tax, EFT,
realistic profit, GP/hr, trap/stale gates, verdict gate order, ticket math,
audit hit-rate/trend/CV grades, presets, filter thresholds, sort keys).
Two literal-parity nits (JS `||` falsy-zero fallbacks on avg24h prices and
the nature rune price) were fixed in MarketAnalyzer on 2026-07-08.

The remaining visible differences are **state and timing, not logic**, and
must NOT be "corrected" in code:

1. **Independent filter state.** The web app persists filters in browser
   localStorage; the plugin persists them in RuneLite config. Unless the
   owner sets both to the same values, the two frontends legitimately show
   different lists. Not a bug.
2. **Different data snapshots.** The web app's latest/24h pass through
   api-cache.php's shared 30s server cache, and the browser polls on its own
   30s phase — web data can lag the plugin's direct fetch by up to ~60s.
   Margins, sort order, quote ages, and therefore verdicts shift within that
   window. Not a bug; do not add artificial delays to "match".
3. **Independent audit caches.** Verdict downgrades that depend on audit
   history ("Margin unstable (grade D/F)", "Sell price rarely reached")
   only apply on the frontend that has audited that item. Web audits live in
   localStorage, plugin audits in RuneLite config; the same item can be
   RISKY on one and DECENT/BUY on the other until both have audited it.
   Working as designed.
4. **Known intentional deltas:** plugin caps rendering at 50 cards (P1-4
   surfaces the true count); plugin has no "New Log" sort / session-alert
   tracking (deferred feature, not drift).

**How the owner A/B tests parity properly:** set identical filters on both,
audit the same item on both, compare within the same ~30s window; the top
of both lists should then agree, modulo quotes that moved between fetches.

## Completion checklist

- [x] All P0 + P1 + P2 items applied in order (implemented 2026-07-08)
- [x] `gradlew build` green, 29 test methods passing, 0 failures (GpFormat's
      negative-value case was added as an assertion inside the existing
      `matchesSpecWorkedExamples()` method, matching that file's convention,
      rather than as a new `@Test` — same coverage, method count unchanged)
- [x] No new network calls, no interval lowered, auto-audit cap untouched
      (AUTO_AUDIT_MAX_CALLS/AUTO_AUDIT_WINDOW_MS/HEARTBEAT_SECONDS all
      untouched; the new FRESH_ENOUGH_MS constant only ever *prevents* a
      fetch, never adds one)
- [x] Engine formula files unmodified in this pass — VerdictEngine.java,
      TicketBuilder.java, and AuditEngine.java untouched. MarketAnalyzer.java
      was modified, but that was the parity-audit fix from the appendix
      (JS `||` falsy-zero fallback), done in a separate, explicitly-requested
      prior pass — not part of this plan's scope.

### What to visually verify (owner, next time the client is open)

- **P2-1 chevron**: every card header now shows ▸ (collapsed) / ▾ (expanded)
  at the right edge, flipping on click.
- **P2-2 ALL LEADS divider**: when Top Picks is showing, the main list below
  it now starts with an amber "ALL LEADS / everything passing your filters"
  header before the first card.
- **P1-1 preset→combo sync**: pick "Fast Flips" — the Fill Time combo should
  immediately read "<=1h" and Min ROI "1%+" without reopening the panel.
- **P1-2 countdown accuracy**: the `T-XXs` chip should now hit ~T-0 right as
  new data actually lands, tick after tick.
- **P1-4 "(top 50 shown)"**: loosen every filter until matches exceed 50 —
  the leads count should read "N LEADS (top 50 shown)" instead of quietly
  capping at "50 LEADS".
- **P0-3 single-flash audit**: click Audit on a card — the panel should
  update once, not visibly flash twice.
- **P2-3 alignment**: card and pick-row content should now share one flush
  left edge instead of the slightly ragged edges seen before.
