package com.quantscapers.ui;

import com.quantscapers.QSColors;
import com.quantscapers.QuantScapersPlugin;
import com.quantscapers.engine.AnalyzedItem;
import com.quantscapers.engine.AuditResult;
import com.quantscapers.engine.Signal;
import com.quantscapers.engine.Ticket;
import com.quantscapers.engine.TicketBuilder;
import com.quantscapers.engine.Verdict;
import com.quantscapers.engine.VerdictEngine;
import com.quantscapers.ui.util.AgeLabel;
import com.quantscapers.ui.util.GpFormat;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.LineBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * One opportunity card: collapsed shows a one-line verdict summary,
 * expanded shows the full ticket, price wells, meta line, and audit
 * section. Layout/anatomy per RUNELITE_PLUGIN_SPEC.md §7.5.
 */
public class ItemCardBox extends JPanel {

    private final QuantScapersPlugin plugin;
    private final AnalyzedItem item;
    private final Verdict verdict;
    private final Signal signal;
    private final Ticket ticket;
    private final AuditResult hist;

    private final List<AgeLabel> ageLabels = new ArrayList<>();
    private JPanel detailPanel;
    private boolean expanded;
    private JButton auditButton;
    private boolean auditInFlight = false;
    private JLabel chevron;
    private final Color cardBorderColor;
    private final boolean tracked;

