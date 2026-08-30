package com.quantscapers.ui;

import com.quantscapers.QSColors;
import com.quantscapers.engine.AnalyzedItem;
import com.quantscapers.engine.MarketIntelligence;
import com.quantscapers.engine.MarketIntelligence.Briefing;
import com.quantscapers.engine.MarketIntelligence.SignalRow;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.FontManager;

/**
 * Snapshot-only market briefing. It gives the Overview a market-intelligence
 * job of its own instead of duplicating the Scanner's highest-profit cards.
 */
public class MarketPulseBox extends JPanel {
    private final Consumer<AnalyzedItem> onItemSelected;
    private final JComboBox<VolumeOption> volumeFilter = new JComboBox<>(VolumeOption.values());
    private final JComboBox<PriceOption> priceFilter = new JComboBox<>(PriceOption.values());
    private final JComboBox<MoveOption> moveFilter = new JComboBox<>(MoveOption.values());
    private final JPanel filterBar = new JPanel(new GridLayout(1, 3, 3, 0));
    private List<AnalyzedItem> lastMarket = Collections.emptyList();

    public MarketPulseBox() {
        this(item -> { });
    }

    public MarketPulseBox(Consumer<AnalyzedItem> onItemSelected) {
        this.onItemSelected = onItemSelected;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        setVisible(false);
        configureFilter(volumeFilter, v -> v.label, "Minimum estimated daily trades");
        configureFilter(priceFilter, v -> v.label, "Minimum current item value");
        configureFilter(moveFilter, v -> v.label, "Minimum 24h movement for directional signals");
        ActionListener refresh = e -> update(lastMarket);
        volumeFilter.addActionListener(refresh);
        priceFilter.addActionListener(refresh);
        moveFilter.addActionListener(refresh);
        filterBar.setOpaque(false);
        filterBar.setAlignmentX(LEFT_ALIGNMENT);
        filterBar.add(filterField("Trades/day", volumeFilter));
        filterBar.add(filterField("Item value", priceFilter));
        filterBar.add(filterField("24h change", moveFilter));
    }

    public void update(List<AnalyzedItem> market) {
        lastMarket = market == null ? Collections.emptyList() : market;
        removeAll();
        Briefing briefing = MarketIntelligence.summarize(lastMarket, selectedParameters());
        if (lastMarket.isEmpty()) {
            setVisible(false);
            return;
        }

        JLabel title = new JLabel("MARKET INTELLIGENCE");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(QSColors.VIOLET_400);
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(2));
        add(filterBar);
        add(Box.createVerticalStrut(4));

        if (briefing.getLiquidItems() == 0) {
            JLabel empty = new JLabel("No items match these thresholds.");
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setForeground(QSColors.SLATE_400);
            empty.setAlignmentX(LEFT_ALIGNMENT);
            add(empty);
            add(Box.createVerticalStrut(6));
            setVisible(true);
            revalidate();
            repaint();
            return;
        }

        JLabel summary = new JLabel(briefing.getSummary());
        summary.setFont(FontManager.getRunescapeSmallFont());
        summary.setForeground(colorForRegime(briefing.getRegime()));
        summary.setAlignmentX(LEFT_ALIGNMENT);
        add(summary);
        add(Box.createVerticalStrut(4));

