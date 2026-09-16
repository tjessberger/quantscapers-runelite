package com.quantscapers;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.quantscapers.api.LatestResponse;
import com.quantscapers.api.MappingItem;
import com.quantscapers.api.PriceQuote;
import com.quantscapers.api.StatsResponse;
import com.quantscapers.api.TimeseriesResponse;
import com.quantscapers.api.VolumeStats;
import com.quantscapers.api.WikiPricesClient;
import com.quantscapers.engine.AnalyzedItem;
import com.quantscapers.engine.AuditEngine;
import com.quantscapers.engine.AuditResult;
import com.quantscapers.engine.Constants;
import com.quantscapers.engine.MarketAnalyzer;
import com.quantscapers.engine.SuppressionEngine;
import com.quantscapers.engine.TrackedTrade;
import com.quantscapers.engine.VaultPruner;
import com.quantscapers.engine.VerdictEngine;
import com.quantscapers.ui.QuantScapersPanel;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ImageUtil;
import com.quantscapers.ui.util.GpFormat;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
    name = "QuantScapers",
    description = "OSRS Market Intelligence tool with opportunity scanning, item research, price audits, and a local watchlist",
    tags = {"grand", "exchange", "economy", "market", "intelligence", "alchemy", "prices", "money", "watchlist"}
)
public class QuantScapersPlugin extends Plugin {

    @Inject private ClientToolbar clientToolbar;
    @Inject private OkHttpClient okHttpClient;
    @Inject private ScheduledExecutorService executor;
    @Inject private ConfigManager configManager;
    @Inject private Gson gson;
    @Inject private QuantScapersConfig config;
    @Inject private Client runeliteClient;
    @Inject private ClientThread clientThread;
    @Inject private Notifier notifier;
    @Inject private EventBus eventBus;

    // Notification state tracking. Session-only (never persisted).
    private final Set<Integer> matchingNotificationIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Long> lastNotifiedMs = new ConcurrentHashMap<>();

