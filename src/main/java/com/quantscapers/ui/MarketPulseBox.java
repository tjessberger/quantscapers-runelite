package com.quantscapers.ui;

import com.quantscapers.QSColors;
import com.quantscapers.engine.AnalyzedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.FontManager;

/** A compact, local-only reading of the already loaded market snapshot. */
public class MarketPulseBox extends JPanel {
    public MarketPulseBox() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        setVisible(false);
    }

    public void update(List<AnalyzedItem> market) {
        removeAll();
        if (market == null || market.isEmpty()) {
            setVisible(false);
            return;
        }

        AnalyzedItem mover = market.stream()
            .filter(item -> item.getAvg24hHigh() > 0)
            .max(Comparator.comparingDouble(this::absoluteMove)).orElse(null);
        AnalyzedItem active = market.stream()
            .max(Comparator.comparingLong(AnalyzedItem::getVol24h)).orElse(null);
        if (mover == null || active == null) {
            setVisible(false);
            return;
        }

        JLabel title = new JLabel("MARKET PULSE");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(QSColors.VIOLET_400);
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(3));

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(QSColors.BG_DEEP);
        box.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(QSColors.BORDER, 1, true),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        box.setAlignmentX(LEFT_ALIGNMENT);
        double move = movePercent(mover);
        box.add(line("24H MOVE", mover.getName() + " " + (move >= 0 ? "+" : "")
            + String.format(java.util.Locale.ROOT, "%.1f%%", move), move >= 0 ? QSColors.EMERALD_400 : QSColors.RED_400));
        box.add(line("MOST ACTIVE", active.getName() + " " + compact(active.getVol24h()) + " daily trades", QSColors.AMBER_400));
        add(box);
        add(Box.createVerticalStrut(6));
        setVisible(true);
    }

    private JPanel line(String labelText, String valueText, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(QSColors.SLATE_500);
        JLabel value = new JLabel("<html><body style='width:" + (UiConstants.CARD_TEXT_WIDTH_PX - 55) + "px'>"
            + escape(valueText) + "</body></html>");
        value.setFont(FontManager.getRunescapeSmallFont());
        value.setForeground(valueColor);
        row.add(label, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    private double movePercent(AnalyzedItem item) {
        return ((item.getHigh() - item.getAvg24hHigh()) / item.getAvg24hHigh()) * 100.0;
    }

    private double absoluteMove(AnalyzedItem item) {
        return Math.abs(movePercent(item));
    }

    private static String compact(long value) {
        if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0);
        return Long.toString(value);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
