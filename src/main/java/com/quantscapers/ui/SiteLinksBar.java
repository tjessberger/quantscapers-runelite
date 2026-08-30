package com.quantscapers.ui;

import com.quantscapers.QSColors;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** Always-visible external links, separated from the plugin's internal tabs. */
public class SiteLinksBar extends JPanel {
    private static final String SITE = "https://quantscapers.com";

    public SiteLinksBar() {
        setLayout(new BorderLayout(4, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        JButton website = actionButton("QUANTSCAPERS.COM  ▾", "Open QuantScapers.com links");
        website.addActionListener(e -> websiteMenu().show(website, 0, website.getHeight() + 2));

        JButton social = actionButton("X @Quantscapers", "Open @Quantscapers on X");
        social.setPreferredSize(new Dimension(96, 24));
        social.addActionListener(e -> open("https://x.com/Quantscapers"));

        add(website, BorderLayout.CENTER);
        add(social, BorderLayout.EAST);
    }

    private JPopupMenu websiteMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(QSColors.BG_DEEP);
        menu.setBorder(new LineBorder(QSColors.BORDER_AMBER, 1, true));
        menu.add(menuItem("Home", SITE));
        menu.add(menuItem("Market Overview", SITE + "/market"));
        menu.add(menuItem("Full Item Table", SITE + "/market/table"));
        menu.add(menuItem("Gear Indices", SITE + "/market/indices"));
        return menu;
    }

    private JMenuItem menuItem(String text, String url) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(FontManager.getRunescapeSmallFont());
        item.setForeground(QSColors.SLATE_200);
        item.setBackground(QSColors.BG_DEEP);
        item.addActionListener(e -> open(url));
        return item;
    }

    private static JButton actionButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setForeground(QSColors.AMBER_400);
        button.setBackground(QSColors.BG_DEEP);
        button.setBorder(new LineBorder(QSColors.BORDER_AMBER, 1, true));
        button.setFocusPainted(false);
        button.setToolTipText(tooltip);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static void open(String url) {
        LinkBrowser.browse(url);
    }
}
