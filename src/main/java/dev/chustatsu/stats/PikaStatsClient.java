package dev.chustatsu.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import dev.chustatsu.ChuStatsu;
import dev.chustatsu.ChuStatsuConfig;

/** Bounded, asynchronous reader for the public PikaNetwork profile API. */
public final class PikaStatsClient {
    private static final String BASE = "https://stats.pika-network.net/api/profile/";
    private static final String[] MODES = {"ALL_MODES", "SOLO", "DOUBLES", "QUAD"};
    private static final String[] PERIODS = {"total", "weekly", "monthly", "yearly"};
    private static final long REQUEST_SPACING_MS = 250L;
    private final ExecutorService worker = Executors.newFixedThreadPool(4, task -> {
        Thread thread = new Thread(task, "ChuStatsu Pika API");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final String base;
    private final BooleanSupplier debugLogging;
    private final Map<String, ProfileData> profiles = new ConcurrentHashMap<>();
    private final AtomicInteger queued = new AtomicInteger();
    private volatile long blockedUntil;
    private final AtomicLong nextRequestAt = new AtomicLong();

    private record Entry(StatsView view, long fetchedAt, CompletableFuture<StatsView> pending) {}
    private record ProfileData(Integer level, String guild, String rank, Set<String> friends, long fetchedAt) {}

    public PikaStatsClient() { this(BASE, () -> ChuStatsuConfig.debugMode); }

    PikaStatsClient(String base) { this(base, () -> false); }

    private PikaStatsClient(String base, BooleanSupplier debugLogging) {
        this.base = base;
        this.debugLogging = debugLogging;
    }

    public StatsView get(String username, int mode, int period, int ttlSeconds) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) {
            return StatsView.error(String.valueOf(username), StatsView.Status.ERROR, "Invalid name");
        }
        int m = Math.max(0, Math.min(mode, MODES.length - 1));
        int p = Math.max(0, Math.min(period, PERIODS.length - 1));
        String key = username.toLowerCase(Locale.ROOT) + ':' + m + ':' + p;
        long now = System.currentTimeMillis();
        Entry existing = cache.get(key);
        long lifetime = existing != null && existing.view != null && existing.view.status() == StatsView.Status.ERROR
            ? (rateLimited(existing.view) ? 0L : 30_000L)
            : Math.max(300, ttlSeconds) * 1000L;
        if (existing != null && existing.view != null && now - existing.fetchedAt < lifetime) {
            return existing.view;
        }
        if (existing != null && existing.pending != null) {
            return existing.view == null || rateLimited(existing.view)
                ? StatsView.loading(username) : existing.view;
        }
        if (now < blockedUntil || queued.get() >= 40) {
            return existing == null || existing.view == null ? StatsView.loading(username) : existing.view;
        }
        if (cache.size() >= 256) cache.entrySet().removeIf(e -> e.getValue().pending == null && now - e.getValue().fetchedAt > 600_000L);
        queued.incrementAndGet();
        CompletableFuture<StatsView> future = new CompletableFuture<>();
        // Publish the pending entry before the worker can finish a fast local or cached response.
        cache.put(key, new Entry(existing == null ? null : existing.view, now, future));
        future.whenComplete((result, error) -> {
            StatsView next = error == null ? result : StatsView.error(username, StatsView.Status.ERROR, "API unavailable");
            if (next.status() == StatsView.Status.ERROR && existing != null && existing.view != null
                && existing.view.status() == StatsView.Status.READY) next = existing.view;
            cache.put(key, new Entry(next, System.currentTimeMillis(), null));
        });
        worker.execute(() -> {
            try {
                future.complete(System.currentTimeMillis() < blockedUntil
                    ? StatsView.error(username, StatsView.Status.ERROR, "Rate limited") : fetch(username, m, p));
            } catch (Throwable error) {
                future.completeExceptionally(error);
            } finally {
                queued.decrementAndGet();
            }
        });
        return existing == null || existing.view == null || rateLimited(existing.view)
            ? StatsView.loading(username) : existing.view;
    }

