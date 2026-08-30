package com.quantscapers;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("quantscapers")
public interface QuantScapersConfig extends Config {

    enum BudgetCap {
        K100("100k", 100_000), K250("250k", 250_000), K500("500k", 500_000),
        M1("1M", 1_000_000), M5("5M", 5_000_000), M10("10M", 10_000_000),
        M50("50M", 50_000_000), M100("100M", 100_000_000), M500("500M", 500_000_000),
        MAX("Max", Integer.MAX_VALUE);

        private final String label;
        private final long value;

        BudgetCap(String label, long value) { this.label = label; this.value = value; }

        public long value() { return value; }

        @Override
        public String toString() { return label; }
    }

    enum ProfitFloor {
        ANY("Any", 0), K100("100k", 100_000), K500("500k", 500_000),
        M1("1M", 1_000_000), M2("2M", 2_000_000), M5("5M", 5_000_000), M10("10M", 10_000_000);

        private final String label;
        private final long value;

        ProfitFloor(String label, long value) { this.label = label; this.value = value; }

        public long value() { return value; }

        @Override
        public String toString() { return label; }
    }

    enum RoiFloor {
        P0_1("0.1%+", 0.1), P0_5("0.5%+", 0.5), P1("1%+", 1), P2("2%+", 2),
        P3("3%+", 3), P5("5%+", 5), P10("10%+", 10), P15("15%+", 15);

        private final String label;
        private final double value;

        RoiFloor(String label, double value) { this.label = label; this.value = value; }

        public double value() { return value; }

        @Override
        public String toString() { return label; }
    }

    enum FillTimeCap {
        ANY("Any", -1), M5("<=5min", 5), M15("<=15min", 15), M30("<=30min", 30),
        H1("<=1h", 60), H2("<=2h", 120), H4("<=4h", 240), H8("<=8h", 480),
        H12("<=12h", 720), H24("<=24h", 1440), H48("<=48h", 2880);

        private final String label;
        private final int minutes; // -1 means "any" (no cap)

        FillTimeCap(String label, int minutes) { this.label = label; this.minutes = minutes; }

        public int minutes() { return minutes; }

        public boolean isAny() { return minutes < 0; }

        @Override
        public String toString() { return label; }
    }

    enum SortBy {
        PROFIT("Net Profit"), GP_HR("GP / Hour"), ROI("Yield %"), VOLUME("Daily Vol"),
        NEWEST("Newest Entries");

        private final String label;

        SortBy(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    enum ViewMode {
        FLIP("Trade View"), ALCH("Alch View");

        private final String label;

        ViewMode(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    enum NotificationMode {
        TRAY("Tray Only"), CHAT("Chat Only"), BOTH("Both");

        private final String label;

        NotificationMode(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    enum NotificationViewMode {
        FLIP("Trades Only"), ALCH("Alch Only"), BOTH("Both");

        private final String label;

        NotificationViewMode(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }


    @ConfigItem(
        keyName = "enableWikiMarketData",
        name = "Enable Wiki market data",
        description = "Downloads Grand Exchange market snapshots from the OSRS Wiki Prices API. "
            + "No RuneScape account, player, Grand Exchange offer, or telemetry data is sent.",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        position = 0
    )
    default boolean enableWikiMarketData() { return false; }

    @ConfigItem(
        keyName = "maxBuyPrice",
        name = "Budget max (full limit cost)",
        description = "Skip items whose buy price times buy limit exceeds this.",
        position = 1
    )
    default BudgetCap maxBuyPrice() { return BudgetCap.MAX; }

    @ConfigItem(
        keyName = "minProfit",
        name = "Min profit",
        description = "Skip items with less realistic net profit than this.",
        position = 2
    )
    default ProfitFloor minProfit() { return ProfitFloor.M1; }

    @ConfigItem(
        keyName = "minROI",
        name = "Target ROI",
        description = "Skip items with a lower return on investment than this.",
        position = 3
    )
    default RoiFloor minROI() { return RoiFloor.P2; }

    @ConfigItem(
        keyName = "maxFillTime",
        name = "Max fill time",
        description = "Skip items whose estimated fill time per side exceeds this.",
        position = 4
    )
    default FillTimeCap maxFillTime() { return FillTimeCap.ANY; }

    @ConfigItem(
        keyName = "sortBy",
        name = "Sort priority",
        description = "How the item list is ordered.",
        position = 5
    )
    default SortBy sortBy() { return SortBy.PROFIT; }

    @ConfigItem(
        keyName = "viewMode",
        name = "View mode",
        description = "Switch between trade-opportunity and Alch views.",
        position = 6
    )
    default ViewMode viewMode() { return ViewMode.FLIP; }

    @ConfigItem(
        keyName = "enableNotifications",
        name = "Enable notifications",
        description = "Toggle notifications for new profitable items.",
        position = 7
    )
    default boolean enableNotifications() { return false; }

    @ConfigItem(
        keyName = "notificationMode",
        name = "Notification mode",
        description = "Choose how you want to be notified.",
        position = 8
    )
    default NotificationMode notificationMode() { return NotificationMode.TRAY; }

    @ConfigItem(
        keyName = "notificationMinProfit",
        name = "Notification min profit",
        description = "Minimum profit required to trigger a notification.",
        position = 9
    )
    default ProfitFloor notificationMinProfit() { return ProfitFloor.M1; }

    @ConfigItem(
        keyName = "notificationMinROI",
        name = "Notification min ROI",
        description = "Minimum ROI required to trigger a notification.",
        position = 10
    )
    default RoiFloor notificationMinROI() { return RoiFloor.P2; }

    @ConfigItem(
        keyName = "notificationViewMode",
        name = "Notification view mode",
        description = "Trigger notifications for trade opportunities, Alch targets, or Both.",
        position = 11
    )
    default NotificationViewMode notificationViewMode() { return NotificationViewMode.BOTH; }

    @ConfigItem(
        keyName = "auditCacheJson",
        name = "",
        description = "",
        hidden = true
    )
    default String auditCacheJson() { return ""; }

    @ConfigItem(
        keyName = "auditCacheJson",
        name = "",
        description = "",
        hidden = true
    )
    void setAuditCacheJson(String json);

    @ConfigItem(
        keyName = "vaultJson",
        name = "",
        description = "",
        hidden = true
    )
    default String vaultJson() { return ""; }

    @ConfigItem(
        keyName = "vaultJson",
        name = "",
        description = "",
        hidden = true
    )
    void setVaultJson(String json);

    @ConfigItem(
        keyName = "vaultCollapsed",
        name = "",
        description = "",
        hidden = true
    )
    default boolean vaultCollapsed() { return false; }

    @ConfigItem(
        keyName = "vaultCollapsed",
        name = "",
        description = "",
        hidden = true
    )
    void setVaultCollapsed(boolean collapsed);

    @ConfigItem(
        keyName = "suppressedJson",
        name = "",
        description = "",
        hidden = true
    )
    default String suppressedJson() { return ""; }

    @ConfigItem(
        keyName = "suppressedJson",
        name = "",
        description = "",
        hidden = true
    )
    void setSuppressedJson(String json);

    @ConfigItem(
        keyName = "customPresetsJson",
        name = "",
        description = "",
        hidden = true
    )
    default String customPresetsJson() { return ""; }

    @ConfigItem(
        keyName = "customPresetsJson",
        name = "",
        description = "",
        hidden = true
    )
    void setCustomPresetsJson(String json);
}
