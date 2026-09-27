package dev.chustatsu.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class PikaStatsClientTest {
    @Test
    public void readsThePlayersEntryRatherThanTheLeaderboardTotal() {
        String body = "{" +
            "\"Wins\":{\"metadata\":{\"total\":5000},\"entries\":[{\"id\":\"Other\",\"value\":\"99\"},{\"id\":\"Alice\",\"value\":\"5\"}]}," +
            "\"Losses\":{\"entries\":[{\"id\":\"Alice\",\"value\":\"2\"}]}," +
            "\"Final kills\":{\"entries\":[{\"id\":\"Alice\",\"value\":\"12\"}]}," +
            "\"Final deaths\":{\"entries\":[{\"id\":\"Alice\",\"value\":\"3\"}]}" +
            "}";
        StatsView view = PikaStatsClient.parseLeaderboard("Alice", 10, JsonParser.parseString(body).getAsJsonObject());
        assertEquals(StatsView.Status.READY, view.status());
        assertEquals(Integer.valueOf(5), view.wins());
        assertEquals("4.00", view.fkdr());
        assertEquals("2.50", view.wlr());
    }

    @Test
    public void nullEntriesMeanNoRecordedStats() {
        StatsView view = PikaStatsClient.parseLeaderboard("Alice", null,
            JsonParser.parseString("{\"Wins\":{\"entries\":null}}").getAsJsonObject());
        assertEquals(StatsView.Status.NO_STATS, view.status());
    }

    @Test
    public void retryAfterRespectsServerHeaderAndSafeBounds() {
        assertEquals(10_000L, PikaStatsClient.retryAfterMillis(null, 0));
        assertEquals(2_000L, PikaStatsClient.retryAfterMillis("2", 0));
        assertEquals(120_000L, PikaStatsClient.retryAfterMillis("120", 0));
        assertEquals(120_000L, PikaStatsClient.retryAfterMillis("999", 0));
    }

    @Test
    public void rateLimitStopsFollowUpRequests() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/api/profile/", exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().add("Retry-After", "120");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        PikaStatsClient client = new PikaStatsClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api/profile/");
        try {
            client.get("Alice", 0, 0, 300);
            long deadline = System.currentTimeMillis() + 5000;
            StatsView result;
            do {
                Thread.sleep(20);
                result = client.get("Alice", 0, 0, 300);
            } while (result.status() == StatsView.Status.LOADING && System.currentTimeMillis() < deadline);
            assertEquals(StatsView.Status.ERROR, result.status());
            client.get("Bob", 0, 0, 300);
            assertEquals(1, calls.get());
        } finally {
            client.close();
            server.stop(0);
        }
    }

    @Test
    public void retriesAtCooldownExpiryInsteadOfCachingRateLimitForThirtySeconds() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/profile/", exchange -> {
            int count = requests.incrementAndGet();
            if (count == 1) {
                exchange.getResponseHeaders().add("Retry-After", "1");
                exchange.sendResponseHeaders(429, -1);
            } else {
                byte[] body = (exchange.getRequestURI().getQuery() == null
                    ? "{\"rank\":{\"level\":12}}"
                    : "{\"Wins\":{\"entries\":[{\"id\":\"Alice\",\"value\":3}]}}")
                    .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            }
            exchange.close();
        });
        server.start();
        PikaStatsClient client = new PikaStatsClient("http://127.0.0.1:"
            + server.getAddress().getPort() + "/api/profile/");
        try {
            assertEquals(StatsView.Status.ERROR, await(client, "Alice", 0).status());
            Thread.sleep(1_100L);
            StatsView recovered = await(client, "Alice", 0);
            assertEquals("requests=" + requests.get() + " message=" + recovered.message(),
                StatsView.Status.READY, recovered.status());
            assertEquals(Integer.valueOf(12), recovered.level());
            assertEquals(3, requests.get());
        } finally {
            client.close();
            server.stop(0);
        }
    }

    @Test
    public void sharesThePacedProfileResponseWithFriendsAcrossModes() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger profiles = new AtomicInteger();
        AtomicInteger leaderboards = new AtomicInteger();
        server.createContext("/api/profile/", exchange -> {
            String body;
            if (exchange.getRequestURI().getQuery() == null) {
                profiles.incrementAndGet();
                body = "{\"username\":\"Alice\",\"rank\":{\"level\":14},\"friends\":[{\"username\":\"Bob\"}]}";
            } else {
                leaderboards.incrementAndGet();
                body = "{\"Wins\":{\"entries\":[{\"id\":\"Alice\",\"value\":5}]}}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        PikaStatsClient client = new PikaStatsClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api/profile/");
        try {
            assertEquals(StatsView.Status.READY, await(client, "Alice", 0).status());
            assertEquals(java.util.Set.of("bob"), client.friendsFor("Alice"));
            assertEquals(StatsView.Status.READY, await(client, "Alice", 1).status());
            assertEquals(1, profiles.get());
            assertEquals(2, leaderboards.get());
        } finally {
            client.close();
            server.stop(0);
        }
    }

    @Test
    public void keepsProfileLevelWhenLeaderboardIsPrivate() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/profile/", exchange -> {
            if (exchange.getRequestURI().getQuery() != null) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                byte[] body = "{\"username\":\"Alice\",\"rank\":{\"level\":42}}"
                    .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            }
            exchange.close();
        });
        server.start();
        PikaStatsClient client = new PikaStatsClient("http://127.0.0.1:"
            + server.getAddress().getPort() + "/api/profile/");
        try {
            StatsView result = await(client, "Alice", 0);
            assertEquals(StatsView.Status.API_DISABLED, result.status());
            assertEquals(Integer.valueOf(42), result.level());
        } finally {
            client.close();
            server.stop(0);
        }
    }

    @Test
    public void primesSeveralPlayersWithoutSerialRequestDelay() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/profile/", exchange -> {
            requests.incrementAndGet();
            byte[] body = (exchange.getRequestURI().getQuery() == null
                ? "{\"rank\":{\"level\":10}}"
                : "{\"Wins\":{\"entries\":[]}}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        PikaStatsClient client = new PikaStatsClient("http://127.0.0.1:"
            + server.getAddress().getPort() + "/api/profile/");
        try {
            String[] names = {"Alice", "Bobby", "Carol", "David"};
            long started = System.currentTimeMillis();
            for (String name : names) client.get(name, 0, 0, 300);
            for (String name : names) assertEquals(StatsView.Status.NO_STATS, await(client, name, 0).status());
            assertEquals(8, requests.get());
            assertTrue("Four profiles should finish within seven seconds",
                System.currentTimeMillis() - started < 7_000L);
        } finally {
            client.close();
            server.stop(0);
        }
    }

    private static StatsView await(PikaStatsClient client, String username, int mode) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 7000;
        StatsView result;
        do {
            result = client.get(username, mode, 0, 300);
            if (result.status() != StatsView.Status.LOADING) return result;
            Thread.sleep(20);
        } while (System.currentTimeMillis() < deadline);
        return result;
    }
}
