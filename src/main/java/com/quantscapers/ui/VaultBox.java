package com.quantscapers.ui;

import com.quantscapers.QSColors;
import com.quantscapers.QuantScapersPlugin;
import com.quantscapers.api.PriceQuote;
import com.quantscapers.engine.TrackedTrade;
import com.quantscapers.ui.util.GpFormat;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/**
 * Collapsible list of user-tracked ("vaulted") items, each showing the
 * snapshot price from when it was tracked next to the current live price.
 * Deliberately styled differently from ItemCardBox (violet accent, no
 * verdict-colored rectangle border) so it doesn't read as just another
 * AVOID/RISKY card in the list below it. Hidden entirely when empty.
 */
public class VaultBox extends JPanel {

    private final QuantScapersPlugin plugin;
    private final ItemManager itemManager;
    private boolean collapsed;
    private List<TrackedTrade> lastVault = java.util.Collections.emptyList();
    private Map<Integer, PriceQuote> lastLive = java.util.Collections.emptyMap();

    public VaultBox(QuantScapersPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.collapsed = plugin.getConfig().vaultCollapsed();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        setVisible(false);
    }

    // Recomputed every layout pass rather than a single setMaximumSize(...) call - this
    // is the one width-stretch technique already proven reliable in this codebase
    // (ItemCardBox uses the same override for the same reason).
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    public void update(List<TrackedTrade> vault, Map<Integer, PriceQuote> live) {
        lastVault = vault;
        lastLive = live;
        removeAll();
        if (vault.isEmpty()) {
            setVisible(false);
            return;
        }

        add(header(vault.size()));

        if (!collapsed) {
            add(Box.createVerticalStrut(6));
            for (TrackedTrade t : vault) {
                add(row(t, live.get(t.getId())));
                add(Box.createVerticalStrut(6));
            }
        }
        setVisible(true);
        revalidate();
        repaint();
    }

    private JPanel header(int count) {
        JLabel title = new JLabel("VAULT (" + count + ")");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(QSColors.VIOLET_400);

        JLabel chevron = new JLabel(collapsed ? "▸" : "▾");
        chevron.setFont(FontManager.getRunescapeSmallFont());
        chevron.setForeground(QSColors.SLATE_500);

        JPanel titleRow = new JPanel(new BorderLayout(4, 0));
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.CENTER);
        titleRow.add(chevron, BorderLayout.EAST);

        JPanel accent = new JPanel();
        accent.setBackground(QSColors.VIOLET_500);
        accent.setPreferredSize(new Dimension(3, 1));

        JPanel bar = new StretchPanel(new BorderLayout(6, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        bar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bar.add(accent, BorderLayout.WEST);
        bar.add(titleRow, BorderLayout.CENTER);
        bar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                collapsed = !collapsed;
                plugin.getConfig().setVaultCollapsed(collapsed);
                update(lastVault, lastLive);
            }
        });
        return bar;
    }

    private JPanel row(TrackedTrade t, PriceQuote live) {
        // Full-width, violet left-accent style deliberately unlike ItemCardBox's
        // verdict-colored rectangle border, so a vaulted entry doesn't read as
        // just another AVOID/RISKY card in the list below it. The accent is a
        // matte border directly on this panel (not a separate nested JPanel) so
        // there's no second layout manager whose width-stretch behavior could
        // disagree with this one and leave a gap.
        JPanel content = new StretchPanel(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(QSColors.BG_DEEP);
        content.setAlignmentX(LEFT_ALIGNMENT);
        content.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, QSColors.VIOLET_500),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(36, 32));
        icon.setHorizontalAlignment(JLabel.CENTER);
        icon.setVerticalAlignment(JLabel.CENTER);
        try {
            itemManager.getImage(t.getId()).addTo(icon);
        } catch (Exception ignored) {
            // icon best-effort
        }

        JLabel name = new JLabel("<html><body style='width:" + (UiConstants.CARD_TEXT_WIDTH_PX - 30)
            + "px'>" + escape(t.getName()) + "</body></html>");
        name.setFont(FontManager.getRunescapeBoldFont());
        name.setForeground(QSColors.SLATE_200);

        JButton untrack = flatButton("✕");
        untrack.setForeground(QSColors.RED_400);
        untrack.setToolTipText("Remove from vault");
        untrack.addActionListener(e -> firePropertyChange("untrack", -1, t.getId()));

        JPanel titleRow = new JPanel(new BorderLayout(6, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        titleRow.add(icon, BorderLayout.WEST);
        titleRow.add(name, BorderLayout.CENTER);
        titleRow.add(untrack, BorderLayout.EAST);
        content.add(titleRow);
        content.add(Box.createVerticalStrut(6));

        int marginThen = t.getSnapSell() - t.getSnapBuy() - t.getSnapTax();

        content.add(priceBlock("THEN", t.getSnapBuy(), t.getSnapSell()));
        content.add(Box.createVerticalStrut(4));
        if (live != null && live.getHigh() != null && live.getLow() != null) {
            content.add(priceBlock("NOW", live.getLow(), live.getHigh()));

            int marginNow = live.getHigh() - live.getLow(); // tax on current sell not modeled - directional delta only
            long delta = marginNow - marginThen;
            content.add(Box.createVerticalStrut(6));
            content.add(fullWidthLabel(
                (delta >= 0 ? "▲ +" : "▼ ") + GpFormat.format(delta) + " margin since tracked",
                delta >= 0 ? QSColors.EMERALD_400 : QSColors.RED_400, true));
        } else {
            content.add(fullWidthLabel("NOW · no live data for this item", QSColors.SLATE_500, false));
        }

        content.add(Box.createVerticalStrut(6));
        content.add(fullWidthLabel("tracked " + agoText(t.getTrackedAtMs()), QSColors.SLATE_500, false));

        return content;
    }

    /**
     * A labeled block with Buy and Sell each on their own full-width line - not
     * squeezed into a half column, same reasoning as ItemCardBox's stacked price
     * wells: a bold 9-10 digit price (common on expensive gear) needs the room.
     */
    private JPanel priceBlock(String label, int buy, int sell) {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setOpaque(false);
        block.setAlignmentX(LEFT_ALIGNMENT);

        JLabel tag = new JLabel(label);
        tag.setFont(FontManager.getRunescapeSmallFont());
        tag.setForeground(QSColors.SLATE_500);
        tag.setAlignmentX(LEFT_ALIGNMENT);
        block.add(tag);
        block.add(fullWidthLabel("Buy " + GpFormat.withCommas(buy), QSColors.EMERALD_400, true));
        block.add(fullWidthLabel("Sell " + GpFormat.withCommas(sell), QSColors.RED_400, true));
        return block;
    }

    private static String agoText(long trackedAtMs) {
        long minutes = Math.max(0, (System.currentTimeMillis() - trackedAtMs) / 60_000);
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        return (hours / 24) + "d ago";
    }

    private static JLabel fullWidthLabel(String text, Color color, boolean bold) {
        JLabel l = new JLabel(text);
        l.setFont(bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
        l.setForeground(color);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JButton flatButton(String text) {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // Same technique ItemCardBox uses to reliably stretch to the panel's full width
    // under BoxLayout - a static setMaximumSize(...) call left a persistent left gap
    // in this box, this dynamic override did not.
    private static class StretchPanel extends JPanel {
        StretchPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }
}
