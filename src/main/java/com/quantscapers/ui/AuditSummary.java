package com.quantscapers.ui;

import com.quantscapers.QSColors;
import com.quantscapers.engine.AuditResult;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;

/**
 * Renders an audit result as separate, labeled lines instead of one crammed
 * "72% hit rate · Hyper Active · grade A" string — that format read as noise
 * and clipped in the narrow sidebar. Shared by TopPicksBox and ItemCardBox
 * so both present audit info identically.
 */
public final class AuditSummary {
    private AuditSummary() {}

    public static JPanel build(AuditResult hist) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.setAlignmentX(0f);

        if (hist == null) {
            box.add(line("Not yet audited", QSColors.SLATE_500));
            return box;
        }
        if (hist.isFailed()) {
            box.add(line("Audit failed - try again", QSColors.RED_400));
            return box;
        }

        box.add(line(hist.getVerdict(), QSColors.forAuditVerdict(hist.getVerdict())));
        box.add(line(hist.getLikelihood() + "% of the time, the sell price was actually reached",
            QSColors.SLATE_200));
        box.add(Box.createVerticalStrut(3));

        int cols = hist.getStabilityGrade() != null ? 3 : 2;
        JPanel stats = new JPanel(new GridLayout(1, cols, 2, 0));
        stats.setOpaque(false);
        stats.setAlignmentX(0f);
        stats.setMaximumSize(new Dimension(UiConstants.CARD_TEXT_WIDTH_PX, Integer.MAX_VALUE));
        stats.add(statWell(String.valueOf(hist.getHits1d()), "24h Hits"));
        stats.add(statWell(String.valueOf(hist.getHits7d()), "7d Hits"));
        if (hist.getStabilityGrade() != null) {
            stats.add(statWell(String.valueOf(hist.getStabilityGrade()), "Grade"));
        }
        box.add(stats);
        return box;
    }

    // GridLayout sizes every column to whichever child reports the widest preferred
    // width, and a plain (non-HTML) JLabel never wraps - a caption a little too long
    // was enough to force the whole row past the card's border. Pinning each well's
    // preferred width caps that regardless of caption length.
    private static final int WELL_WIDTH = 46;

    private static JPanel statWell(String value, String label) {
        JPanel well = new JPanel();
        well.setLayout(new BoxLayout(well, BoxLayout.Y_AXIS));
        well.setBackground(QSColors.BG);

        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setAlignmentX(0.5f);
        v.setFont(FontManager.getRunescapeBoldFont());
        v.setForeground(QSColors.SLATE_200);

        JLabel l = new JLabel("<html><body style='width:" + (WELL_WIDTH - 6) + "px; text-align:center'>"
            + label + "</body></html>");
        l.setAlignmentX(0.5f);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(QSColors.SLATE_500);

        well.add(v);
        well.add(l);
        well.setPreferredSize(new Dimension(WELL_WIDTH, well.getPreferredSize().height));
        return well;
    }

    private static JLabel line(String text, Color color) {
        JLabel l = new JLabel("<html><body style='width:" + UiConstants.CARD_TEXT_WIDTH_PX + "px'>"
            + text + "</body></html>");
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(color);
        l.setAlignmentX(0f);
        return l;
    }
}
