package com.quantscapers.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.quantscapers.engine.Constants;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Single gateway to prices.runescape.wiki. Every request carries a
 * fixed application User-Agent with the project's GitHub issue tracker as its
 * contact route. Mapping is cached for 24h; 5m and 24h stats are cached for
 * five minutes. Latest prices use the plugin's visible-panel poll interval.
 * Transient errors apply a shared backoff across every endpoint.
 *
 * One instance per plugin; all methods are blocking (call from the plugin's
 * executor, never from the EDT).
 */
@Slf4j
public class WikiPricesClient {

    private static final Type MAPPING_LIST_TYPE = new TypeToken<List<MappingItem>>() {}.getType();

    private final OkHttpClient http;
    private final Gson gson;

    private final AtomicReference<CachedValue<List<MappingItem>>> mappingCache = new AtomicReference<>();
    private final AtomicReference<CachedValue<StatsResponse>> stats24hCache = new AtomicReference<>();
    private final AtomicReference<CachedValue<StatsResponse>> stats5mCache = new AtomicReference<>();
    private final AtomicLong backoffUntilMs = new AtomicLong();
    private final AtomicInteger transientFailureCount = new AtomicInteger();

    private static final long MAPPING_CACHE_MS = 86_400_000L;
    private static final long STATS_CACHE_MS = 300_000L;
    private static final long BASE_BACKOFF_MS = 120_000L;
    private static final long MAX_BACKOFF_MS = 1_800_000L;

    public WikiPricesClient(OkHttpClient http, Gson gson) {
        this.http = http;
        this.gson = gson;
    }

    public List<MappingItem> fetchMapping() throws IOException {
        CachedValue<List<MappingItem>> cached = mappingCache.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAtMs < MAPPING_CACHE_MS) {
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
        CachedValue<StatsResponse> cached = stats24hCache.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAtMs < STATS_CACHE_MS) {
            return cached.value;
        }
        StatsResponse stats = get(Constants.API_BASE + "/24h", StatsResponse.class);
        stats24hCache.set(new CachedValue<>(stats, now));
        return stats;
    }

    /** Returns the five-minute cached value; only hits the network when stale. */
    public StatsResponse fetch5m() throws IOException {
        CachedValue<StatsResponse> cached = stats5mCache.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAtMs < STATS_CACHE_MS) {
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
        long now = System.currentTimeMillis();
        long blockedUntil = backoffUntilMs.get();
        if (now < blockedUntil) {
            throw new IOException("QuantScapers: Wiki API backoff active for " + (blockedUntil - now) + "ms");
        }
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", Constants.WIKI_USER_AGENT)
            .build();
        try (Response response = http.newCall(request).execute()) {
            if (response.code() == 429 || response.code() >= 500) {
                applyBackoff(response, now);
                throw new IOException("QuantScapers: transient response " + response.code() + " for " + url);
            }
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("QuantScapers: bad response " + response.code() + " for " + url);
            }
            transientFailureCount.set(0);
            T parsed = gson.fromJson(response.body().charStream(), type);
            if (parsed == null) {
                throw new IOException("QuantScapers: empty JSON response for " + url);
            }
            return parsed;
        }
    }

    private void applyBackoff(Response response, long nowMs) {
        int failure = transientFailureCount.incrementAndGet();
        int shift = Math.min(failure - 1, 4);
        long exponentialMs = Math.min(BASE_BACKOFF_MS << shift, MAX_BACKOFF_MS);
        long retryAfterMs = parseRetryAfterMs(response.header("Retry-After"), nowMs);
        long delayMs = Math.max(exponentialMs, retryAfterMs);
        backoffUntilMs.accumulateAndGet(nowMs + delayMs, Math::max);
    }

    private static long parseRetryAfterMs(String value, long nowMs) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        String trimmed = value.trim();
        try {
            return Math.max(0, Long.parseLong(trimmed) * 1_000L);
        } catch (NumberFormatException ignored) {
            try {
                long retryAtMs = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
                return Math.max(0, retryAtMs - nowMs);
            } catch (DateTimeParseException ignoredDate) {
                return 0;
            }
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
