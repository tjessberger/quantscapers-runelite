package com.quantscapers.ui;

import com.quantscapers.QSColors;
import com.quantscapers.QuantScapersPlugin;
import com.quantscapers.engine.AnalyzedItem;
import com.quantscapers.engine.AuditResult;
import com.quantscapers.engine.Ticket;
import com.quantscapers.engine.TicketBuilder;
import com.quantscapers.ui.util.GpFormat;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.FontManager;

/** Top three trade candidates ranked by GP/hour. Hidden when empty. */
public class TopPicksBox extends JPanel {

    private final QuantScapersPlugin plugin;

    public TopPicksBox(QuantScapersPlugin plugin) {
        this.plugin = plugin;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        setVisible(false);
    }

    public void update(List<AnalyzedItem> picks, Map<Integer, AuditResult> auditCache) {
        removeAll();
        if (picks.isEmpty()) {
            setVisible(false);
            return;
        }

        JLabel title = new JLabel("TRADE CANDIDATES");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(QSColors.EMERALD_400);
        title.setAlignmentX(LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Fresh quotes with spread checks");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(QSColors.SLATE_500);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        JPanel headerText = new JPanel();
        headerText.setLayout(new BoxLayout(headerText, BoxLayout.Y_AXIS));
        headerText.setOpaque(false);
        headerText.add(title);
        headerText.add(subtitle);

        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setOpaque(false);
        JPanel accent = new JPanel();
        accent.setBackground(QSColors.EMERALD_500);
        accent.setPreferredSize(new java.awt.Dimension(3, 1));
        bar.add(accent, BorderLayout.WEST);
        bar.add(headerText, BorderLayout.CENTER);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        add(bar);
        add(Box.createVerticalStrut(4));

        for (AnalyzedItem pick : picks) {
            add(pickRow(pick, auditCache.get(pick.getId())));
            add(Box.createVerticalStrut(4));
        }
        setVisible(true);
    }

    private JPanel pickRow(AnalyzedItem item, AuditResult hist) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(QSColors.BG_DEEP);
        row.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(QSColors.BORDER_EMERALD, 1, true),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        row.setAlignmentX(LEFT_ALIGNMENT);

        JPanel line1 = new JPanel(new BorderLayout(4, 0));
        line1.setOpaque(false);
        line1.setAlignmentX(LEFT_ALIGNMENT);
        // Full name, wrapped rather than hard-clipped — a name silently cut off mid-word
        // reads as broken, not just abbreviated. Width leaves room for gpHr sharing the row.
        JLabel name = new JLabel("<html><body style='width:" + (UiConstants.CARD_TEXT_WIDTH_PX - 50)
            + "px'>" + escape(item.getName()) + "</body></html>");
        name.setFont(FontManager.getRunescapeBoldFont());
        name.setForeground(QSColors.SLATE_200);
        JLabel gpHr = new JLabel(GpFormat.format(Math.round(item.getGpHour())) + "/hr");
        gpHr.setFont(FontManager.getRunescapeSmallFont());
        gpHr.setForeground(QSColors.AMBER_400);
        line1.add(name, BorderLayout.CENTER);
        line1.add(gpHr, BorderLayout.EAST);
        row.add(line1);

        Ticket ticket = TicketBuilder.build(item);
        if (ticket != null) {
            row.add(Box.createVerticalStrut(2));
            row.add(wrapped("BUY " + ticket.getQty() + " @ " + GpFormat.withCommas(ticket.getBuyAt()),
                QSColors.EMERALD_400));
            row.add(wrapped("SELL @ " + GpFormat.withCommas(ticket.getSellAt())
                + " · +" + GpFormat.format(ticket.getProfit()) + " after tax", QSColors.RED_400));
        }

        row.add(Box.createVerticalStrut(4));
        row.add(AuditSummary.build(hist));
        row.add(Box.createVerticalStrut(2));

        JButton auditBtn = new JButton(hist != null ? "Audit again" : "Run audit");
        auditBtn.setFont(FontManager.getRunescapeSmallFont());
        auditBtn.setForeground(QSColors.AMBER_400);
        auditBtn.setBorder(new LineBorder(QSColors.BORDER_AMBER, 1, true));
        auditBtn.setContentAreaFilled(false);
        auditBtn.setFocusPainted(false);
        auditBtn.setAlignmentX(LEFT_ALIGNMENT);
        auditBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        auditBtn.addActionListener(e -> {
            auditBtn.setEnabled(false);
            auditBtn.setText("Auditing...");
            plugin.requestAudit(item, () -> firePropertyChange("auditComplete", false, true));
        });
        row.add(auditBtn);

        return row;
    }

    private static JLabel wrapped(String text, java.awt.Color color) {
        JLabel l = new JLabel("<html><body style='width:" + UiConstants.CARD_TEXT_WIDTH_PX + "px'>"
            + escape(text) + "</body></html>");
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(color);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