    public ItemCardBox(QuantScapersPlugin plugin, ItemManager itemManager, AnalyzedItem item,
                        AuditResult hist, boolean expandedInitially, boolean auditingInitially,
                        boolean tracked, Consumer<Boolean> onExpandToggle) {
        this.plugin = plugin;
        this.item = item;
        this.hist = hist;
        this.verdict = VerdictEngine.verdict(item, hist);
        this.signal = VerdictEngine.signal(item);
        this.ticket = verdict.getRating() != Verdict.Rating.AVOID ? TicketBuilder.build(item) : null;
        this.expanded = expandedInitially;
        this.auditInFlight = auditingInitially;
        this.tracked = tracked;

        this.cardBorderColor = verdict.getBorder();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(QSColors.BG);
        // Plain LineBorder produced a misaligned overhang on this panel (Swing insets
        // vs. RuneLite's UI scaling didn't agree on pixel bounds) - painting the
        // rectangle by hand against getWidth()/getHeight() guarantees it hugs the
        // component's actual bounds exactly.
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        setAlignmentX(LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel header = buildHeader(itemManager);
        MouseAdapter toggleListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ItemCardBox.this.expanded = !ItemCardBox.this.expanded;
                detailPanel.setVisible(ItemCardBox.this.expanded);
                chevron.setText(ItemCardBox.this.expanded ? "▾" : "▸");
                onExpandToggle.accept(ItemCardBox.this.expanded);
                revalidate();
                repaint();
                if (getParent() != null) {
                    getParent().revalidate();
                    getParent().repaint();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                header.setBackground(QSColors.BG_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                header.setBackground(QSColors.BG);
            }
        };
        // Swing dispatches clicks to the deepest component under the cursor - the icon,
        // name, chip, and stat labels cover nearly all of the header's visible area, so a
        // listener on header alone only catches clicks on its few pixels of bare padding.
        attachRecursively(header, toggleListener);
        add(header);

        detailPanel = buildDetail();
        detailPanel.setVisible(expanded);
        add(detailPanel);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(cardBorderColor);
        g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        g2.dispose();
    }

    public void refreshAges(long nowMs) {
        for (AgeLabel label : ageLabels) {
            label.refresh(nowMs);
        }
    }

    /**
     * Recomputed on every layout pass instead of frozen at construction time,
     * so BoxLayout lets the card grow when the detail panel toggles visible
     * (a fixed max height locked in during the collapsed state would clip
     * the expansion down to a sliver).
     */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private static void attachRecursively(java.awt.Component c, MouseAdapter listener) {
        c.addMouseListener(listener);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (c instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) c).getComponents()) {
                attachRecursively(child, listener);
            }
        }
    }

    private JPanel buildHeader(ItemManager itemManager) {
        JPanel header = new JPanel(new BorderLayout(6, 2));
        header.setOpaque(true);
        header.setBackground(QSColors.BG);
        header.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        header.setAlignmentX(LEFT_ALIGNMENT);

        JLabel icon = new JLabel();
        // OSRS item sprites render on a 36x32 canvas and aren't all square - a
        // smaller fixed box (previously 20x20) clipped anything wider or taller
        // than that, which is most two-handed weapons and many other items.
        icon.setPreferredSize(new Dimension(36, 32));
        icon.setHorizontalAlignment(JLabel.CENTER);
        icon.setVerticalAlignment(JLabel.CENTER);
        try {
            itemManager.getImage(item.getId()).addTo(icon);
        } catch (Exception ignored) {
            // icon best-effort; card still works without it
        }

        JLabel name = new JLabel(item.getName());
        name.setFont(FontManager.getRunescapeBoldFont());
        name.setForeground(QSColors.SLATE_200);
        // A long item name (e.g. "Contract of familiar acquisition") otherwise inflates
        // this BorderLayout row's preferred width past the panel's actual width, which
        // - since horizontal scrolling is disabled - pushed the whole card wider than
        // the viewport and clipped the right border off-screen. Zeroing the preferred
        // width here stops it from driving layout; BorderLayout still gives it whatever
        // space is actually left at paint time and clips the text there instead.
        name.setPreferredSize(new Dimension(0, name.getPreferredSize().height));

        // Nothing else signals a card is clickable at all - this is the affordance.
        chevron = new JLabel(expanded ? "▾" : "▸");
        chevron.setFont(FontManager.getRunescapeSmallFont());
        chevron.setForeground(QSColors.SLATE_500);

        JPanel titleRow = new JPanel(new BorderLayout(4, 0));
        titleRow.setOpaque(false);
        titleRow.add(icon, BorderLayout.WEST);
        titleRow.add(name, BorderLayout.CENTER);
        titleRow.add(chevron, BorderLayout.EAST);

        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statsRow.setOpaque(false);
        statsRow.add(verdictChip());
        if (plugin.getConfig().viewMode() == com.quantscapers.QuantScapersConfig.ViewMode.ALCH) {
            statsRow.add(smallLabel("+" + GpFormat.format(item.getAlchProfit()), QSColors.AMBER_400));
            statsRow.add(smallLabel(GpFormat.format(Math.round(item.getAlchGpHour())) + "/hr", QSColors.SLATE_200));
            statsRow.add(smallLabel(String.format("%.1f%%", item.getAlchROI()), QSColors.SLATE_400));
        } else {
            statsRow.add(smallLabel("+" + GpFormat.format(item.getRealisticProfit()), QSColors.AMBER_400));
            statsRow.add(smallLabel(GpFormat.format(Math.round(item.getGpHour())) + "/hr", QSColors.SLATE_200));
            statsRow.add(smallLabel(String.format("%.1f%%", item.getRoi()), QSColors.SLATE_400));
        }

        header.add(titleRow, BorderLayout.NORTH);
        header.add(statsRow, BorderLayout.SOUTH);
        return header;
    }

    private JLabel verdictChip() {
        JLabel chip = new JLabel(verdict.getRating().name());
        chip.setOpaque(true);
        chip.setBackground(QSColors.chipBackground(verdict.getColor()));
        chip.setForeground(verdict.getColor());
        chip.setFont(FontManager.getRunescapeBoldFont());
        chip.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        return chip;
    }

    private static JLabel smallLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(color);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JPanel buildDetail() {
        JPanel detail = new JPanel();
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setBackground(QSColors.BG_DEEP);
        detail.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        detail.setAlignmentX(LEFT_ALIGNMENT);

        detail.add(wrappedLabel(verdict.getReason(), QSColors.SLATE_400));
        detail.add(Box.createVerticalStrut(4));
        detail.add(smallLabel(signal.getLabel(), signal.getColor()));
        detail.add(Box.createVerticalStrut(6));

        if (plugin.getConfig().viewMode() == com.quantscapers.QuantScapersConfig.ViewMode.ALCH) {
            if (item.getAlchProfit() > 0) {
                detail.add(alchTicketBlock());
                detail.add(Box.createVerticalStrut(6));
            }
        } else {
            if (ticket != null) {
                detail.add(ticketBlock());
                detail.add(Box.createVerticalStrut(6));
            }
        }

        detail.add(priceRows());
        detail.add(Box.createVerticalStrut(6));
        detail.add(metaLine());
        detail.add(Box.createVerticalStrut(6));
        detail.add(auditSection());
        detail.add(Box.createVerticalStrut(4));
        detail.add(wikiLink());

        return detail;
    }

    private JPanel ticketBlock() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(QSColors.BG);
        box.setBorder(new LineBorder(QSColors.BORDER_AMBER, 1, true));
        box.setAlignmentX(LEFT_ALIGNMENT);

        box.add(ticketRow("BUY", ticket.getQty(), ticket.getBuyAt(), QSColors.EMERALD_400));
        box.add(ticketRow("SELL", ticket.getQty(), ticket.getSellAt(), QSColors.RED_400));

        JLabel profit = wrappedLabel("+" + GpFormat.format(ticket.getProfit()) + " after tax",
            QSColors.AMBER_400, UiConstants.CARD_TEXT_WIDTH_PX);
        profit.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
        box.add(profit);

        JLabel wait = wrappedLabel("No fill after ~" + ticket.getWaitMin() + " min? Cancel and check again.",
            QSColors.SLATE_500, UiConstants.CARD_TEXT_WIDTH_PX);
        wait.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 6));
        box.add(wait);

        return box;
    }

    private JPanel alchTicketBlock() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(QSColors.BG);
        box.setBorder(new LineBorder(QSColors.BORDER_AMBER, 1, true));
        box.setAlignmentX(LEFT_ALIGNMENT);

        box.add(ticketRow("BUY", item.getLimit(), item.getLow(), QSColors.EMERALD_400));
        box.add(ticketRow("ALCH", item.getLimit(), item.getHighalch(), QSColors.RED_400));

        JLabel profit = wrappedLabel("+" + GpFormat.format(item.getAlchProfit()) + " profit",
            QSColors.AMBER_400, UiConstants.CARD_TEXT_WIDTH_PX);
        profit.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
        box.add(profit);

        long natRune = item.getHighalch() - item.getLow() - item.getAlchMarginPer();
        JLabel nature = wrappedLabel("Nature rune cost included (" + GpFormat.withCommas(natRune) + " gp)",
            QSColors.SLATE_500, UiConstants.CARD_TEXT_WIDTH_PX);
        nature.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 6));
        box.add(nature);

        return box;
    }

    // Room reserved for the copy button + gap, subtracted from the shared card width
    // so a long "SELL 8 @ 10,210,938" line wraps instead of pushing the button out.
    private static final int TICKET_ROW_TEXT_WIDTH_PX = UiConstants.CARD_TEXT_WIDTH_PX - 32;

    private JPanel ticketRow(String label, long qty, long price, Color labelColor) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 4));

        JLabel text = wrappedLabel(label + " " + qty + " @ " + GpFormat.withCommas(price),
            labelColor, TICKET_ROW_TEXT_WIDTH_PX);
        JButton copy = flatButton("⧉");
        copy.setToolTipText("Copy price");
        copy.addActionListener(e -> copyToClipboard(copy, String.valueOf(price)));

        row.add(text, BorderLayout.CENTER);
        row.add(copy, BorderLayout.EAST);
        return row;
    }

    private static void copyToClipboard(JButton button, String value) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
        String original = button.getText();
        button.setText("✓");
        Timer t = new Timer(1500, e -> button.setText(original));
        t.setRepeats(false);
        t.start();
    }

    // Half the shared card text budget, minus the grid gap - the price label below
    // wraps onto a second line at this width instead of overflowing on a bold
    // 9-10 digit price, which is what side-by-side columns used to do before the
    // list container was pinned to the real viewport width.
    private static final int PRICE_WELL_WIDTH_PX = (UiConstants.CARD_TEXT_WIDTH_PX - 6) / 2;

    private JPanel priceRows() {
        JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);

        if (plugin.getConfig().viewMode() == com.quantscapers.QuantScapersConfig.ViewMode.ALCH) {
            row.add(priceWell("Buy", item.getLow(), QSColors.EMERALD_400, item.getLowTime(), item.getAvg5mLow()));
            row.add(alchPriceWell("Alch Value", item.getHighalch(), QSColors.RED_400));
        } else {
            row.add(priceWell("Buy", item.getLow(), QSColors.EMERALD_400, item.getLowTime(), item.getAvg5mLow()));
            row.add(priceWell("Sell", item.getHigh(), QSColors.RED_400, item.getHighTime(), item.getAvg5mHigh()));
        }
        return row;
    }

    private JPanel alchPriceWell(String label, long price, Color color) {
        JPanel well = new JPanel();
        well.setLayout(new BoxLayout(well, BoxLayout.Y_AXIS));
        well.setBackground(QSColors.BG);
        well.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        well.setAlignmentX(LEFT_ALIGNMENT);

        well.add(smallLabel(label, QSColors.SLATE_400));
        JLabel priceLabel = new JLabel("<html><body style='width:" + PRICE_WELL_WIDTH_PX + "px'>"
            + escape(GpFormat.withCommas(price)) + "</body></html>");
        priceLabel.setFont(FontManager.getRunescapeBoldFont());
        priceLabel.setForeground(color);
        priceLabel.setAlignmentX(LEFT_ALIGNMENT);
        well.add(priceLabel);

        long natRune = item.getHighalch() - item.getLow() - item.getAlchMarginPer();
        well.add(smallLabel("nat rune −" + GpFormat.withCommas(natRune), QSColors.SLATE_500));
        return well;
    }

    private JPanel priceWell(String label, long price, Color color, long timeSec, double avg5m) {
        JPanel well = new JPanel();
        well.setLayout(new BoxLayout(well, BoxLayout.Y_AXIS));
        well.setBackground(QSColors.BG);
        well.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        well.setAlignmentX(LEFT_ALIGNMENT);

        well.add(smallLabel(label, QSColors.SLATE_400));
        JLabel priceLabel = new JLabel("<html><body style='width:" + PRICE_WELL_WIDTH_PX + "px'>"
            + escape(GpFormat.withCommas(price)) + "</body></html>");
        priceLabel.setFont(FontManager.getRunescapeBoldFont());
        priceLabel.setForeground(color);
        priceLabel.setAlignmentX(LEFT_ALIGNMENT);
        well.add(priceLabel);

        AgeLabel age = new AgeLabel(" ago");
        age.setUnixSec(timeSec);
        age.refresh(item.getAnalyzedAtMs());
        age.setAlignmentX(LEFT_ALIGNMENT);
        ageLabels.add(age);
        well.add(age);

        if (avg5m > 0) {
            well.add(smallLabel("5min average " + GpFormat.withCommas(Math.round(avg5m)), QSColors.SLATE_500));
        }
        if ("Sell".equals(label)) {
            well.add(smallLabel("tax −" + GpFormat.withCommas(item.getTax()), QSColors.SLATE_500));
        }
        return well;
    }

    private JLabel metaLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("Limit ").append(GpFormat.withCommas(item.getLimit()))
            .append(" · 24h volume ").append(GpFormat.format(item.getVol24h()));
        if (item.getVol5m() != null) {
            sb.append(" · 5min volume ").append(GpFormat.format(item.getVol5m()));
        }
        sb.append(" · Fill ").append(Math.round(item.getEft())).append(" min");
        Color color = (item.getVol5m() != null && item.getVol5m() > 0) ? QSColors.EMERALD_500 : QSColors.SLATE_500;
        return wrappedLabel(sb.toString(), color, UiConstants.CARD_TEXT_WIDTH_PX);
    }

    private JPanel auditSection() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.setAlignmentX(LEFT_ALIGNMENT);

        auditButton = flatTextButton(hist != null ? "Audit again" : "Run audit", QSColors.AMBER_400);
        if (auditInFlight) {
            auditButton.setEnabled(false);
            auditButton.setText("Auditing...");
        }
        auditButton.addActionListener(e -> runAudit());

        JButton trackButton = flatTextButton(tracked ? "★ Untrack" : "☆ Track", QSColors.AMBER_400);
        trackButton.addActionListener(e -> firePropertyChange("trackToggled", false, true));

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(LEFT_ALIGNMENT);
        buttonRow.add(auditButton);
        buttonRow.add(Box.createHorizontalStrut(6));
        buttonRow.add(trackButton);
        box.add(buttonRow);
        box.add(Box.createVerticalStrut(4));
        box.add(AuditSummary.build(hist));
        return box;
    }

    private void runAudit() {
        if (auditInFlight) {
            return;
        }
        auditInFlight = true;
        auditButton.setEnabled(false);
        auditButton.setText("Auditing...");
        // Panel tracks in-flight audits separately from this card instance so the
        // in-flight audit state survives the periodic re-render (a 30s poll tick
        // rebuilds every card from scratch and would otherwise silently drop it).
        firePropertyChange("auditStarted", false, true);
        plugin.requestAudit(item, () -> {
            auditInFlight = false;
            // Panel owns the authoritative re-render on completion (fresh AuditResult
            // needs to reach this card, which requires a rebuild from the plugin's cache).
            firePropertyChange("auditComplete", false, true);
        });
    }

    private JLabel wikiLink() {
        JLabel link = new JLabel("prices.wiki ↗");
        link.setFont(FontManager.getRunescapeSmallFont());
        link.setForeground(QSColors.SLATE_500);
        link.setAlignmentX(LEFT_ALIGNMENT);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                LinkBrowser.browse("https://prices.runescape.wiki/osrs/item/" + item.getId());
            }
        });
        return link;
    }

    private JLabel wrappedLabel(String text, Color color) {
        return wrappedLabel(text, color, UiConstants.CARD_TEXT_WIDTH_PX);
    }

    private JLabel wrappedLabel(String text, Color color, int widthPx) {
        JLabel label = new JLabel("<html><body style='width:" + widthPx + "px'>" + escape(text) + "</body></html>");
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static JButton flatButton(String text) {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setForeground(QSColors.SLATE_400);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setMargin(new java.awt.Insets(0, 4, 0, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JButton flatTextButton(String text, Color color) {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeBoldFont());
        b.setForeground(color);
        b.setBorderPainted(true);
        b.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(QSColors.BORDER_AMBER, 1, true),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        return b;
    }
}