        JPanel stats = new JPanel(new GridLayout(1, 4, 3, 0));
        stats.setOpaque(false);
        stats.setAlignmentX(LEFT_ALIGNMENT);
        stats.add(statWell(String.valueOf(briefing.getLiquidItems()), "Liquid"));
        stats.add(statWell(String.valueOf(briefing.getRisingItems()), "Rising"));
        stats.add(statWell(String.valueOf(briefing.getFallingItems()), "Falling"));
        stats.add(statWell(String.valueOf(briefing.getLiquidItems() - briefing.getStaleItems()), "Fresh"));
        add(stats);
        add(Box.createVerticalStrut(5));

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(QSColors.BG_DEEP);
        box.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(QSColors.BORDER, 1, true),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        box.setAlignmentX(LEFT_ALIGNMENT);
        addSection(box, "GAINERS", briefing.getGainers(), QSColors.EMERALD_400);
        addSection(box, "LOSERS", briefing.getLosers(), QSColors.RED_400);
        addSection(box, "HEATING", briefing.getHeating(), QSColors.EMERALD_400);
        addSection(box, "COOLING", briefing.getCooling(), QSColors.RED_400);
        addSection(box, "MOST TRADED", briefing.getMostActive(), QSColors.AMBER_400);
        add(box);
        add(Box.createVerticalStrut(6));
        setVisible(true);
        revalidate();
        repaint();
    }

    private MarketIntelligence.Parameters selectedParameters() {
        VolumeOption volume = (VolumeOption) volumeFilter.getSelectedItem();
        PriceOption price = (PriceOption) priceFilter.getSelectedItem();
        MoveOption move = (MoveOption) moveFilter.getSelectedItem();
        MarketIntelligence.Parameters defaults = MarketIntelligence.Parameters.defaults();
        return new MarketIntelligence.Parameters(
            volume == null ? defaults.getMinDailyVolume() : volume.minDailyVolume,
            price == null ? defaults.getMinItemValue() : price.minItemValue,
            move == null ? defaults.getMinMovePct() : move.minMovePct);
    }

    private static <T> void configureFilter(JComboBox<T> combo, java.util.function.Function<T, String> labeler,
                                             String tooltip) {
        combo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                                                                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                @SuppressWarnings("unchecked") T typed = (T) value;
                label.setText(typed == null ? "" : labeler.apply(typed));
                label.setFont(FontManager.getRunescapeSmallFont());
                label.setForeground(QSColors.SLATE_200);
                label.setBackground(isSelected ? QSColors.BG_HOVER : QSColors.BG_DEEP);
                return label;
            }
        });
        combo.setFont(FontManager.getRunescapeSmallFont());
        combo.setForeground(QSColors.SLATE_200);
        combo.setBackground(QSColors.BG_DEEP);
        combo.setBorder(new LineBorder(QSColors.BORDER, 1, true));
        combo.setMaximumRowCount(8);
        combo.setPreferredSize(new Dimension(54, 22));
        combo.setMaximumSize(new Dimension(68, 22));
        combo.setAlignmentX(LEFT_ALIGNMENT);
        combo.setToolTipText(tooltip);
    }

    private static JPanel filterField(String caption, JComboBox<?> combo) {
        JPanel field = new JPanel();
        field.setLayout(new BoxLayout(field, BoxLayout.Y_AXIS));
        field.setOpaque(false);
        JLabel label = new JLabel(caption);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(QSColors.SLATE_500);
        label.setAlignmentX(LEFT_ALIGNMENT);
        field.add(label);
        field.add(combo);
        return field;
    }

    private void addSection(JPanel box, String section, List<SignalRow> rows, Color tone) {
        if (rows == null || rows.isEmpty()) return;
        for (SignalRow row : rows) {
            String metric;
            if ("MOST TRADED".equals(section)) {
                metric = compact(row.getVolume()) + " trades";
            } else if ("HEATING".equals(section) || "COOLING".equals(section)) {
                metric = signed(row.getMomentumPct()) + " 5m";
            } else {
                metric = signed(row.getMovePct()) + " 24h";
            }
            String label = section + " · " + row.getItem().getName() + "  " + metric;
            if (box.getComponentCount() > 0) box.add(Box.createVerticalStrut(3));
            JLabel line = new JLabel("<html><body style='width:" + UiConstants.CARD_TEXT_WIDTH_PX + "px'>"
                + escape(label) + "</body></html>");
            line.setFont(FontManager.getRunescapeSmallFont());
            line.setForeground(tone);
            line.setAlignmentX(LEFT_ALIGNMENT);
            line.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            line.setToolTipText("Open in Scanner");
            line.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    onItemSelected.accept(row.getItem());
                }
            });
            box.add(line);
        }
    }

    private static JPanel statWell(String value, String label) {
        JPanel well = new JPanel();
        well.setLayout(new BoxLayout(well, BoxLayout.Y_AXIS));
        well.setBackground(QSColors.BG);
        JLabel v = new JLabel(value);
        v.setFont(FontManager.getRunescapeBoldFont());
        v.setForeground(QSColors.SLATE_200);
        v.setAlignmentX(CENTER_ALIGNMENT);
        JLabel l = new JLabel(label);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(QSColors.SLATE_500);
        l.setAlignmentX(CENTER_ALIGNMENT);
        well.add(v);
        well.add(l);
        return well;
    }

    private static String signed(double value) {
        return String.format(java.util.Locale.ROOT, "%+.1f%%", value);
    }

    private static String compact(long value) {
        if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0);
        return Long.toString(value);
    }

    private static Color colorForRegime(String regime) {
        if (regime == null) return QSColors.SLATE_400;
        if (regime.contains("RISING")) return QSColors.EMERALD_400;
        if (regime.contains("FALLING")) return QSColors.RED_400;
        return QSColors.AMBER_400;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private enum VolumeOption {
        K1("1k+", 1_000L),
        K5("5k+", 5_000L),
        K10("10k+", 10_000L),
        K50("50k+", 50_000L),
        K100("100k+", 100_000L);

        private final String label;
        private final long minDailyVolume;

        VolumeOption(String label, long minDailyVolume) {
            this.label = label;
            this.minDailyVolume = minDailyVolume;
        }

        @Override public String toString() { return label; }
    }

    private enum PriceOption {
        ANY("Any", 0),
        GP100("100gp+", 100),
        K1("1k+", 1_000),
        K10("10k+", 10_000),
        K100("100k+", 100_000),
        M1("1M+", 1_000_000);

        private final String label;
        private final int minItemValue;

        PriceOption(String label, int minItemValue) {
            this.label = label;
            this.minItemValue = minItemValue;
        }

        @Override public String toString() { return label; }
    }

    private enum MoveOption {
        P1("1%+", 1.0),
        P2("2%+", 2.0),
        P5("5%+", 5.0),
        P10("10%+", 10.0);

        private final String label;
        private final double minMovePct;

        MoveOption(String label, double minMovePct) {
            this.label = label;
            this.minMovePct = minMovePct;
        }

        @Override public String toString() { return label; }
    }
}
