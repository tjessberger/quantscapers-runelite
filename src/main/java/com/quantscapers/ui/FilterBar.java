package com.quantscapers.ui;

import com.quantscapers.QSColors;
import com.quantscapers.QuantScapersConfig;
import com.quantscapers.QuantScapersPlugin;
import com.quantscapers.engine.Constants;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.FontManager;

/*
 * Every combo here is wrapped by labeledCombo() so its purpose stays
 * visible once collapsed - a bare "100M" or "2%+" reads as meaningless
 * without a caption sitting above it.
 */

/** Preset/sort row, leads-count row, and a collapsible detailed-filters row. All bound to config. */
public class FilterBar extends JPanel {

    private static final String GROUP = "quantscapers";

    private final QuantScapersPlugin plugin;
    private final JLabel leadsLabel = new JLabel();
    private final JLabel suppressedLabel = new JLabel();
    private final JPanel detailRow;

    // Custom presets tracking
    private final Map<String, CustomPreset> customPresets = new LinkedHashMap<>();

    private final JComboBox<String> presetCombo;
    private final JComboBox<QuantScapersConfig.SortBy> sortCombo;
    private final JComboBox<QuantScapersConfig.BudgetCap> budgetCombo;
    private final JComboBox<QuantScapersConfig.ProfitFloor> profitCombo;
    private final JComboBox<QuantScapersConfig.RoiFloor> roiCombo;
    private final JComboBox<QuantScapersConfig.FillTimeCap> fillCombo;
    private final JComboBox<QuantScapersConfig.ViewMode> viewCombo;

    // While true, combo ActionListeners no-op - set during syncFromConfig() so a
    // programmatic setSelectedItem() doesn't echo straight back into config.
    private boolean syncing = false;

    public FilterBar(QuantScapersPlugin plugin) {
        this.plugin = plugin;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        presetCombo = new JComboBox<>();
        sortCombo = new JComboBox<>(QuantScapersConfig.SortBy.values());
        budgetCombo = new JComboBox<>(QuantScapersConfig.BudgetCap.values());
        profitCombo = new JComboBox<>(QuantScapersConfig.ProfitFloor.values());
        roiCombo = new JComboBox<>(QuantScapersConfig.RoiFloor.values());
        fillCombo = new JComboBox<>(QuantScapersConfig.FillTimeCap.values());
        viewCombo = new JComboBox<>(QuantScapersConfig.ViewMode.values());

        add(row1());
        add(row2());
        detailRow = row3();
        detailRow.setVisible(false);
        add(detailRow);

        syncFromConfig();
    }

    public void setLeadsCount(int n) {
        String suffix = n > Constants.MAX_CARDS_RENDERED
            ? " LEADS (top " + Constants.MAX_CARDS_RENDERED + " shown)"
            : " LEADS";
        leadsLabel.setText(n + suffix);
    }

    /** AVOID items confirmed by a fresh audit's low hit rate, hidden unless the user asks to see them. */
    public void setSuppressedInfo(int suppressedCount, boolean showing) {
        if (suppressedCount == 0) {
            suppressedLabel.setText("");
            suppressedLabel.setVisible(false);
            return;
        }
        suppressedLabel.setText(suppressedCount + " suppressed · " + (showing ? "hide" : "show"));
        suppressedLabel.setVisible(true);
    }

