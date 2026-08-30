package com.quantscapers.ui;

import com.quantscapers.QSColors;
import com.quantscapers.QuantScapersPlugin;
import com.quantscapers.api.PriceQuote;
import com.quantscapers.engine.AnalyzedItem;
import com.quantscapers.engine.AuditResult;
import com.quantscapers.engine.Constants;
import com.quantscapers.engine.TrackedTrade;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.border.LineBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;

/**
 * Root panel with a fixed header, website links, and internal navigation.
 * Market data is rendered into one of three local views; switching views never fetches data.
 */
public class QuantScapersPanel extends PluginPanel {

    private final QuantScapersPlugin plugin;
    private final ItemManager itemManager;

    private final HeaderBar headerBar;
    private final SiteLinksBar siteLinksBar;
    private final PluginTabBar tabBar;
    private final FilterBar filterBar;
    private final TopPicksBox topPicksBox;
    private final MarketPulseBox marketPulseBox;
    private final VaultBox vaultBox;
    private final JPanel errorBanner;
    private final JPanel wikiDataNotice;
    private final ScrollableListPanel overviewContainer;
    private final ScrollableListPanel scannerContainer;
    private final ScrollableListPanel watchlistContainer;
    private final JPanel listContainer;
    private final JPanel allLeadsHeader;
    private final PluginErrorPanel overviewState;
    private final PluginErrorPanel scannerState;
    private final PluginErrorPanel watchlistState;
    private final JScrollPane contentScrollPane;

    private final Set<Integer> expandedIds = new HashSet<>();
    private final Set<Integer> auditingIds = new HashSet<>();
    private final java.util.List<ItemCardBox> liveCards = new java.util.ArrayList<>();

    private Map<Integer, AuditResult> lastAuditCache = new HashMap<>();
    private List<AnalyzedItem> lastDisplay = java.util.Collections.emptyList();
    private List<AnalyzedItem> lastTopPicks = java.util.Collections.emptyList();
    private List<AnalyzedItem> lastMarket = java.util.Collections.emptyList();
    private List<TrackedTrade> lastVault = java.util.Collections.emptyList();
    private Map<Integer, PriceQuote> lastVaultLive = new HashMap<>();
    private int lastTotalMatched = 0;
    private boolean gateShown = false;
    private PluginTabBar.Tab selectedTab = PluginTabBar.Tab.OVERVIEW;

    private Timer countdownTimer;
    private int countdown = Constants.HEARTBEAT_SECONDS;

    public QuantScapersPanel(QuantScapersPlugin plugin, ItemManager itemManager) {
        super(false);
        this.plugin = plugin;
        this.itemManager = itemManager;

        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        headerBar = new HeaderBar(this::onManualRefresh);
        siteLinksBar = new SiteLinksBar();
        tabBar = new PluginTabBar(this::showTab);
        filterBar = new FilterBar(plugin);
        topPicksBox = new TopPicksBox(plugin);
        topPicksBox.addPropertyChangeListener("auditComplete", e -> refreshAuditsAndRerender());
        marketPulseBox = new MarketPulseBox(this::openInScanner);

        vaultBox = new VaultBox(plugin, itemManager);
        vaultBox.addPropertyChangeListener("untrack", e -> plugin.untrack((Integer) e.getNewValue()));
        vaultBox.addPropertyChangeListener("auditComplete", e -> refreshAuditsAndRerender());

        errorBanner = buildErrorBanner();
        errorBanner.setVisible(false);
        wikiDataNotice = buildWikiDataNotice();
        refreshWikiDataNotice();

        overviewContainer = contentPanel();
        scannerContainer = contentPanel();
        watchlistContainer = contentPanel();
        listContainer = contentPanel();
        allLeadsHeader = buildAllLeadsHeader();

        overviewState = statePanel();
        scannerState = statePanel();
        watchlistState = statePanel();
        overviewState.setContent("QUANTSCAPERS", "Loading market data...");
        scannerState.setContent("QUANTSCAPERS", "Loading market data...");
        watchlistState.setContent("WATCHLIST", "Track an item in Scanner to see it here.");

        overviewContainer.add(marketPulseBox);
        overviewContainer.add(topPicksBox);
        overviewContainer.add(overviewState);

        scannerContainer.add(filterBar);
        scannerContainer.add(Box.createVerticalStrut(4));
        scannerContainer.add(listContainer);
        // BoxLayout positions siblings around their alignment points. FilterBar's
        // default center alignment previously pushed the left-aligned card list
        // halfway across the panel even though both components allowed full width.
        alignLeft(scannerContainer);

        watchlistContainer.add(vaultBox);
        watchlistContainer.add(watchlistState);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        north.add(headerBar);
        north.add(siteLinksBar);
        north.add(tabBar);
        north.add(wikiDataNotice);
        north.add(errorBanner);
        alignLeft(north);

        contentScrollPane = new JScrollPane(overviewContainer);
        contentScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        contentScrollPane.setBorder(BorderFactory.createEmptyBorder());
        contentScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        contentScrollPane.setOpaque(false);
        contentScrollPane.getViewport().setOpaque(false);

        add(north, BorderLayout.NORTH);
        add(contentScrollPane, BorderLayout.CENTER);
    }

