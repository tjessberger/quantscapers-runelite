package com.quantscapers.ui;

import com.quantscapers.QSColors;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.FontManager;

/**
 * Pill-nav row linking to the web dashboard, directly below the logo —
 * visually mirrors quantscapers.com's own nav (a rounded rail of pill
 * buttons with an amber hover state) using the plugin's design tokens.
 * The plugin stays verdict-first; full browsing/filtering/charting lives
 * on the site, this is the door to it. Opens links the same way as
 * ItemCardBox's wikiLink().
 */
public class SiteLinksBar extends JPanel {

    private static final String SITE = "https://quantscapers.com";

    public SiteLinksBar() {
        // The rail: dark inset well with a rounded hairline border, matching
        // the site's .nav-links container (and FilterBar's bordered controls).
        setLayout(new java.awt.BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0)); // breathing room before the sections below

        JPanel rail = new JPanel(new GridLayout(1, 3, 4, 0));
        rail.setOpaque(true);
        rail.setBackground(QSColors.BG_DEEP);
        rail.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(QSColors.BORDER, 1, true),
            BorderFactory.createEmptyBorder(3, 3, 3, 3)));

        rail.add(new PillLink("OVERVIEW", "Market overview — opens quantscapers.com/market in your browser", SITE + "/market"));
        rail.add(new PillLink("TABLE", "Full GE item table — opens quantscapers.com/market/table in your browser", SITE + "/market/table"));
        rail.add(new PillLink("INDICES", "Gear & loadout indices — opens quantscapers.com/market/indices in your browser", SITE + "/market/indices"));

        add(rail, java.awt.BorderLayout.CENTER);
    }

    /** One pill: quiet slate text at rest, amber pill fill + amber text on hover. */
    private static final class PillLink extends JLabel {
        private boolean hovered = false;

        PillLink(String text, String tooltip, String url) {
            super(text, SwingConstants.CENTER);
            setFont(FontManager.getRunescapeSmallFont());
            setForeground(QSColors.SLATE_400);
            setToolTipText(tooltip);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
            // Not opaque: an opaque JLabel would paint a square background
            // under our rounded hover fill. We paint the pill ourselves.
            setOpaque(false);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        Desktop.getDesktop().browse(new URI(url));
                    } catch (Exception ignored) {
                        // best-effort; no in-panel fallback needed for an external link
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    setForeground(QSColors.AMBER_400);
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    setForeground(QSColors.SLATE_400);
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (hovered) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(QSColors.chipBackground(QSColors.AMBER_500));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
