package com.quantscapers.ui;

import com.quantscapers.QSColors;
import com.quantscapers.api.PriceQuote;
import com.quantscapers.engine.RaidsWatchlist;
import com.quantscapers.ui.util.AgeLabel;
import com.quantscapers.ui.util.GpFormat;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** Community watchlist using only the existing Wiki price snapshot. */
public class RaidsWatchlistBox extends JPanel {
    private final RaidsWatchlist data;
    private final JTextField search = new JTextField();
    private final JComboBox<String> calls = new JComboBox<>(new String[]{"All calls", "Rise", "Fall", "Mixed"});
    private final JPanel rows = column();
    private final Set<Integer> expanded = new HashSet<>();
    private final List<AgeLabel> ages = new ArrayList<>();
    private final Consumer<String> openScanner;
    private Map<Integer, PriceQuote> prices = Collections.emptyMap();

    public RaidsWatchlistBox(RaidsWatchlist data, Consumer<String> openScanner) {
        this.data = data;
        this.openScanner = openScanner;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        add(wrapped(data.getTitle(), QSColors.AMBER_400));
        add(wrapped("Scheduled launch: " + data.getLaunch(), QSColors.SLATE_400));
        add(wrapped("Community evidence: " + data.getEvidenceUpdated()
            + ". Bundled snapshot; open the page for updates.", QSColors.SLATE_400));
        add(wrapped("Community speculation, not financial advice. Calls describe mentions, not price forecasts.",
            QSColors.SLATE_400));
        add(button("Open page / charts", () -> LinkBrowser.browse(data.getSourceUrl())));
        add(Box.createVerticalStrut(6));

        add(wrapped("Search items", QSColors.SLATE_400));
        search.setToolTipText("Search Raids 4 items");
        search.setFont(FontManager.getRunescapeSmallFont());
        search.setForeground(QSColors.SLATE_200);
        search.setBackground(QSColors.BG_DEEP);
        search.getAccessibleContext().setAccessibleName("Search Raids 4 watchlist");
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        search.setAlignmentX(LEFT_ALIGNMENT);
        add(search);
        calls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        calls.setAlignmentX(LEFT_ALIGNMENT);
        calls.getAccessibleContext().setAccessibleName("Filter community call");
        calls.addActionListener(e -> renderRows());
        add(calls);
        add(Box.createVerticalStrut(6));
        add(rows);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { renderRows(); }
            @Override public void removeUpdate(DocumentEvent e) { renderRows(); }
            @Override public void changedUpdate(DocumentEvent e) { renderRows(); }
        });

        add(wrapped("UNRELEASED REWARDS", QSColors.AMBER_400));
        add(wrapped("Community speculation; no tradeable prices yet.", QSColors.SLATE_400));
        for (String note : data.getUnreleased()) add(wrapped(note, QSColors.SLATE_400));
        add(Box.createVerticalStrut(6));
        add(wrapped("HOW CALLS ARE GROUPED", QSColors.AMBER_400));
        add(wrapped(data.getMethod(), QSColors.SLATE_400));
        renderRows();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    public void update(Map<Integer, PriceQuote> latest) {
        prices = latest;
        renderRows();
    }

    public void refreshAges(long nowMs) {
        for (AgeLabel age : ages) age.refresh(nowMs);
    }

    private void renderRows() {
        rows.removeAll();
        ages.clear();
        String call = (String) calls.getSelectedItem();
        boolean moreHeading = false;
        int count = 0;
        for (RaidsWatchlist.Entry item : data.getItems()) {
            if (!item.matches(search.getText(), call)) continue;
            if (item.getRank() == 0 && !moreHeading) {
                rows.add(wrapped("MORE ITEMS MENTIONED", QSColors.AMBER_400));
                moreHeading = true;
            }
            rows.add(itemRow(item));
            rows.add(Box.createVerticalStrut(6));
            count++;
        }
        if (count == 0) rows.add(wrapped("No matching community items.", QSColors.SLATE_400));
        refreshAges(System.currentTimeMillis());
        revalidate();
        repaint();
    }

    private JPanel itemRow(RaidsWatchlist.Entry item) {
        JPanel row = column();
        row.setOpaque(true);
        row.setBackground(QSColors.BG_DEEP);
        row.setBorder(BorderFactory.createCompoundBorder(new LineBorder(QSColors.BORDER, 1, true),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        Color callColor = "Rise".equals(item.getCall()) ? QSColors.EMERALD_400
            : "Fall".equals(item.getCall()) ? QSColors.RED_400 : QSColors.AMBER_400;
        row.add(wrapped((item.getRank() > 0 ? "#" + item.getRank() + " · " : "")
            + item.getCall(), callColor));
        JLabel name = wrapped(item.getName(), QSColors.SLATE_200);
        name.setFont(FontManager.getRunescapeBoldFont());
        row.add(name);
        row.add(wrapped(priceText(prices.get(item.getId())), QSColors.AMBER_400));
        PriceQuote quote = prices.get(item.getId());
        if (quote != null && quote.getHigh() != null && quote.getHigh() > 0) {
            AgeLabel age = new AgeLabel(" ago · buy trade");
            age.setUnixSec(quote.getHighTime() == null ? 0 : quote.getHighTime());
            age.setAlignmentX(LEFT_ALIGNMENT);
            ages.add(age);
            row.add(age);
        }
        row.add(wrapped(item.getMentions() + " mentions · " + item.getUpvotes() + " upvotes", QSColors.SLATE_400));
        if (item.getRank() > 0) {
            row.add(wrapped("Rise " + item.getRise() + " · Fall " + item.getFall()
                + " · Other " + item.getOther(), QSColors.SLATE_400));
            JPanel details = column();
            details.add(wrapped("Why some expect a rise", QSColors.EMERALD_400));
            details.add(wrapped(item.getRiseReason(), QSColors.SLATE_200));
            details.add(button("Supporting view", () -> LinkBrowser.browse(item.getRiseUrl())));
            details.add(wrapped("Why others expect a fall", QSColors.RED_400));
            details.add(wrapped(item.getFallReason(), QSColors.SLATE_200));
            details.add(button("Opposing view", () -> LinkBrowser.browse(item.getFallUrl())));
            details.setVisible(expanded.contains(item.getId()));
            JToggleButton toggle = new JToggleButton("Reasons / evidence", details.isVisible());
            toggle.setFont(FontManager.getRunescapeSmallFont());
            toggle.setAlignmentX(LEFT_ALIGNMENT);
            toggle.addActionListener(e -> {
                if (toggle.isSelected()) expanded.add(item.getId());
                else expanded.remove(item.getId());
                details.setVisible(toggle.isSelected());
                revalidate();
            });
            row.add(toggle);
            row.add(details);
        }
        row.add(button("Open in Scanner", () -> openScanner.accept(item.getName())));
        row.add(button("Item page / history", () -> LinkBrowser.browse(item.getUrl())));
        return row;
    }

    static String priceText(PriceQuote quote) {
        return quote == null || quote.getHigh() == null || quote.getHigh() <= 0
            ? "Current price unavailable"
            : "Current price: " + GpFormat.format(quote.getHigh()) + " GP";
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private static JLabel wrapped(String text, Color color) {
        JLabel label = new JLabel("<html><body style='width:" + UiConstants.CARD_TEXT_WIDTH_PX + "px'>"
            + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</body></html>");
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setForeground(QSColors.AMBER_400);
        button.setBorder(new LineBorder(QSColors.BORDER_AMBER, 1, true));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.addActionListener(e -> action.run());
        return button;
    }
}