    @Override
    public void onActivate() {
        countdown = Constants.HEARTBEAT_SECONDS;
        headerBar.setCountdown(countdown);
        if (countdownTimer == null) {
            countdownTimer = new Timer(1000, e -> tickCountdown());
        }
        countdownTimer.start();
        refreshWikiDataNotice();
        plugin.requestImmediateRefresh();
    }

    @Override
    public void onDeactivate() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
    }

    /** Called by the plugin on shutDown() so the 1s timer doesn't keep firing on an orphaned panel. */
    public void shutdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
    }

    private void tickCountdown() {
        countdown = countdown <= 1 ? Constants.HEARTBEAT_SECONDS : countdown - 1;
        headerBar.setCountdown(countdown);
        long now = System.currentTimeMillis();
        for (ItemCardBox card : liveCards) {
            card.refreshAges(now);
        }
    }

    private void onManualRefresh() {
        countdown = Constants.HEARTBEAT_SECONDS;
        headerBar.setCountdown(countdown);
        plugin.requestImmediateRefresh();
    }

    private void showTab(PluginTabBar.Tab tab) {
        selectedTab = tab;
        tabBar.setSelected(tab);
        contentScrollPane.setViewportView(tab == PluginTabBar.Tab.OVERVIEW ? overviewContainer
            : tab == PluginTabBar.Tab.SCANNER ? scannerContainer : watchlistContainer);
        contentScrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0));
        revalidate();
        repaint();
    }

    /** Sends an Overview insight into the execution-oriented Scanner without fetching again. */
    private void openInScanner(AnalyzedItem item) {
        plugin.setSearchTerm(item.getName());
        showTab(PluginTabBar.Tab.SCANNER);
    }

    /** Called on the EDT by the plugin after every successful analysis tick. */
    public void render(List<AnalyzedItem> display, List<AnalyzedItem> topPicks, List<AnalyzedItem> market,
                        Map<Integer, AuditResult> auditCache, List<TrackedTrade> vault,
                        Map<Integer, PriceQuote> vaultLive, int totalMatched,
                        int suppressedCount, boolean showSuppressed, boolean syncOk) {
        gateShown = false;
        countdown = Constants.HEARTBEAT_SECONDS;
        headerBar.setCountdown(countdown);
        filterBar.setVisible(true);
        filterBar.syncFromConfig();
        lastDisplay = display;
        lastTopPicks = topPicks;
        lastMarket = market;
        lastAuditCache = auditCache;
        lastVault = vault;
        lastVaultLive = vaultLive;
        lastTotalMatched = totalMatched;
        marketPulseBox.update(market);
        filterBar.setSuppressedInfo(suppressedCount, showSuppressed);
        errorBanner.setVisible(!syncOk);
        refreshWikiDataNotice();
        rerenderFromCache();
    }

    public void showSyncError() {
        errorBanner.setVisible(true);
    }

    /** Called when Wiki market data is disabled; no API request has occurred. */
    public void showWikiDataGate() {
        if (gateShown) {
            return;
        }
        gateShown = true;
        filterBar.setVisible(false);
        marketPulseBox.setVisible(false);
        topPicksBox.setVisible(false);
        vaultBox.setVisible(false);
        errorBanner.setVisible(false);
        refreshWikiDataNotice();

        overviewState.setContent("Wiki market data is off", "Open plugin settings to load market intelligence.");
        overviewState.setVisible(true);
        scannerState.setContent("Wiki market data is off", "Open plugin settings to load market data.");
        listContainer.removeAll();
        listContainer.add(scannerState);
        watchlistState.setContent("Wiki market data is off", "Open plugin settings to refresh tracked prices.");
        watchlistState.setVisible(true);
        tabBar.setWatchlistCount(0);
        revalidate();
        repaint();
    }

    private void rerenderFromCache() {
        int scannerScroll = selectedTab == PluginTabBar.Tab.SCANNER
            ? contentScrollPane.getViewport().getViewPosition().y : 0;

        filterBar.setLeadsCount(lastTotalMatched);
        topPicksBox.update(lastTopPicks, lastAuditCache);
        vaultBox.update(lastVault, lastVaultLive, lastAuditCache, analyzedById(lastMarket));
        tabBar.setWatchlistCount(lastVault.size());
        overviewState.setVisible(!marketPulseBox.isVisible() && !topPicksBox.isVisible());
        if (overviewState.isVisible()) {
            overviewState.setContent("No market data yet", "Market Intelligence appears after a successful refresh.");
        }
        watchlistState.setVisible(lastVault.isEmpty());
        if (watchlistState.isVisible()) {
            watchlistState.setContent("WATCHLIST", "Track an item in Scanner to see it here.");
        }

        renderScannerList();
        revalidate();
        repaint();

        if (selectedTab == PluginTabBar.Tab.SCANNER) {
            javax.swing.SwingUtilities.invokeLater(() -> contentScrollPane.getViewport().setViewPosition(
                new java.awt.Point(0, Math.min(scannerScroll,
                    Math.max(0, scannerContainer.getHeight() - contentScrollPane.getViewport().getHeight())))));
        }
    }

    private void renderScannerList() {
        Set<Integer> trackedIds = new HashSet<>();
        for (TrackedTrade t : lastVault) {
            trackedIds.add(t.getId());
        }

        listContainer.removeAll();
        liveCards.clear();
        if (lastDisplay.isEmpty()) {
            scannerState.setContent("No matching items", "Try lower budget, profit, ROI, or fill-time filters.");
            listContainer.add(scannerState);
            return;
        }

        listContainer.add(allLeadsHeader);
        listContainer.add(Box.createVerticalStrut(4));
        for (AnalyzedItem item : lastDisplay) {
            boolean expanded = expandedIds.contains(item.getId());
            boolean auditing = auditingIds.contains(item.getId());
            boolean tracked = trackedIds.contains(item.getId());
            ItemCardBox card = new ItemCardBox(plugin, itemManager, item, lastAuditCache.get(item.getId()),
                expanded, auditing, tracked, isExpanded -> {
                    if (isExpanded) expandedIds.add(item.getId());
                    else expandedIds.remove(item.getId());
                });
            card.addPropertyChangeListener("auditStarted", e -> {
                auditingIds.add(item.getId());
                rerenderFromCache();
            });
            card.addPropertyChangeListener("auditComplete", e -> {
                auditingIds.remove(item.getId());
                refreshAuditsAndRerender();
            });
            card.addPropertyChangeListener("trackToggled", e -> plugin.toggleTrack(item));
            liveCards.add(card);
            listContainer.add(card);
            listContainer.add(Box.createVerticalStrut(6));
        }
    }

    private static Map<Integer, AnalyzedItem> analyzedById(List<AnalyzedItem> market) {
        Map<Integer, AnalyzedItem> byId = new HashMap<>();
        for (AnalyzedItem item : market) {
            byId.put(item.getId(), item);
        }
        return byId;
    }

    private void refreshAuditsAndRerender() {
        lastAuditCache = plugin.getAuditCacheSnapshot();
        rerenderFromCache();
    }

    private void refreshWikiDataNotice() {
        wikiDataNotice.setVisible(!plugin.getConfig().enableWikiMarketData());
    }

    private JPanel buildWikiDataNotice() {
        JPanel banner = new JPanel(new BorderLayout(4, 0));
        banner.setBackground(new java.awt.Color(0xf5, 0x9e, 0x0b, 25));
        banner.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JLabel prompt = new JLabel("Wiki market data is off.");
        prompt.setForeground(QSColors.AMBER_400);
        prompt.setFont(FontManager.getRunescapeSmallFont());

        JLabel openLabel = new JLabel("Open ");
        openLabel.setForeground(QSColors.AMBER_400);
        openLabel.setFont(FontManager.getRunescapeSmallFont());

        JLabel settingsLink = new JLabel("<html><u>plugin settings</u></html>");
        settingsLink.setForeground(QSColors.AMBER_400);
        settingsLink.setFont(FontManager.getRunescapeSmallFont());
        settingsLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsLink.setToolTipText("Open QuantScapers settings");
        settingsLink.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { plugin.openSettings(); }
        });

        JLabel period = new JLabel(" to enable it.");
        period.setForeground(QSColors.AMBER_400);
        period.setFont(FontManager.getRunescapeSmallFont());

        JPanel actionPanel = new JPanel();
        actionPanel.setLayout(new BoxLayout(actionPanel, BoxLayout.X_AXIS));
        actionPanel.setOpaque(false);
        actionPanel.add(openLabel);
        actionPanel.add(Box.createHorizontalStrut(3));
        actionPanel.add(settingsLink);
        actionPanel.add(period);

        JPanel promptPanel = new JPanel();
        promptPanel.setLayout(new BoxLayout(promptPanel, BoxLayout.Y_AXIS));
        promptPanel.setOpaque(false);
        promptPanel.add(prompt);
        promptPanel.add(actionPanel);

        JLabel why = new JLabel("<html><u>Why?</u></html>");
        why.setForeground(QSColors.AMBER_400);
        why.setFont(FontManager.getRunescapeSmallFont());
        why.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        why.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { showWhyPopover(why); }
        });

        banner.add(promptPanel, BorderLayout.CENTER);
        banner.add(why, BorderLayout.EAST);
        return banner;
    }

    private void showWhyPopover(JComponent anchor) {
        JLabel content = new JLabel("<html><body style='width:190px'>When you enable this option, "
            + "QuantScapers requests market snapshots from the OSRS Wiki Prices API. The Wiki receives "
            + "your IP address as part of the normal HTTP request.<br><br>QuantScapers does not send "
            + "RuneScape account details, player data, Grand Exchange offers, or telemetry. Requests use "
            + "the project issue tracker as the contact route. We do not ask for your email.</body></html>");
        content.setForeground(QSColors.SLATE_200);
        content.setFont(FontManager.getRunescapeSmallFont());
        content.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(QSColors.BG_DEEP);
        wrapper.setBorder(new LineBorder(QSColors.BORDER_AMBER, 1, true));
        wrapper.add(content, BorderLayout.CENTER);

        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createEmptyBorder());
        popup.setBackground(QSColors.BG_DEEP);
        popup.add(wrapper);
        popup.show(anchor, 0, anchor.getHeight() + 2);
    }

    private JPanel buildAllLeadsHeader() {
        JLabel title = new JLabel("MATCHING ITEMS");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(QSColors.AMBER_400);
        JLabel subtitle = new JLabel("Items that match your filters");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(QSColors.SLATE_500);

        JPanel headerText = new JPanel();
        headerText.setLayout(new BoxLayout(headerText, BoxLayout.Y_AXIS));
        headerText.setOpaque(false);
        headerText.add(title);
        headerText.add(subtitle);

        JPanel accent = new JPanel();
        accent.setBackground(QSColors.AMBER_500);
        accent.setPreferredSize(new Dimension(3, 1));

        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        bar.add(accent, BorderLayout.WEST);
        bar.add(headerText, BorderLayout.CENTER);
        return bar;
    }

    private JPanel buildErrorBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new java.awt.Color(0xef, 0x44, 0x44, 25));
        banner.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JLabel label = new JLabel("Market data could not load. Retrying soon.");
        label.setForeground(QSColors.RED_400);
        label.setFont(FontManager.getRunescapeSmallFont());
        banner.add(label, BorderLayout.CENTER);
        return banner;
    }

    private static ScrollableListPanel contentPanel() {
        ScrollableListPanel panel = new ScrollableListPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private static PluginErrorPanel statePanel() {
        PluginErrorPanel panel = new PluginErrorPanel();
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private static void alignLeft(JPanel panel) {
        for (java.awt.Component component : panel.getComponents()) {
            if (component instanceof JComponent) {
                ((JComponent) component).setAlignmentX(LEFT_ALIGNMENT);
            }
        }
    }

    private static final class ScrollableListPanel extends JPanel implements Scrollable {
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return visibleRect.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
