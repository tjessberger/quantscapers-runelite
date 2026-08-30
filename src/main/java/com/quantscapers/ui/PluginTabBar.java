package com.quantscapers.ui;

import com.quantscapers.QSColors;
import java.awt.Cursor;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.FontManager;

/** Compact internal navigation for the plugin's three working areas. */
public class PluginTabBar extends JPanel {
    public enum Tab { OVERVIEW, SCANNER, WATCHLIST }

    private final JButton overview = button("OVERVIEW", "Market Intelligence and Top Picks");
    private final JButton scanner = button("SCANNER", "Search and scan market opportunities");
    private final JButton watchlist = button("WATCHLIST", "Tracked items and price changes");
    private Tab selected = Tab.OVERVIEW;

    public PluginTabBar(java.util.function.Consumer<Tab> onSelect) {
        setLayout(new GridLayout(1, 3, 3, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        overview.addActionListener(e -> onSelect.accept(Tab.OVERVIEW));
        scanner.addActionListener(e -> onSelect.accept(Tab.SCANNER));
        watchlist.addActionListener(e -> onSelect.accept(Tab.WATCHLIST));

        add(overview);
        add(scanner);
        add(watchlist);
        setSelected(Tab.OVERVIEW);
    }

    public void setSelected(Tab tab) {
        selected = tab;
        style(overview, tab == Tab.OVERVIEW);
        style(scanner, tab == Tab.SCANNER);
        style(watchlist, tab == Tab.WATCHLIST);
    }

    public Tab getSelected() {
        return selected;
    }

    public void setWatchlistCount(int count) {
        watchlist.setText(count > 0 ? "WATCHLIST " + count : "WATCHLIST");
    }

    private static JButton button(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setToolTipText(tooltip);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        button.setBorder(new LineBorder(QSColors.BORDER, 1, true));
        return button;
    }

    private static void style(JButton button, boolean active) {
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(active ? QSColors.chipBackground(QSColors.AMBER_500) : QSColors.BG_DEEP);
        button.setForeground(active ? QSColors.AMBER_400 : QSColors.SLATE_400);
        button.setBorder(new LineBorder(active ? QSColors.BORDER_AMBER : QSColors.BORDER, 1, true));
    }
}
