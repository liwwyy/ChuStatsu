package dev.chustatsu;

/** Compact latency text using the same visible tiers as PikaStats. */
final class PingText {
    private PingText() {}

    static String format(int ping, boolean removeMs) {
        if (ping == 0) return "§8?";
        if (ping < 0) return "§8-";
        String color = ping <= 90 ? "§a" : ping <= 140 ? "§e"
            : ping <= 200 ? "§6" : ping < 400 ? "§c" : "§4";
        return color + ping + (removeMs ? "" : "ms");
    }
}
