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
 * Root panel: header, filter bar, top picks, and a scrollable list of item
 * cards. Layout per RUNELITE_PLUGIN_SPEC.md §7.1.
 */
public class QuantScapersPanel extends PluginPanel {

    private final QuantScapersPlugin plugin;
    private final ItemManager itemManager;

    private final HeaderBar headerBar;
    private final FilterBar filterBar;
    private final TopPicksBox topPicksBox;
    private final VaultBox vaultBox;
    private final JPanel errorBanner;
    private final JPanel emailNotice;
    private final JPanel listContainer;
    private final JPanel allLeadsHeader;
    private final PluginErrorPanel statePanel;

    private final Set<Integer> expandedIds = new HashSet<>();
    private final Set<Integer> auditingIds = new HashSet<>();
    private final java.util.List<ItemCardBox> liveCards = new java.util.ArrayList<>();

    private Map<Integer, AuditResult> lastAuditCache = new HashMap<>();
    private List<AnalyzedItem> lastDisplay = java.util.Collections.emptyList();
    private List<AnalyzedItem> lastTopPicks = java.util.Collections.emptyList();
    private List<TrackedTrade> lastVault = java.util.Collections.emptyList();
    private Map<Integer, PriceQuote> lastVaultLive = new HashMap<>();
    private int lastTotalMatched = 0;
    private boolean gateShown = false;

    private Timer countdownTimer;
    private int countdown = Constants.HEARTBEAT_SECONDS;

    public QuantScapersPanel(QuantScapersPlugin plugin, ItemManager itemManager) {
        super(false);
        this.plugin = plugin;
        this.itemManager = itemManager;

        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        headerBar = new HeaderBar(this::onManualRefresh);
        filterBar = new FilterBar(plugin);
        topPicksBox = new TopPicksBox(plugin);
        topPicksBox.addPropertyChangeListener("auditComplete", e -> rerenderFromCache());

        vaultBox = new VaultBox(plugin, itemManager);
        vaultBox.addPropertyChangeListener("untrack", e -> plugin.untrack((Integer) e.getNewValue()));

        errorBanner = buildErrorBanner();
        errorBanner.setVisible(false);

        emailNotice = buildEmailNotice();
        refreshEmailNotice();

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        north.add(headerBar);
        north.add(emailNotice);
        north.add(filterBar);
        north.add(errorBanner);
        north.add(topPicksBox);
        north.add(vaultBox);
        // A vertical BoxLayout with MIXED child alignmentX values lines the children up
        // by their alignment points, not their edges - one default-CENTER sibling is
        // enough to shove every LEFT-aligned child's left edge to mid-panel. Normalize
        // here, in one place, so no individual section has to remember to opt in.
        for (java.awt.Component c : north.getComponents()) {
            if (c instanceof JComponent) {
                ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
            }
        }

        // Plain JPanel isn't Scrollable, so JViewport sizes it to its own preferred
        // width - meaning any unconstrained child anywhere in the card tree (a long
        // item name, a wide stats row, whatever comes next) can silently inflate the
        // whole list wider than the visible panel. With the horizontal scrollbar
        // disabled by design, that excess just gets clipped off the right edge
        // instead of showing a scrollbar, which read as a "bleeding" border. Forcing
        // getScrollableTracksViewportWidth() true pins the list - and therefore every
        // card - to the real visible width no matter what a child asks for.
        listContainer = new ScrollableListPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setOpaque(false);

        allLeadsHeader = buildAllLeadsHeader();

        statePanel = new PluginErrorPanel();
        statePanel.setContent("QUANTSCAPERS", "Syncing market data...");

        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        add(north, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        listContainer.add(statePanel);
    }

    @Override
    public void onActivate() {
        countdown = Constants.HEARTBEAT_SECONDS;
        headerBar.setCountdown(countdown);
        if (countdownTimer == null) {
            countdownTimer = new Timer(1000, e -> tickCountdown());
        }
        countdownTimer.start();
        refreshEmailNotice();
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

    /** Called on the EDT by the plugin after every successful analysis tick. */
    public void render(List<AnalyzedItem> display, List<AnalyzedItem> topPicks,
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
        lastAuditCache = auditCache;
        lastVault = vault;
        lastVaultLive = vaultLive;
        lastTotalMatched = totalMatched;
        filterBar.setSuppressedInfo(suppressedCount, showSuppressed);
        errorBanner.setVisible(!syncOk);
        refreshEmailNotice();
        rerenderFromCache();
    }

    public void showSyncError() {
        errorBanner.setVisible(true);
    }

    /**
     * Called on the EDT whenever a tick finds no contact email set. No wiki request has
     * been made for this tick — filters/top picks/errors are hidden since there's no data
     * behind them, and the list area explains why instead of showing a fake "syncing" state.
     * Guarded so it only rebuilds once per gated stretch instead of every 30s tick.
     */
    public void showEmailGate() {
        if (gateShown) {
            return;
        }
        gateShown = true;

        filterBar.setVisible(false);
        topPicksBox.setVisible(false);
        vaultBox.setVisible(false);
        errorBanner.setVisible(false);
        refreshEmailNotice(); // stays visible here - it's where the "Why?" popover lives

        listContainer.removeAll();
        liveCards.clear();
        statePanel.setContent("Valid contact email required", "No data until you enter a real email address above.");
        listContainer.add(statePanel);
        revalidate();
        repaint();
    }

    private void rerenderFromCache() {
        int scrollValue = 0;
        java.awt.Container parent = listContainer.getParent();
        if (parent instanceof javax.swing.JViewport) {
            scrollValue = ((javax.swing.JViewport) parent).getViewPosition().y;
        }

        filterBar.setLeadsCount(lastTotalMatched);
        topPicksBox.update(lastTopPicks, lastAuditCache);
        vaultBox.update(lastVault, lastVaultLive);

        Set<Integer> trackedIds = new HashSet<>();
        for (TrackedTrade t : lastVault) {
            trackedIds.add(t.getId());
        }

        listContainer.removeAll();
        liveCards.clear();

        if (lastDisplay.isEmpty()) {
            statePanel.setContent("No unicorns detected",
                "Loosen Budget, Profit, ROI or Fill Time to see leads.");
            listContainer.add(statePanel);
        } else {
            // A Top Pick also appearing as the first main-list card reads as a
            // duplication glitch without this - it's intentional (a highlight
            // strip of the same leads listed in full below), just needs a label.
            if (topPicksBox.isVisible()) {
                listContainer.add(allLeadsHeader);
                listContainer.add(Box.createVerticalStrut(4));
            }
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
                    rerenderFromCache();
                });
                // The plugin's own renderCurrentState() after the mutation delivers the
                // authoritative refresh (fresh vault snapshot) - rerendering locally here
                // would render against a stale lastVault.
                card.addPropertyChangeListener("trackToggled", e -> plugin.toggleTrack(item));
                liveCards.add(card);
                listContainer.add(card);
                listContainer.add(Box.createVerticalStrut(6));
            }
        }

