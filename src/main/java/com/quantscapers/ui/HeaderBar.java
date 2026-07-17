package com.quantscapers.ui;

import com.quantscapers.QSColors;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/** Title + subtitle on the left, refresh countdown chip + manual refresh button on the right. */
public class HeaderBar extends JPanel {

    private final JLabel countdownLabel = new JLabel();

    public HeaderBar(Runnable onRefreshClick) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        JLabel title = new JLabel("⚡ QUANTSCAPERS");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(QSColors.AMBER_500);

        JLabel subtitle = new JLabel("PREDICTIVE TERMINAL");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(QSColors.SLATE_500);

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(subtitle);

        countdownLabel.setOpaque(true);
        countdownLabel.setBackground(QSColors.BG_DEEP);
        countdownLabel.setForeground(QSColors.SLATE_400);
        countdownLabel.setFont(FontManager.getRunescapeSmallFont());
        countdownLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        setCountdown(30);

        JButton refresh = new JButton("⟳");
        refresh.setFont(FontManager.getRunescapeBoldFont());
        refresh.setForeground(QSColors.SLATE_400);
        refresh.setBorderPainted(false);
        refresh.setContentAreaFilled(false);
        refresh.setFocusPainted(false);
        refresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refresh.setToolTipText("Refresh now");
        refresh.addActionListener(e -> onRefreshClick.run());

        JPanel rightBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightBlock.setOpaque(false);
        rightBlock.add(countdownLabel);
        rightBlock.add(refresh);

        add(titleBlock, BorderLayout.WEST);
        add(rightBlock, BorderLayout.EAST);
    }

    public void setCountdown(int seconds) {
        countdownLabel.setText("T-" + seconds + "s");
        if (seconds <= 5) {
            countdownLabel.setBackground(new java.awt.Color(0xef, 0x44, 0x44, 40));
            countdownLabel.setForeground(QSColors.RED_500);
        } else {
            countdownLabel.setBackground(QSColors.BG_DEEP);
            countdownLabel.setForeground(QSColors.SLATE_400);
        }
    }
}
