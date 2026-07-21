package com.quantscapers.ui;

import com.quantscapers.QSColors;
import com.quantscapers.engine.AnalyzedItem;
import com.quantscapers.ui.util.GpFormat;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.FontManager;

/**
 * A local 24-hour approximation of the website's movers boards. It deliberately
 * uses only the already loaded wiki snapshot; no history endpoint is requested.
 */
public class MarketMoversBox extends JPanel {
    private static final long MIN_DAILY_TRADES = 1_000L;

    public MarketMoversBox() {
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

        List<AnalyzedItem> liquid = market.stream()
            .filter(item -> item.getVol24h() >= MIN_DAILY_TRADES)
            .collect(Collectors.toList());
        if (liquid.isEmpty()) {
            setVisible(false);
            return;
        }

        AnalyzedItem gainer = liquid.stream()
            .filter(item -> highMove(item) > 0)
            .max(Comparator.comparingDouble(this::highMove)).orElse(null);
        AnalyzedItem loser = liquid.stream()
            .filter(item -> lowMove(item) < 0)
            .min(Comparator.comparingDouble(this::lowMove)).orElse(null);
        AnalyzedItem active = liquid.stream()
            .max(Comparator.comparingLong(AnalyzedItem::getVol24h)).orElse(null);
        if (gainer == null && loser == null && active == null) {
            setVisible(false);
            return;
        }

        JLabel title = new JLabel("24H MARKET MOVERS");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(QSColors.AMBER_400);
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);

        JLabel subtitle = new JLabel("liquid items · same market snapshot");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(QSColors.SLATE_500);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        add(subtitle);
        add(Box.createVerticalStrut(4));

        if (gainer != null) add(row("TOP GAINER", gainer, highMove(gainer), gainer.getAvg24hHigh(), QSColors.EMERALD_400));
        if (loser != null) add(row("TOP LOSER", loser, lowMove(loser), loser.getAvg24hLow(), QSColors.RED_400));
        if (active != null) add(volumeRow(active));
        add(Box.createVerticalStrut(6));
        setVisible(true);
    }

    private JPanel row(String labelText, AnalyzedItem item, double change, double baseline, Color tone) {
        String amount = (change >= 0 ? "+" : "") + GpFormat.format(Math.round(change));
        double pct = baseline > 0 ? (change / baseline) * 100.0 : 0;
        return row(labelText, item.getName(), amount + String.format(java.util.Locale.ROOT, " (%.1f%%)", pct), tone);
    }

    private JPanel volumeRow(AnalyzedItem item) {
        return row("MOST TRADED", item.getName(), compact(item.getVol24h()) + " daily trades", QSColors.AMBER_400);
    }

    private JPanel row(String labelText, String nameText, String valueText, Color tone) {
        JPanel row = new StretchPanel(new BorderLayout(6, 0));
        row.setBackground(QSColors.BG_DEEP);
        row.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(QSColors.BORDER, 1, true),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        row.setAlignmentX(LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(QSColors.SLATE_500);
        JLabel name = new JLabel("<html><body style='width:" + (UiConstants.CARD_TEXT_WIDTH_PX - 112)
            + "px'>" + escape(nameText) + "</body></html>");
        name.setFont(FontManager.getRunescapeSmallFont());
        name.setForeground(QSColors.SLATE_200);
        JLabel value = new JLabel(valueText);
        value.setFont(FontManager.getRunescapeSmallFont());
        value.setForeground(tone);

        JPanel details = new JPanel(new BorderLayout(4, 0));
        details.setOpaque(false);
        details.add(name, BorderLayout.CENTER);
        details.add(value, BorderLayout.EAST);
        row.add(label, BorderLayout.WEST);
        row.add(details, BorderLayout.CENTER);
        return row;
    }

    private double highMove(AnalyzedItem item) {
        return item.getHigh() - item.getAvg24hHigh();
    }

    private double lowMove(AnalyzedItem item) {
        return item.getLow() - item.getAvg24hLow();
    }

    private static String compact(long value) {
        if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0);
        return Long.toString(value);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class StretchPanel extends JPanel {
        StretchPanel(java.awt.LayoutManager layout) { super(layout); }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }
}
