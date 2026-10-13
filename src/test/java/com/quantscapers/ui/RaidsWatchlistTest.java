package com.quantscapers.ui;

import com.quantscapers.QuantScapersConfig;
import com.quantscapers.QuantScapersPlugin;
import com.quantscapers.api.PriceQuote;
import com.quantscapers.engine.RaidsWatchlist;
import com.quantscapers.engine.TrackedTrade;
import java.awt.Component;
import java.awt.Container;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class RaidsWatchlistTest {
    @Test
    public void snapshotPreservesPageRanksCountsAndEvidence() {
        RaidsWatchlist data = RaidsWatchlist.load();
        assertEquals("2026-10-12", data.getEvidenceUpdated());
        assertEquals("2026-10-13", data.getCapturedAt());
        assertEquals(43, data.getItems().size());
        assertEquals(5, data.getUnreleased().size());
        assertEquals(22486, data.getItems().get(0).getId());
        assertEquals(119, data.getItems().get(0).getMentions());
        assertEquals(729, data.getItems().get(0).getUpvotes());
        int rank = 0;
        for (RaidsWatchlist.Entry entry : data.getItems()) {
            assertTrue(entry.getId() > 0);
            assertFalse(entry.getName().isEmpty());
            assertTrue(Arrays.asList("Rise", "Fall", "Mixed").contains(entry.getCall()));
            assertEquals("quantscapers.com", URI.create(entry.getUrl()).getHost());
            if (entry.getRank() == 0) continue;
            assertEquals(++rank, entry.getRank());
            assertFalse(entry.getRiseReason().isEmpty());
            assertFalse(entry.getFallReason().isEmpty());
            assertEquals("www.reddit.com", URI.create(entry.getRiseUrl()).getHost());
            assertEquals("www.reddit.com", URI.create(entry.getFallUrl()).getHost());
        }
        assertEquals(10, rank);
        // Preserve the source's two separate Crimson kisten rows, rather than inventing an aggregate call.
        assertEquals(2, data.getItems().stream().filter(e -> e.getId() == 33631).count());
    }

    @Test
    public void filtersAndMissingPricesDoNotInventSignals() {
        RaidsWatchlist.Entry scythe = RaidsWatchlist.load().getItems().get(0);
        assertTrue(scythe.matches("  SCYTHE ", "Fall"));
        assertFalse(scythe.matches("scythe", "Rise"));
        assertFalse(scythe.matches("shark", "All calls"));
        assertEquals("Current price unavailable", RaidsWatchlistBox.priceText(null));
        PriceQuote quote = new PriceQuote();
        quote.setLow(500L);
        assertEquals("Current price unavailable", RaidsWatchlistBox.priceText(quote));
        quote.setHigh(0L);
        assertEquals("Current price unavailable", RaidsWatchlistBox.priceText(quote));
        quote.setHigh(1200L);
        assertEquals("Current price: 1.2k GP", RaidsWatchlistBox.priceText(quote));
    }

    @Test
    public void localUiKeepsFiltersAndExpandedEvidenceAcrossPriceUpdates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<String> scanned = new AtomicReference<>();
            RaidsWatchlistBox box = new RaidsWatchlistBox(scanned::set);
            find(box, JTextField.class).get(0).setText("Scythe");
            assertEquals(1, find(box, JToggleButton.class).size());
            find(box, JToggleButton.class).get(0).doClick();
            PriceQuote quote = new PriceQuote();
            quote.setHigh(1200L);
            quote.setHighTime(System.currentTimeMillis() / 1000);
            box.update(Collections.singletonMap(22486, quote));
            assertTrue(find(box, JToggleButton.class).get(0).isSelected());
            assertTrue(find(box, JLabel.class).stream().anyMatch(l -> l.getText().contains("1.2k GP")));
            find(box, JButton.class).stream().filter(b -> "Open in Scanner".equals(b.getText()))
                .findFirst().get().doClick();
            assertEquals("Scythe of Vitur (uncharged)", scanned.get());
            find(box, JComboBox.class).get(0).setSelectedItem("Rise");
            assertTrue(find(box, JToggleButton.class).isEmpty());
            find(box, JComboBox.class).get(0).setSelectedItem("Fall");
            box.update(Collections.emptyMap());
            assertTrue(find(box, JToggleButton.class).get(0).isSelected());
            assertFalse(find(box, JLabel.class).stream().anyMatch(l -> l.getText().contains("1.2k GP")));
        });
    }

    @Test
    public void wikiGateLeavesCommunityReadableAndPersonalViewSelectable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            boolean[] wikiEnabled = {false};
            QuantScapersPlugin plugin = new QuantScapersPlugin() {
                @Override public QuantScapersConfig getConfig() {
                    return new QuantScapersConfig() {
                        @Override public boolean enableWikiMarketData() { return wikiEnabled[0]; }
                        @Override public void setAuditCacheJson(String json) {}
                        @Override public void setVaultJson(String json) {}
                        @Override public void setVaultCollapsed(boolean collapsed) {}
                        @Override public void setSuppressedJson(String json) {}
                        @Override public void setCustomPresetsJson(String json) {}
                    };
                }
            };
            QuantScapersPanel panel = new QuantScapersPanel(plugin);
            panel.showWikiDataGate();
            find(panel, JButton.class).stream().filter(b -> "WATCHLIST".equals(b.getText()))
                .findFirst().get().doClick();
            RaidsWatchlistBox community = find(panel, RaidsWatchlistBox.class).get(0);
            assertTrue(community.isVisible());
            JComboBox<?> views = find(panel, JComboBox.class).stream()
                .filter(c -> "Raids 4 community".equals(c.getSelectedItem())).findFirst().get();
            views.setSelectedItem("My tracked items");
            assertFalse(community.isVisible());
            assertFalse(find(panel, VaultBox.class).get(0).isVisible());
            views.setSelectedItem("Raids 4 community");
            assertTrue(community.isVisible());

            wikiEnabled[0] = true;
            PriceQuote quote = new PriceQuote();
            quote.setHigh(1200L);
            quote.setLow(1000L);
            TrackedTrade tracked = TrackedTrade.builder().id(22486).name("Scythe of Vitur (uncharged)")
                .snapBuy(1000).snapSell(1200).trackedAtMs(System.currentTimeMillis()).build();
            // Raw quotes must reach community cards even when Scanner has no analyzed matches.
            panel.render(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyMap(), Collections.singletonList(tracked), Collections.singletonMap(22486, quote),
                Collections.singletonMap(22486, quote), 0, 0, false, true);
            assertTrue(find(community, JLabel.class).stream().anyMatch(l -> l.getText().contains("1.2k GP")));
            views.setSelectedItem("My tracked items");
            assertTrue(find(panel, VaultBox.class).get(0).isVisible());
            assertFalse(community.isVisible());
            wikiEnabled[0] = false;
            panel.showWikiDataGate();
            views.setSelectedItem("Raids 4 community");
            assertTrue(community.isVisible());
            assertFalse(find(community, JLabel.class).stream().anyMatch(l -> l.getText().contains("1.2k GP")));
            panel.shutdown();
        });
    }

    private static <T> List<T> find(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) found.add(type.cast(component));
            if (component instanceof Container) found.addAll(find((Container) component, type));
        }
        return found;
    }
}