    @Provides
    QuantScapersConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(QuantScapersConfig.class);
    }

    private WikiPricesClient client;
    private QuantScapersPanel panel;
    private NavigationButton navButton;
    private ScheduledFuture<?> tickFuture;

    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final AtomicBoolean auditInFlight = new AtomicBoolean(false);
    // Written on the executor thread, read on the EDT via a snapshot taken at
    // render time (never hand the live map to Swing - see renderCurrentState()).
    private final Map<Integer, AuditResult> auditCache = new ConcurrentHashMap<>();
    private final AuditBudget manualAuditBudget = new AuditBudget();

    // Same discipline as auditCache - written on the executor thread, only ever
    // handed to Swing as a snapshot copy taken at render time.
    private final Map<Integer, TrackedTrade> vault = new ConcurrentHashMap<>();
    private final Object vaultPersistLock = new Object();

    // id -> suppressedUntilMs. An AVOID verdict confirmed by a fresh audit's low hit rate
    // is hidden from the leads list until this expires (see SuppressionEngine).
    private final Map<Integer, Long> suppressedUntilMs = new ConcurrentHashMap<>();
    private final Object suppressedPersistLock = new Object();
    private volatile boolean showSuppressed = false;

    // "Newest Entries" sort support - firstSeenMs is stamped the moment an item first
    // enters the user's FILTERED pool (not the whole market), matching the web app's
    // session-alert behavior: this is "newest to your view", not "newest on the wiki".
    // Session-only by design (never persisted) - persisting it would make everything
    // look equally old after a restart, defeating the point of the sort.
    private final Map<Integer, Long> firstSeenMs = new ConcurrentHashMap<>();

    private volatile List<AnalyzedItem> lastAnalyzed = new ArrayList<>();
    // Local UI state only. Searching never triggers a fetch or changes saved filters.
    private volatile String searchTerm = "";
    // Raw quotes from the same tick as lastAnalyzed - needed for vault "live" prices
    // since MarketAnalyzer filters out items with no analyzable quote, but the vault
    // still wants to show "no data" for those rather than losing the row.
    private volatile Map<Integer, PriceQuote> lastLatest = new HashMap<>();
    private volatile long lastAnalysisMs = 0;
    // Below this age, any trigger (manual refresh, panel re-open, config event)
    // re-renders from the last analysis instead of refetching from the wiki.
    private static final long FRESH_ENOUGH_MS = 60_000; // NEVER lower: Wiki API request floor

    @Override
    protected void startUp() {
        client = new WikiPricesClient(okHttpClient, gson);
        panel = new QuantScapersPanel(this);
        loadPersistedAuditCache();
        loadPersistedVault();
        loadPersistedSuppressed();

        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
        navButton = NavigationButton.builder()
            .tooltip("QuantScapers")
            .icon(icon)
            .priority(6)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        tickFuture = executor.scheduleWithFixedDelay(this::tick, 0, Constants.HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    protected void shutDown() {
        if (tickFuture != null) {
            tickFuture.cancel(false);
        }
        clientToolbar.removeNavigation(navButton);
        panel.shutdown();
        persistAuditCache();
        persistVault();
        persistSuppressed();
    }

    /** Called by the panel when it becomes visible again after being hidden. */
    public void requestImmediateRefresh() {
        executor.execute(this::tick);
    }

    /** Runs a user-requested price history audit. onComplete fires on the EDT. */
    public void requestAudit(AnalyzedItem item, Runnable onComplete) {
        executor.execute(() -> {
            if (!isWikiDataDisabled() && tryConsumeManualAuditBudget()) {
                auditItemBlocking(item);
            }
            SwingUtilities.invokeLater(onComplete);
        });
    }

    /** Same silent-skip-and-still-fire-callback pattern as the auditInFlight CAS below. */
    private boolean tryConsumeManualAuditBudget() {
        long now = System.currentTimeMillis();
        synchronized (manualAuditBudget) {
            if (now - manualAuditBudget.windowStartMs > Constants.MANUAL_AUDIT_WINDOW_MS) {
                manualAuditBudget.windowStartMs = now;
                manualAuditBudget.count = 0;
            }
            if (manualAuditBudget.count >= Constants.MANUAL_AUDIT_MAX_CALLS) {
                log.info("QuantScapers: manual audit rate limit hit ({}/{}min this window) - skipping",
                    Constants.MANUAL_AUDIT_MAX_CALLS, Constants.MANUAL_AUDIT_WINDOW_MS / 60_000);
                return false;
            }
            manualAuditBudget.count++;
            return true;
        }
    }

    /** Adds the item to the vault with a snapshot of its current buy/sell/tax. No-op past the entry cap. */
    public void toggleTrack(AnalyzedItem item) {
        if (vault.containsKey(item.getId())) {
            untrack(item.getId());
            return;
        }
        if (vault.size() >= Constants.VAULT_MAX_ENTRIES) {
            log.info("QuantScapers: vault full ({} entries) - not tracking item {}",
                Constants.VAULT_MAX_ENTRIES, item.getId());
            return;
        }
        long now = System.currentTimeMillis();
        vault.put(item.getId(), TrackedTrade.builder()
            .id(item.getId())
            .name(item.getName())
            .snapBuy(item.getLow())
            .snapSell(item.getHigh())
            .snapTax(item.getTax())
            .trackedAtMs(now)
            .lastSeenHealthyMs(now)
            .build());
        persistVault();
        renderCurrentState();
    }

    public void untrack(int itemId) {
        if (vault.remove(itemId) != null) {
            persistVault();
            renderCurrentState();
        }
    }

    public boolean isTracked(int itemId) {
        return vault.containsKey(itemId);
    }

    /** Updates the local item-name search without adding a market-data request. */
    public void setSearchTerm(String value) {
        String next = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!next.equals(searchTerm)) {
            searchTerm = next;
            renderCurrentState();
        }
    }

    /** Toggles whether confirmed-AVOID entries are shown in the leads list. Session-only. */
    public void setShowSuppressed(boolean show) {
        showSuppressed = show;
        renderCurrentState();
    }

    public boolean isShowSuppressed() {
        return showSuppressed;
    }

    public QuantScapersConfig getConfig() {
        return config;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    /** Opens RuneLite's configuration panel directly on QuantScapers. */
    public void openSettings() {
        OverlayMenuEntry configure = new OverlayMenuEntry(
            MenuAction.RUNELITE_OVERLAY_CONFIG, "Configure", "QuantScapers");
        eventBus.post(new OverlayMenuClicked(configure, new SettingsLinkOverlay(this)));
    }

    private static final class SettingsLinkOverlay extends Overlay {
        private SettingsLinkOverlay(QuantScapersPlugin plugin) {
            super(plugin);
        }

        @Override
        public Dimension render(Graphics2D graphics) {
            return null;
        }
    }

    /** Fresh local snapshot for UI rerenders after a user-clicked audit completes. */
    public Map<Integer, AuditResult> getAuditCacheSnapshot() {
        return new HashMap<>(auditCache);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"quantscapers".equals(event.getGroup())) {
            return;
        }
        if ("auditCacheJson".equals(event.getKey()) || "vaultJson".equals(event.getKey())
            || "vaultCollapsed".equals(event.getKey()) || "suppressedJson".equals(event.getKey())
            || "customPresetsJson".equals(event.getKey())) {
            // Our own persistence write-back, not a real filter/setting change -
            // reacting to it would double-render every audit or vault mutation.
            return;
        }
        if ("enableWikiMarketData".equals(event.getKey())) {
            // Consent state changed - update the gate or fetch immediately while visible.
            requestImmediateRefresh();
            return;
        }
        // Filters changed - re-derive the display list from the last analysis, no refetch needed.
        renderCurrentState();
    }

    private boolean isWikiDataDisabled() {
        return !config.enableWikiMarketData();
    }

    private void tick() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            refreshVisiblePanel();
        } catch (Exception e) {
            log.warn("QuantScapers: market sync failed", e);
            SwingUtilities.invokeLater(() -> panel.showSyncError());
        } finally {
            polling.set(false);
        }
    }

    private void refreshVisiblePanel() throws IOException {
        // Hidden panels make no network requests. onActivate() requests a fresh tick.
        if (panel == null || !panel.isShowing()) return;

        // Third-party Wiki access is explicit and disabled by default.
        if (isWikiDataDisabled()) {
            SwingUtilities.invokeLater(panel::showWikiDataGate);
            return;
        }

        long now = System.currentTimeMillis();
        if (lastAnalysisMs > 0 && now - lastAnalysisMs < FRESH_ENOUGH_MS) {
            renderCurrentState();
            return;
        }

        List<AnalyzedItem> analyzed = fetchMarket(now);
        checkNotifications(analyzed);

        renderCurrentState();

        boolean suppressionChanged = updateSuppression(analyzed, now);
        if (suppressionChanged) renderCurrentState();
    }

    private List<AnalyzedItem> fetchMarket(long now) throws IOException {
        List<MappingItem> mapping = client.fetchMapping();
        LatestResponse latestResponse = client.fetchLatest();
        StatsResponse stats24hResponse = client.fetch24h();
        if (mapping == null || latestResponse == null || stats24hResponse == null
            || latestResponse.getData() == null || stats24hResponse.getData() == null) {
            throw new IOException("QuantScapers: incomplete market response");
        }

        StatsResponse stats5mResponse = fetchOptional5mStats();
        Map<Integer, PriceQuote> latest = latestResponse.getData();
        Map<Integer, VolumeStats> stats5m = stats5mResponse == null ? null : stats5mResponse.getData();
        List<AnalyzedItem> analyzed = MarketAnalyzer.analyze(
            mapping,
            latest,
            stats24hResponse.getData(),
            stats5m,
            MarketAnalyzer.extractNatureRuneGp(latest),
            now);
        lastAnalyzed = analyzed;
        lastLatest = latest;
        lastAnalysisMs = now;
        pruneVault(latest, now);
        return analyzed;
    }

    private StatsResponse fetchOptional5mStats() {
        try {
            return client.fetch5m();
        } catch (IOException e) {
            log.debug("QuantScapers: 5m feed unavailable this tick", e);
            return null;
        }
    }

    /** Re-derives top picks and the filtered list from the last analysis and pushes to the panel - no refetch. */
    private void renderCurrentState() {
        List<AnalyzedItem> topPicks = computeTopPicks(lastAnalyzed);
        FilterResult result = filterAndSort(lastAnalyzed);
        int totalMatched = result.items.size();
        List<AnalyzedItem> display = totalMatched > Constants.MAX_CARDS_RENDERED
            ? result.items.subList(0, Constants.MAX_CARDS_RENDERED)
            : result.items;
        Map<Integer, AuditResult> snapshot = freshAuditSnapshot(System.currentTimeMillis());

        List<TrackedTrade> vaultSnapshot = new ArrayList<>(vault.values());
        vaultSnapshot.sort(Comparator.comparingLong(TrackedTrade::getTrackedAtMs).reversed());
        Map<Integer, PriceQuote> latestSnapshot = lastLatest;
        Map<Integer, PriceQuote> vaultLive = new HashMap<>();
        for (TrackedTrade t : vaultSnapshot) {
            PriceQuote q = latestSnapshot.get(t.getId());
            if (q != null) {
                vaultLive.put(t.getId(), q);
            }
        }

        SwingUtilities.invokeLater(() -> panel.render(display, topPicks, lastAnalyzed, snapshot, vaultSnapshot, vaultLive,
            totalMatched, result.suppressedCount, showSuppressed, true));
    }

    /**
     * Hard TTL plus dead-quote grace, run only on real successful fetches (never on the
     * gated/paused/fresh-enough/error paths) so offline time never counts against pruning.
     */
    private void pruneVault(Map<Integer, PriceQuote> latest, long now) {
        if (vault.isEmpty()) {
            return;
        }
        boolean removed = false;
        for (Map.Entry<Integer, TrackedTrade> entry : vault.entrySet()) {
            TrackedTrade t = entry.getValue();
            if (VaultPruner.pastHardTtl(t, now)) {
                vault.remove(entry.getKey());
                removed = true;
                continue;
            }
            if (VaultPruner.isQuoteHealthy(latest.get(t.getId()), now)) {
                vault.put(entry.getKey(), t.toBuilder().lastSeenHealthyMs(now).build());
            } else if (VaultPruner.pastDeadGrace(t, now)) {
                vault.remove(entry.getKey());
                removed = true;
            }
        }
        if (removed) {
            persistVault();
        }
    }

    private List<AnalyzedItem> computeTopPicks(List<AnalyzedItem> analyzed) {
        long maxBuyPrice = config.maxBuyPrice().value();
        List<AnalyzedItem> picks = new ArrayList<>();
        for (AnalyzedItem it : analyzed) {
            if (VerdictEngine.isBestBet(it) && it.getFullLimitCost() <= maxBuyPrice) {
                picks.add(it);
            }
        }
        picks.sort(Comparator.comparingDouble(AnalyzedItem::getGpHour).reversed());
        return picks.size() > 3 ? picks.subList(0, 3) : picks;
    }

    /** Filtered+sorted leads plus how many of them are currently hidden by suppression. */
    private static final class FilterResult {
        final List<AnalyzedItem> items;
        final int suppressedCount;

        FilterResult(List<AnalyzedItem> items, int suppressedCount) {
            this.items = items;
            this.suppressedCount = suppressedCount;
        }
    }

    /** Returns every match, unsorted-cap free - the caller decides how much of it to render. */
    private FilterResult filterAndSort(List<AnalyzedItem> analyzed) {
        long maxBuyPrice = config.maxBuyPrice().value();
        long minProfit = config.minProfit().value();
        double minROI = config.minROI().value();
        QuantScapersConfig.FillTimeCap fillCap = config.maxFillTime();
        boolean isAlch = config.viewMode() == QuantScapersConfig.ViewMode.ALCH;

        List<AnalyzedItem> passesUserFilters = new ArrayList<>();
        String activeSearch = searchTerm;
        for (AnalyzedItem it : analyzed) {
            if (passesFilters(it, activeSearch, maxBuyPrice, minProfit, minROI, fillCap, isAlch)) {
                passesUserFilters.add(it);
            }
        }

        trackFirstSeen(passesUserFilters);
        FilterResult result = applySuppression(passesUserFilters, System.currentTimeMillis());
        result.items.sort(comparatorFor(isAlch));
        return result;
    }

    private static boolean passesFilters(AnalyzedItem item, String search, long maxBuyPrice,
                                         long minProfit, double minROI,
                                         QuantScapersConfig.FillTimeCap fillCap, boolean isAlch) {
        if (!search.isEmpty() && !item.getName().toLowerCase(java.util.Locale.ROOT).contains(search)) return false;
        if (item.getFullLimitCost() > maxBuyPrice) return false;
        if (!isAlch && !fillCap.isAny() && item.getEft() > fillCap.minutes()) return false;
        double roi = isAlch ? item.getAlchROI() : item.getRoi();
        long profit = isAlch ? item.getAlchProfit() : item.getRealisticProfit();
        return roi >= minROI && profit >= minProfit;
    }

    private FilterResult applySuppression(List<AnalyzedItem> items, long now) {
        int suppressedCount = 0;
        List<AnalyzedItem> visible = new ArrayList<>();
        for (AnalyzedItem item : items) {
            boolean suppressed = isSuppressed(item.getId(), now);
            if (suppressed) suppressedCount++;
            if (!suppressed || showSuppressed) visible.add(item);
        }
        return new FilterResult(visible, suppressedCount);
    }

    private Comparator<AnalyzedItem> comparatorFor(boolean isAlch) {
        Comparator<AnalyzedItem> comparator;
        switch (config.sortBy()) {
            case GP_HR:
                comparator = Comparator.comparingDouble(isAlch ? AnalyzedItem::getAlchGpHour : AnalyzedItem::getGpHour).reversed();
                break;
            case ROI:
                comparator = Comparator.comparingDouble(isAlch ? AnalyzedItem::getAlchROI : AnalyzedItem::getRoi).reversed();
                break;
            case VOLUME:
                comparator = Comparator.comparingLong(AnalyzedItem::getVol24h).reversed();
                break;
            case NEWEST:
                comparator = Comparator.comparingLong(
                    (AnalyzedItem it) -> firstSeenMs.getOrDefault(it.getId(), 0L)).reversed();
                break;
            case PROFIT:
            default:
                comparator = Comparator.comparingLong(isAlch ? AnalyzedItem::getAlchProfit : AnalyzedItem::getRealisticProfit).reversed();
                break;
        }
        // Tiebreaker: always net profit descending.
        return comparator.thenComparing(Comparator.comparingLong(
            isAlch ? AnalyzedItem::getAlchProfit : AnalyzedItem::getRealisticProfit).reversed());
    }

    /**
     * Stamps firstSeenMs the moment an item enters the user's FILTERED pool - not the
     * whole market - so "Newest Entries" means "just started clearing your filters,"
     * matching the web app's session-alert semantics. Ids are never removed from the
     * seen set, so an item that drops out and comes back doesn't read as new again.
     */
    private void trackFirstSeen(List<AnalyzedItem> pool) {
        long now = System.currentTimeMillis();
        for (AnalyzedItem it : pool) {
            firstSeenMs.putIfAbsent(it.getId(), now);
        }
    }

    private boolean isSuppressed(int id, long now) {
        Long until = suppressedUntilMs.get(id);
        return until != null && until > now;
    }

    /**
     * Hard TTL prune plus new confirmations, run every tick against whatever's already
     * in auditCache - never triggers a fetch itself. See SuppressionEngine for the gate.
     */
    private boolean updateSuppression(List<AnalyzedItem> analyzed, long now) {
        boolean changed = suppressedUntilMs.entrySet().removeIf(e -> e.getValue() < now);
        for (AnalyzedItem it : analyzed) {
            if (suppressedUntilMs.containsKey(it.getId())) {
                continue; // already suppressed and not yet expired
            }
            AuditResult hist = auditCache.get(it.getId());
            if (SuppressionEngine.isConfirmedAvoid(it, hist, now)) {
                suppressedUntilMs.put(it.getId(), now + Constants.SUPPRESSION_DURATION_MS);
                changed = true;
            }
        }
        if (changed) {
            persistSuppressed();
        }
        return changed;
    }

    private boolean isAuditUsable(AuditResult audit, long nowMs) {
        if (audit == null) {
            return false;
        }
        long ttl = audit.isFailed() ? Constants.FAILED_AUDIT_RETRY_MS : Constants.AUDIT_TTL_MS;
        return nowMs >= audit.getTs() && nowMs - audit.getTs() < ttl;
    }

    private Map<Integer, AuditResult> freshAuditSnapshot(long nowMs) {
        Map<Integer, AuditResult> snapshot = new HashMap<>();
        for (Map.Entry<Integer, AuditResult> entry : auditCache.entrySet()) {
            if (isAuditUsable(entry.getValue(), nowMs)) {
                snapshot.put(entry.getKey(), entry.getValue());
            }
        }
        return snapshot;
    }

    /** Returns false only when skipped because another audit was already in flight. */
    private boolean auditItemBlocking(AnalyzedItem item) {
        if (!auditInFlight.compareAndSet(false, true)) {
            log.info("QuantScapers: audit for item {} skipped - another audit already in flight", item.getId());
            return false;
        }
        try {
            TimeseriesResponse resp = client.fetchTimeseries(item.getId());
            if (resp == null || resp.getData() == null || resp.getData().isEmpty()) {
                throw new IOException("QuantScapers: incomplete timeseries response for item " + item.getId());
            }
            AuditResult result = AuditEngine.audit(resp.getData(), item.getHigh(), System.currentTimeMillis());
            auditCache.put(item.getId(), result);
        } catch (Exception e) {
            log.debug("QuantScapers: audit failed for item {}", item.getId(), e);
            auditCache.put(item.getId(), AuditEngine.failed(System.currentTimeMillis()));
        } finally {
            auditInFlight.set(false);
        }
        return true;
    }

    private void loadPersistedAuditCache() {
        try {
            String json = config.auditCacheJson();
            if (json == null || json.isEmpty()) {
                return;
            }
            Type type = new com.google.gson.reflect.TypeToken<Map<Integer, AuditResult>>() {}.getType();
            Map<Integer, AuditResult> persisted = gson.fromJson(json, type);
            if (persisted == null) {
                return;
            }
            long cutoff = System.currentTimeMillis() - Constants.AUDIT_TTL_MS;
            for (Map.Entry<Integer, AuditResult> e : persisted.entrySet()) {
                if (e.getValue().getTs() > cutoff) {
                    auditCache.put(e.getKey(), e.getValue());
                }
            }
        } catch (Exception e) {
            log.debug("QuantScapers: failed to load persisted audit cache", e);
        }
    }

    private void persistAuditCache() {
        try {
            long cutoff = System.currentTimeMillis() - Constants.AUDIT_TTL_MS;
            auditCache.entrySet().removeIf(e -> e.getValue().getTs() < cutoff);
            config.setAuditCacheJson(gson.toJson(auditCache));
        } catch (Exception e) {
            log.debug("QuantScapers: failed to persist audit cache", e);
        }
    }

    private void loadPersistedVault() {
        try {
            String json = config.vaultJson();
            if (json == null || json.isEmpty()) {
                return;
            }
            Type type = new com.google.gson.reflect.TypeToken<Map<Integer, TrackedTrade>>() {}.getType();
            Map<Integer, TrackedTrade> persisted = gson.fromJson(json, type);
            if (persisted == null) {
                return;
            }
            // Hard TTL only on load - dead-quote grace is intentionally NOT re-evaluated here,
            // so time spent with the client closed never counts against that grace window.
            long now = System.currentTimeMillis();
            for (Map.Entry<Integer, TrackedTrade> e : persisted.entrySet()) {
                if (!VaultPruner.pastHardTtl(e.getValue(), now)) {
                    // Dead-quote grace is runtime-only: a closed client cannot observe
                    // whether a quote was unhealthy, so restart the grace clock here.
                    vault.put(e.getKey(), e.getValue().toBuilder().lastSeenHealthyMs(now).build());
                }
            }
        } catch (Exception e) {
            log.debug("QuantScapers: failed to load persisted vault", e);
        }
    }

    private void persistVault() {
        synchronized (vaultPersistLock) {
            try {
                config.setVaultJson(gson.toJson(vault));
            } catch (Exception e) {
                log.debug("QuantScapers: failed to persist vault", e);
            }
        }
    }

    private void loadPersistedSuppressed() {
        try {
            String json = config.suppressedJson();
            if (json == null || json.isEmpty()) {
                return;
            }
            Type type = new com.google.gson.reflect.TypeToken<Map<Integer, Long>>() {}.getType();
            Map<Integer, Long> persisted = gson.fromJson(json, type);
            if (persisted == null) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Map.Entry<Integer, Long> e : persisted.entrySet()) {
                if (e.getValue() > now) {
                    suppressedUntilMs.put(e.getKey(), e.getValue());
                }
            }
        } catch (Exception e) {
            log.debug("QuantScapers: failed to load persisted suppression list", e);
        }
    }

    private void persistSuppressed() {
        synchronized (suppressedPersistLock) {
            try {
                config.setSuppressedJson(gson.toJson(suppressedUntilMs));
            } catch (Exception e) {
                log.debug("QuantScapers: failed to persist suppression list", e);
            }
        }
    }

    public Gson getGson() {
        return gson;
    }

    private void checkNotifications(List<AnalyzedItem> analyzed) {
        if (!config.enableNotifications()) {
            matchingNotificationIds.clear();
            lastNotifiedMs.clear();
            return;
        }

        long now = System.currentTimeMillis();
        long maxBuyPrice = config.maxBuyPrice().value();
        long minProfit = config.notificationMinProfit().value();
        double minROI = config.notificationMinROI().value();
        QuantScapersConfig.NotificationViewMode viewMode = config.notificationViewMode();

        Set<Integer> currentTickMatches = new java.util.HashSet<>();

        for (AnalyzedItem it : analyzed) {
            if (!isNotificationCandidate(it, maxBuyPrice)) continue;
            boolean matchFlip = matchesFlipNotification(it, viewMode, minProfit, minROI);
            boolean matchAlch = matchesAlchNotification(it, viewMode, minProfit, minROI);
            if (matchFlip || matchAlch) notifyMatch(it, matchFlip, matchAlch, currentTickMatches, now);
        }

        matchingNotificationIds.removeIf(id -> !currentTickMatches.contains(id));
    }

    private static boolean isNotificationCandidate(AnalyzedItem item, long maxBuyPrice) {
        // Recommendations require both timestamps; a missing timestamp is not stale,
        // but it cannot prove that the apparent opportunity is fresh.
        return item.getFullLimitCost() <= maxBuyPrice
            && VerdictEngine.hasFreshQuotes(item)
            && !VerdictEngine.isPossibleTrap(item);
    }

    private static boolean matchesFlipNotification(AnalyzedItem item,
                                                     QuantScapersConfig.NotificationViewMode mode,
                                                     long minProfit, double minROI) {
        return (mode == QuantScapersConfig.NotificationViewMode.FLIP
            || mode == QuantScapersConfig.NotificationViewMode.BOTH)
            && item.getRoi() >= minROI
            && item.getRealisticProfit() >= minProfit;
    }

    private static boolean matchesAlchNotification(AnalyzedItem item,
                                                     QuantScapersConfig.NotificationViewMode mode,
                                                     long minProfit, double minROI) {
        return (mode == QuantScapersConfig.NotificationViewMode.ALCH
            || mode == QuantScapersConfig.NotificationViewMode.BOTH)
            && item.getAlchROI() >= minROI
            && item.getAlchProfit() >= minProfit;
    }

    private void notifyMatch(AnalyzedItem item, boolean matchFlip, boolean matchAlch,
                             Set<Integer> currentTickMatches, long now) {
        currentTickMatches.add(item.getId());
        boolean isNew = matchingNotificationIds.add(item.getId());
        Long lastNotified = lastNotifiedMs.get(item.getId());
        if (!isNew && lastNotified != null && now - lastNotified <= 600_000) return;
        lastNotifiedMs.put(item.getId(), now);
        triggerNotification(item, matchFlip, matchAlch);
    }

    private void triggerNotification(AnalyzedItem item, boolean matchFlip, boolean matchAlch) {
        StringBuilder message = new StringBuilder();

        if (matchFlip && matchAlch) {
            message.append(item.getName())
                .append(". Trade profit: ")
                .append(GpFormat.format(item.getRealisticProfit()))
                .append(" (")
                .append(String.format("%.1f", item.getRoi()))
                .append("% ROI), Alch profit: ")
                .append(GpFormat.format(item.getAlchProfit()))
                .append(" (")
                .append(String.format("%.1f", item.getAlchROI()))
                .append("% ROI)");
        } else if (matchFlip) {
            message.append("Market opportunity: ")
                .append(item.getName())
                .append(". Profit: ")
                .append(GpFormat.format(item.getRealisticProfit()))
                .append(" (")
                .append(String.format("%.1f", item.getRoi()))
                .append("% ROI)");
        } else {
            message.append("Alch opportunity: ")
                .append(item.getName())
                .append(". Profit: ")
                .append(GpFormat.format(item.getAlchProfit()))
                .append(" (")
                .append(String.format("%.1f", item.getAlchROI()))
                .append("% ROI)");
        }

        String plainMsg = message.toString();
        QuantScapersConfig.NotificationMode mode = config.notificationMode();

        if (mode == QuantScapersConfig.NotificationMode.TRAY || mode == QuantScapersConfig.NotificationMode.BOTH) {
            notifier.notify(plainMsg);
        }

        if (mode == QuantScapersConfig.NotificationMode.CHAT || mode == QuantScapersConfig.NotificationMode.BOTH) {
            String chatMessage = "<col=9f7fef>[QuantScapers]</col> " + plainMsg;
            clientThread.invokeLater(() -> {
                if (runeliteClient != null) {
                    runeliteClient.addChatMessage(
                        ChatMessageType.GAMEMESSAGE,
                        "",
                        chatMessage,
                        null
                    );
                }
            });
        }
    }

    private static final class AuditBudget {
        long windowStartMs = System.currentTimeMillis();
        int count = 0;
    }
}
