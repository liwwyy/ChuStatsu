package dev.chustatsu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class CatboxGalleryTest {
    @Test
    public void downloadsAndReusesCachedImageWhenGalleryIsUnavailable() throws Exception {
        Path cache = Files.createTempDirectory(Path.of("build"), "catbox-test-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger manifests = new AtomicInteger();
        AtomicInteger images = new AtomicInteger();
        server.createContext("/resources/pic.js", exchange -> {
            if (manifests.incrementAndGet() > 1) {
                exchange.sendResponseHeaders(503, -1);
            } else {
                byte[] body = "[\"abc.png\"]".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.createContext("/pictures/qts/abc.png", exchange -> {
            images.incrementAndGet();
            byte[] body = {1, 2, 3};
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            CatboxGallery gallery = new CatboxGallery(URI.create(base + "/resources/pic.js"),
                URI.create(base + "/pictures/qts/"), cache);
            Path first = gallery.next();
            assertEquals("abc.png", first.getFileName().toString());
            assertTrue(Files.isRegularFile(first));
            assertEquals(first, gallery.next());
            assertEquals(1, images.get());
            assertEquals(2, manifests.get());
        } finally {
            server.stop(0);
        }
    }
}