    private static boolean rateLimited(StatsView view) {
        return view.status() == StatsView.Status.ERROR
            && "Rate limited".equalsIgnoreCase(view.message());
    }

    public void close() {
        worker.shutdownNow();
    }

    public Set<String> friendsFor(String username) {
        if (username == null) return null;
        ProfileData profile = profiles.get(username.toLowerCase(Locale.ROOT));
        return profile == null ? null : profile.friends;
    }

    private StatsView fetch(String username, int mode, int period) {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String profileUrl = base + encoded;
        ProfileData profileData = profiles.get(username.toLowerCase(Locale.ROOT));
        try {
            if (profileData == null || System.currentTimeMillis() - profileData.fetchedAt > 600_000L) {
                JsonObject profile = read(profileUrl);
                profileData = parseProfile(profile);
                profiles.put(username.toLowerCase(Locale.ROOT), profileData);
            }
        } catch (ApiStatus e) {
            if (e.code == 400 || e.code == 404) return StatsView.error(username, StatsView.Status.NICKED, "Nicked");
            if (e.code == 429) return StatsView.error(username, StatsView.Status.ERROR, "Rate limited");
        } catch (IOException | RuntimeException e) {
            // A profile failure does not prevent reading leaderboard statistics.
        }
        try {
            JsonObject data = read(profileUrl + "/leaderboard?type=bedwars&interval=" + PERIODS[period] + "&mode=" + MODES[mode]);
            StatsView view = parseLeaderboard(username, profileData, data);
            return view;
        } catch (ApiStatus e) {
            if (e.code == 204) return new StatsView(username, StatsView.Status.API_DISABLED,
                profileData == null ? null : profileData.level, null, null, null, null,
                null, null, null, null, "API disabled");
            if (e.code == 400 || e.code == 404) return StatsView.error(username, StatsView.Status.NO_STATS, "No stats");
            return StatsView.error(username, StatsView.Status.ERROR, "API HTTP " + e.code);
        } catch (IOException | RuntimeException e) {
            return StatsView.error(username, StatsView.Status.ERROR, "API unavailable");
        }
    }

    static StatsView parseLeaderboard(String username, Integer level, JsonObject data) {
        return parseLeaderboard(username, new ProfileData(level, null, null, Set.of(), 0), data);
    }

    private static StatsView parseLeaderboard(String username, ProfileData profile, JsonObject data) {
        Integer wins = stat(data, username, "Wins");
        Integer losses = stat(data, username, "Losses");
        Integer kills = stat(data, username, "Final kills");
        Integer deaths = stat(data, username, "Final deaths");
        Integer winstreak = stat(data, username, "Highest winstreak reached");
        Integer beds = stat(data, username, "Beds destroyed");
        if (beds == null) beds = stat(data, username, "Beds broken");
        StatsView.Status status = wins == null && losses == null && kills == null && deaths == null
            && winstreak == null && beds == null
            ? StatsView.Status.NO_STATS : StatsView.Status.READY;
        return new StatsView(username, status, profile == null ? null : profile.level,
            wins, losses, kills, deaths, winstreak, beds,
            profile == null ? null : profile.guild, profile == null ? null : profile.rank,
            status == StatsView.Status.READY ? "" : "No stats");
    }

