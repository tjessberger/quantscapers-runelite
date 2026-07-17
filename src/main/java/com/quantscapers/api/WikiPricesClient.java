package com.quantscapers.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.quantscapers.engine.Constants;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Single gateway to prices.runescape.wiki. Every request carries a
 * User-Agent built from the user's configured contact email (re-read live
 * via userAgentSupplier so a settings change takes effect on the next
 * request, not just the next plugin restart). Mapping is cached in memory
 * for 24h (it changes rarely); 5m stats are cached for 60s since the
 * upstream data itself only refreshes every 5 minutes. latest/24h are never
 * cached here — the plugin's own poll interval is the cache.
 *
 * One instance per plugin; all methods are blocking (call from the plugin's
 * executor, never from the EDT).
 */
@Slf4j
public class WikiPricesClient {

    private static final Type MAPPING_LIST_TYPE = new TypeToken<List<MappingItem>>() {}.getType();

    private final OkHttpClient http;
    private final Gson gson;
    private final Supplier<String> userAgentSupplier;

    private final AtomicReference<CachedValue<List<MappingItem>>> mappingCache = new AtomicReference<>();
    private final AtomicReference<CachedValue<StatsResponse>> stats5mCache = new AtomicReference<>();

    public WikiPricesClient(OkHttpClient http, Gson gson, Supplier<String> userAgentSupplier) {
        this.http = http;
        this.gson = gson;
        this.userAgentSupplier = userAgentSupplier;
    }

    public List<MappingItem> fetchMapping() throws IOException {
        CachedValue<List<MappingItem>> cached = mappingCache.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAtMs < 86_400_000L) {
            return cached.value;
        }
        List<MappingItem> mapping = get(Constants.API_BASE + "/mapping", MAPPING_LIST_TYPE);
        mappingCache.set(new CachedValue<>(mapping, now));
        return mapping;
    }

    public LatestResponse fetchLatest() throws IOException {
        return get(Constants.API_BASE + "/latest", LatestResponse.class);
    }

    public StatsResponse fetch24h() throws IOException {
        return get(Constants.API_BASE + "/24h", StatsResponse.class);
    }

    /** Returns the 60s-cached value; only hits the network when the cache is stale. */
    public StatsResponse fetch5m() throws IOException {
        CachedValue<StatsResponse> cached = stats5mCache.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAtMs < 60_000L) {
            return cached.value;
        }
        StatsResponse stats = get(Constants.API_BASE + "/5m", StatsResponse.class);
        stats5mCache.set(new CachedValue<>(stats, now));
        return stats;
    }

    public TimeseriesResponse fetchTimeseries(int itemId) throws IOException {
        return get(Constants.API_BASE + "/timeseries?timestep=1h&id=" + itemId, TimeseriesResponse.class);
    }

    private <T> T get(String url, Type type) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", userAgentSupplier.get())
            .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("QuantScapers: bad response " + response.code() + " for " + url);
            }
            return gson.fromJson(response.body().charStream(), type);
        }
    }

    private <T> T get(String url, Class<T> clazz) throws IOException {
        return get(url, (Type) clazz);
    }

    private static final class CachedValue<T> {
        final T value;
        final long fetchedAtMs;

        CachedValue(T value, long fetchedAtMs) {
            this.value = value;
            this.fetchedAtMs = fetchedAtMs;
        }
    }
}
