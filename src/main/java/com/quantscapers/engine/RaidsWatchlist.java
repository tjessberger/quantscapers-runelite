package com.quantscapers.engine;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import lombok.Data;

/** Editorial snapshot bundled with the plugin; never fetches website or social data. */
@Data
public class RaidsWatchlist {
    private String sourceUrl;
    private String capturedAt;
    private String evidenceUpdated;
    private String title;
    private String launch;
    private String method;
    private List<Entry> items;
    private List<String> unreleased;

    public static RaidsWatchlist load() {
        InputStream stream = RaidsWatchlist.class.getResourceAsStream("/com/quantscapers/raids4-watchlist.json");
        if (stream == null) throw new IllegalStateException("Missing Raids 4 watchlist snapshot");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, RaidsWatchlist.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Raids 4 watchlist snapshot", e);
        }
    }

    @Data
    public static class Entry {
        private int id;
        private int rank;
        private String name;
        private String url;
        private String call;
        private int rise;
        private int fall;
        private int other;
        private int mentions;
        private int upvotes;
        private String riseReason;
        private String riseUrl;
        private String fallReason;
        private String fallUrl;

        public boolean matches(String search, String selectedCall) {
            return name.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT))
                && ("All calls".equals(selectedCall) || call.equals(selectedCall));
        }
    }
}