    private void loadCustomPresets() {
        customPresets.clear();
        String json = plugin.getConfig().customPresetsJson();
        if (json != null && !json.trim().isEmpty()) {
            try {
                java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<CustomPreset>>() {}.getType();
                List<CustomPreset> list = plugin.getGson().fromJson(json, type);
                if (list != null) {
                    for (CustomPreset cp : list) {
                        customPresets.put(cp.name, cp);
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void saveCustomPresets() {
        try {
            String json = plugin.getGson().toJson(new ArrayList<>(customPresets.values()));
            plugin.getConfigManager().setConfiguration(GROUP, "customPresetsJson", json);
        } catch (Exception e) {
            // ignore
        }
    }

    private void rebuildPresetCombo() {
        syncing = true;
        try {
            presetCombo.removeAllItems();
            presetCombo.addItem("Preset...");
            presetCombo.addItem("Fast Flips");
            presetCombo.addItem("Big Ticket");
            presetCombo.addItem("High Yield");
            for (String name : customPresets.keySet()) {
                presetCombo.addItem(name);
            }
        } finally {
            syncing = false;
        }
    }

    /** Pushes the current config values into every combo without re-triggering their listeners. */
    public void syncFromConfig() {
        loadCustomPresets();
        rebuildPresetCombo();
        syncing = true;
        try {
            sortCombo.setSelectedItem(plugin.getConfig().sortBy());
            budgetCombo.setSelectedItem(plugin.getConfig().maxBuyPrice());
            profitCombo.setSelectedItem(plugin.getConfig().minProfit());
            roiCombo.setSelectedItem(plugin.getConfig().minROI());
            fillCombo.setSelectedItem(plugin.getConfig().maxFillTime());
            viewCombo.setSelectedItem(plugin.getConfig().viewMode());
        } finally {
            syncing = false;
        }
    }

    private JPanel row1() {
        JPanel row = new JPanel(new GridLayout(1, 3, 6, 0));
        row.setOpaque(false);

        style(presetCombo);
        presetCombo.addActionListener(e -> {
            if (syncing) return;
            int idx = presetCombo.getSelectedIndex();
            if (idx > 0) {
                applyPreset(idx);
                presetCombo.setSelectedIndex(0);
            }
        });

        style(sortCombo);
        sortCombo.addActionListener(e -> {
            if (syncing) return;
            setConfig("sortBy", sortCombo.getSelectedItem());
        });

        style(viewCombo);
        viewCombo.addActionListener(e -> {
            if (syncing) return;
            setConfig("viewMode", viewCombo.getSelectedItem());
        });

        row.add(labeledCombo("Preset", presetCombo));
        row.add(labeledCombo("Sort", sortCombo));
        row.add(labeledCombo("Mode", viewCombo));
        return row;
    }

    private JPanel row2() {
        JPanel row = new JPanel(new java.awt.BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        leadsLabel.setFont(FontManager.getRunescapeSmallFont());
        leadsLabel.setForeground(QSColors.SLATE_400);
        setLeadsCount(0);

        suppressedLabel.setFont(FontManager.getRunescapeSmallFont());
        suppressedLabel.setForeground(QSColors.AMBER_400);
        suppressedLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        suppressedLabel.setVisible(false);
        suppressedLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                plugin.setShowSuppressed(!plugin.isShowSuppressed());
            }
        });

        JButton toggle = new JButton("Filters ⚙");
        toggle.setFont(FontManager.getRunescapeSmallFont());
        toggle.setForeground(QSColors.SLATE_400);
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);
        toggle.setFocusPainted(false);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.addActionListener(e -> {
            detailRow.setVisible(!detailRow.isVisible());
            revalidate();
        });

        row.add(leadsLabel, java.awt.BorderLayout.WEST);
        row.add(suppressedLabel, java.awt.BorderLayout.CENTER);
        row.add(toggle, java.awt.BorderLayout.EAST);
        return row;
    }

    private JPanel row3() {
        JPanel grid = new JPanel(new GridLayout(3, 2, 6, 4));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        style(budgetCombo);
        budgetCombo.addActionListener(e -> {
            if (syncing) return;
            setConfig("maxBuyPrice", budgetCombo.getSelectedItem());
        });

        style(profitCombo);
        profitCombo.addActionListener(e -> {
            if (syncing) return;
            setConfig("minProfit", profitCombo.getSelectedItem());
        });

        style(roiCombo);
        roiCombo.addActionListener(e -> {
            if (syncing) return;
            setConfig("minROI", roiCombo.getSelectedItem());
        });

        style(fillCombo);
        fillCombo.addActionListener(e -> {
            if (syncing) return;
            setConfig("maxFillTime", fillCombo.getSelectedItem());
        });

        JButton savePresetBtn = new JButton("Save Preset");
        savePresetBtn.setFont(FontManager.getRunescapeSmallFont());
        savePresetBtn.setForeground(QSColors.SLATE_200);
        savePresetBtn.setBackground(QSColors.BG_DEEP);
        savePresetBtn.setBorder(new LineBorder(QSColors.BORDER, 1));
        savePresetBtn.setFocusPainted(false);
        savePresetBtn.addActionListener(e -> {
            String name = javax.swing.JOptionPane.showInputDialog(this, "Enter preset name:", "Save Custom Preset", javax.swing.JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                name = name.trim();
                if ("Fast Flips".equalsIgnoreCase(name) || "Big Ticket".equalsIgnoreCase(name) || "High Yield".equalsIgnoreCase(name) || "Preset...".equalsIgnoreCase(name)) {
                    javax.swing.JOptionPane.showMessageDialog(this, "Cannot overwrite built-in presets.", "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                    return;
                }
                CustomPreset cp = new CustomPreset(
                    name,
                    plugin.getConfig().maxBuyPrice().name(),
                    plugin.getConfig().minProfit().name(),
                    plugin.getConfig().minROI().name(),
                    plugin.getConfig().maxFillTime().name()
                );
                customPresets.put(name, cp);
                saveCustomPresets();
                syncFromConfig();
            }
        });

        JButton deletePresetBtn = new JButton("Delete Preset");
        deletePresetBtn.setFont(FontManager.getRunescapeSmallFont());
        deletePresetBtn.setForeground(QSColors.SLATE_200);
        deletePresetBtn.setBackground(QSColors.BG_DEEP);
        deletePresetBtn.setBorder(new LineBorder(QSColors.BORDER, 1));
        deletePresetBtn.setFocusPainted(false);
        deletePresetBtn.addActionListener(e -> {
            List<String> options = new ArrayList<>(customPresets.keySet());
            if (options.isEmpty()) {
                javax.swing.JOptionPane.showMessageDialog(this, "No custom presets to delete.", "Delete Preset", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String selected = (String) javax.swing.JOptionPane.showInputDialog(
                this,
                "Select preset to delete:",
                "Delete Preset",
                javax.swing.JOptionPane.PLAIN_MESSAGE,
                null,
                options.toArray(),
                options.get(0)
            );
            if (selected != null) {
                customPresets.remove(selected);
                saveCustomPresets();
                syncFromConfig();
            }
        });

        grid.add(labeledCombo("Budget", budgetCombo));
        grid.add(labeledCombo("Min Profit", profitCombo));
        grid.add(labeledCombo("Min ROI", roiCombo));
        grid.add(labeledCombo("Fill Time", fillCombo));
        grid.add(labeledButton("Preset Actions", savePresetBtn));
        grid.add(labeledButton("Preset Actions", deletePresetBtn));
        return grid;
    }

    /** Caption sits above the combo so the filter's identity survives collapsing back to just its value. */
    private static JPanel labeledCombo(String caption, JComboBox<?> combo) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);

        JLabel label = new JLabel(caption);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(QSColors.SLATE_500);
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        combo.setAlignmentX(JComboBox.LEFT_ALIGNMENT);

        wrapper.add(label);
        wrapper.add(combo);
        return wrapper;
    }

    private static JPanel labeledButton(String caption, JButton button) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);

        JLabel label = new JLabel(caption);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(QSColors.SLATE_500);
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        button.setAlignmentX(JButton.LEFT_ALIGNMENT);
        button.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 22));
        button.setPreferredSize(new java.awt.Dimension(Integer.MAX_VALUE, 22));

