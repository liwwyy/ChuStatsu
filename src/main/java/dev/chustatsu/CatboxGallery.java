package dev.chustatsu;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded Catbox image source. Network access is only used from the image worker. */
final class CatboxGallery {
    private static final int MANIFEST_LIMIT = 512 * 1024;
    private static final int IMAGE_LIMIT = 8 * 1024 * 1024;
    private static final long CACHE_LIMIT = 8L * 1024 * 1024;
    private static final Pattern PNG = Pattern.compile("[A-Za-z0-9_-]+\\.png");
    private final URI manifest;
    private final URI pictures;
    private final Path cache;
    private String previous;

    CatboxGallery(Path cache) {
        this(URI.create("https://catbox.moe/resources/pic.js"),
            URI.create("https://catbox.moe/pictures/qts/"), cache);
    }

    CatboxGallery(URI manifest, URI pictures, Path cache) {
        this.manifest = manifest;
        this.pictures = pictures;
        this.cache = cache;
    }

    Path next() throws IOException {
        Files.createDirectories(cache);
        List<String> candidates = new ArrayList<>();
        try {
            Matcher found = PNG.matcher(new String(download(manifest, MANIFEST_LIMIT), StandardCharsets.UTF_8));
            Set<String> unique = new HashSet<>();
            while (found.find()) if (unique.add(found.group())) candidates.add(found.group());
        } catch (IOException error) {
            // A previously downloaded image remains usable while Catbox is unavailable.
        }
        try (var files = Files.list(cache)) {
            Set<String> known = new HashSet<>(candidates);
            files.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> PNG.matcher(name).matches())
                .filter(known::add).forEach(candidates::add);
        }
        if (candidates.isEmpty()) throw new IOException("No Catbox images available");
        List<String> alternatives = candidates.stream().filter(name -> !name.equals(previous)).toList();
        if (!alternatives.isEmpty()) candidates = alternatives;
        List<String> shuffled = new ArrayList<>(candidates);
        java.util.Collections.shuffle(shuffled, ThreadLocalRandom.current());
        IOException failure = new IOException("No Catbox images available");
        for (String chosen : shuffled) {
            Path target = cache.resolve(chosen);
            try {
                if (!Files.isRegularFile(target) || Files.size(target) > IMAGE_LIMIT) {
                    byte[] bytes = download(pictures.resolve(chosen), IMAGE_LIMIT);
                    Path temporary = Files.createTempFile(cache, "download-", ".part");
                    try {
                        Files.write(temporary, bytes);
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                }
                previous = chosen;
                trimCache(chosen);
                return target;
            } catch (IOException error) {
                failure = error;
            }
        }
        throw failure;
    }

    private void trimCache(String keep) throws IOException {
        try (var files = Files.list(cache)) {
            List<Path> cached = files.filter(Files::isRegularFile)
                .filter(path -> PNG.matcher(path.getFileName().toString()).matches())
                .sorted((a, b) -> {
                    try { return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b)); }
                    catch (IOException ignored) { return 0; }
                }).toList();
            long total = 0;
            for (Path path : cached) total += Files.size(path);
            for (Path path : cached) {
                if (total <= CACHE_LIMIT) break;
                if (path.getFileName().toString().equals(keep)) continue;
                long size = Files.size(path);
                Files.deleteIfExists(path);
                total -= size;
            }
        }
    }

    private static byte[] download(URI uri, int limit) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(8_000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > limit)
                throw new IOException("Catbox response rejected: " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(limit + 1);
                if (bytes.length > limit) throw new IOException("Catbox response exceeds size limit");
                return bytes;
            }
        } finally {
            connection.disconnect();
        }
    }
}