        revalidate();
        repaint();

        int finalScroll = scrollValue;
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (listContainer.getParent() instanceof javax.swing.JViewport) {
                javax.swing.JViewport vp = (javax.swing.JViewport) listContainer.getParent();
                vp.setViewPosition(new java.awt.Point(0, Math.min(finalScroll,
                    Math.max(0, listContainer.getHeight() - vp.getHeight()))));
            }
        });
    }

    private void refreshEmailNotice() {
        String email = plugin.getConfig().contactEmail();
        emailNotice.setVisible(email == null || email.trim().isEmpty());
    }

    private JPanel buildEmailNotice() {
        JPanel banner = new JPanel(new BorderLayout(4, 0));
        banner.setBackground(new java.awt.Color(0xf5, 0x9e, 0x0b, 25));
        banner.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JLabel label = new JLabel(
            "<html><body style='width:130px'>Set a contact email in plugin settings.</body></html>");
        label.setForeground(QSColors.AMBER_400);
        label.setFont(FontManager.getRunescapeSmallFont());

        JLabel why = new JLabel("<html><u>Why?</u></html>");
        why.setForeground(QSColors.AMBER_400);
        why.setFont(FontManager.getRunescapeSmallFont());
        why.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        why.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showWhyPopover(why);
            }
        });

        banner.add(label, BorderLayout.CENTER);
        banner.add(why, BorderLayout.EAST);
        return banner;
    }

    /** Popover with the full explanation, kept out of the compact banner so it doesn't crowd the sidebar. */
    private void showWhyPopover(JComponent anchor) {
        JLabel content = new JLabel(
            "<html><body style='width:190px'>Saved locally and sent only to the Wiki Prices "
                + "API, as part of the User-Agent on every price request &mdash; nowhere else, "
                + "no telemetry, nothing collected by this plugin."
                + "<br><br>"
                + "It's what keeps QuantScapers compliant with the wiki's usage rules, since "
                + "every install should be individually identifiable rather than anonymous "
                + "instead of everyone sharing one address."
                + "</body></html>");
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
        JLabel title = new JLabel("ALL LEADS");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(QSColors.AMBER_400);
        JLabel subtitle = new JLabel("everything passing your filters");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(QSColors.SLATE_500);

        JPanel headerText = new JPanel();
        headerText.setLayout(new BoxLayout(headerText, BoxLayout.Y_AXIS));
        headerText.setOpaque(false);
        headerText.add(title);
        headerText.add(subtitle);

        JPanel accent = new JPanel();
        accent.setBackground(QSColors.AMBER_500);
        accent.setPreferredSize(new java.awt.Dimension(3, 1));

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
        javax.swing.JLabel label = new javax.swing.JLabel("Market sync failed - retrying next cycle.");
        label.setForeground(com.quantscapers.QSColors.RED_400);
        label.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
        banner.add(label, BorderLayout.CENTER);
        return banner;
    }

    // Pins the list to the viewport's actual width (see the comment where this is
    // constructed) so no child row can silently push the card wider than what's visible.
    private static final class ScrollableListPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