        wrapper.add(label);
        wrapper.add(button);
        return wrapper;
    }

    private void applyPreset(int idx) {
        if (idx == 1) { // Fast Flips
            setConfig("maxFillTime", QuantScapersConfig.FillTimeCap.H1);
            setConfig("minROI", QuantScapersConfig.RoiFloor.P1);
        } else if (idx == 2) { // Big Ticket
            setConfig("maxBuyPrice", QuantScapersConfig.BudgetCap.MAX);
            setConfig("minProfit", QuantScapersConfig.ProfitFloor.M5);
        } else if (idx == 3) { // High Yield
            setConfig("minROI", QuantScapersConfig.RoiFloor.P10);
            setConfig("maxFillTime", QuantScapersConfig.FillTimeCap.ANY);
        } else if (idx > 3) {
            String selectedName = presetCombo.getItemAt(idx);
            CustomPreset cp = customPresets.get(selectedName);
            if (cp != null) {
                try {
                    setConfig("maxBuyPrice", QuantScapersConfig.BudgetCap.valueOf(cp.maxBuyPrice));
                } catch (Exception ignored) {}
                try {
                    setConfig("minProfit", QuantScapersConfig.ProfitFloor.valueOf(cp.minProfit));
                } catch (Exception ignored) {}
                try {
                    setConfig("minROI", QuantScapersConfig.RoiFloor.valueOf(cp.minROI));
                } catch (Exception ignored) {}
                try {
                    setConfig("maxFillTime", QuantScapersConfig.FillTimeCap.valueOf(cp.maxFillTime));
                } catch (Exception ignored) {}
            }
        }
    }

    private void setConfig(String key, Object value) {
        plugin.getConfigManager().setConfiguration(GROUP, key, value);
    }

    private static void style(JComboBox<?> combo) {
        combo.setFont(FontManager.getRunescapeSmallFont());
        combo.setForeground(QSColors.SLATE_200);
        combo.setBackground(QSColors.BG_DEEP);
        combo.setBorder(new LineBorder(QSColors.BORDER, 1));
    }

    private static class CustomPreset {
        String name;
        String maxBuyPrice;
        String minProfit;
        String minROI;
        String maxFillTime;

        public CustomPreset(String name, String maxBuyPrice, String minProfit, String minROI, String maxFillTime) {
            this.name = name;
            this.maxBuyPrice = maxBuyPrice;
            this.minProfit = minProfit;
            this.minROI = minROI;
            this.maxFillTime = maxFillTime;
        }
    }
}