    private static ProfileData parseProfile(JsonObject profile) {
        JsonObject levelData = object(profile, "rank");
        JsonObject clan = object(profile, "clan");
        String guild = clan != null && clan.has("name") ? string(clan.get("name")) : null;
        Set<String> friends = new HashSet<>();
        JsonArray friendEntries = profile.has("friends") && profile.get("friends").isJsonArray()
            ? profile.getAsJsonArray("friends") : null;
        if (friendEntries != null) {
            for (JsonElement item : friendEntries) {
                if (!item.isJsonObject()) continue;
                String name = string(item.getAsJsonObject().get("username"));
                if (name.matches("[A-Za-z0-9_]{3,16}")) friends.add(name.toLowerCase(Locale.ROOT));
            }
        }
        String rank = null;
        JsonArray ranks = profile.has("ranks") && profile.get("ranks").isJsonArray() ? profile.getAsJsonArray("ranks") : null;
        if (ranks != null) {
            for (JsonElement item : ranks) {
                if (!item.isJsonObject()) continue;
                JsonObject candidate = item.getAsJsonObject();
                String server = string(candidate.get("server")).toLowerCase(Locale.ROOT);
                if (server.matches("games|minigames|bedwars|global|network|all")) {
                    rank = string(candidate.has("displayName") ? candidate.get("displayName") : candidate.get("name"));
                    break;
                }
            }
        }
        return new ProfileData(levelData == null ? null : integer(levelData.get("level")), guild, rank,
            Set.copyOf(friends),
            System.currentTimeMillis());
    }

    private static Integer stat(JsonObject data, String username, String label) {
        JsonObject group = object(data, label);
        if (group == null) return null;
        JsonArray entries = group.has("entries") && group.get("entries").isJsonArray() ? group.getAsJsonArray("entries") : null;
        if (entries == null) return null;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            if (username.equalsIgnoreCase(string(row.get("id")))) return integer(row.get("value"));
        }
        return null;
    }

    private static String string(JsonElement value) {
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static Integer integer(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        try {
            double n = value.getAsDouble();
            return n < 0 || !Double.isFinite(n) ? null : (int) Math.round(n);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject object(JsonObject data, String key) {
        return data != null && data.has(key) && data.get(key).isJsonObject() ? data.getAsJsonObject(key) : null;
    }

    private JsonObject read(String address) throws IOException {
        long now = System.currentTimeMillis();
        if (now < blockedUntil) throw new ApiStatus(429);
        long reservedAt = nextRequestAt.getAndUpdate(previous ->
            Math.max(previous, now) + REQUEST_SPACING_MS);
        long wait = Math.max(0L, reservedAt - now);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Pika request interrupted", e);
            }
        }
        if (System.currentTimeMillis() < blockedUntil) throw new ApiStatus(429);
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ChuStatsu/0.1 Minecraft-1.8.9");
            int code = connection.getResponseCode();
            if (code == 429) {
                long receivedAt = System.currentTimeMillis();
                long delay = retryAfterMillis(connection.getHeaderField("Retry-After"), receivedAt);
                blockedUntil = Math.max(blockedUntil, receivedAt + delay);
                if (debugLogging.getAsBoolean())
                    ChuStatsu.LOGGER.warn("Pika API rate limited (HTTP 429); pausing requests for {} seconds",
                        Math.max(1L, (blockedUntil - receivedAt + 999L) / 1000L));
                throw new ApiStatus(code);
            }
            if (code != 200) throw new ApiStatus(code);
            try (InputStream stream = connection.getInputStream()) {
                byte[] body = stream.readNBytes(1_048_577);
                if (body.length > 1_048_576) throw new IOException("Pika response exceeds 1 MiB");
                JsonElement json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
                if (!json.isJsonObject()) throw new IOException("Pika response is not an object");
                return json.getAsJsonObject();
            }
        } finally {
            connection.disconnect();
        }
    }

    static long retryAfterMillis(String value, long now) {
        if (value != null) {
            try {
                return Math.max(1_000L, Math.min(120_000L, Long.parseLong(value.trim()) * 1000L));
            } catch (NumberFormatException ignored) {
                try {
                    long deadline = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
                    return Math.max(1_000L, Math.min(120_000L, deadline - now));
                } catch (RuntimeException ignoredDate) {
                    // Fall through to the conservative default.
                }
            }
        }
        return 10_000L;
    }

    private static final class ApiStatus extends IOException {
        final int code;
        ApiStatus(int code) { super("HTTP " + code); this.code = code; }
    }
}
