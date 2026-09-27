package dev.chustatsu;

import java.util.Locale;

/** Finds the server's bold rank prefix without depending on a fixed rank name list. */
final class RankLabel {
    private RankLabel() {}

    static boolean hasBoldPrefix(String formatted, String username) {
        if (formatted == null || username == null) return false;
        int at = formatted.toLowerCase(Locale.ROOT).lastIndexOf(username.toLowerCase(Locale.ROOT));
        return at >= 0 && formatted.substring(0, at).toLowerCase(Locale.ROOT).contains("§l");
    }

    static String besideName(String formatted, String username, String apiRank) {
        String prefix = "";
        if (formatted != null && username != null) {
            int at = formatted.toLowerCase(Locale.ROOT).lastIndexOf(username.toLowerCase(Locale.ROOT));
            if (at >= 0) prefix = formatted.substring(0, at);
        }
        int bold = prefix.lastIndexOf("§l");
        if (bold < 0) bold = prefix.lastIndexOf("§L");
        String serverRank = bold < 0 ? "" : clean(prefix.substring(bold + 2));
        String rank = serverRank;
        if (rank.isBlank()) return "";
        String color = bold >= 0 ? colorBefore(prefix, bold) : "§f";
        return " §7" + color + rank;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("(?i)§[0-9A-FK-OR]", "")
            .replace('[', ' ').replace(']', ' ').trim();
    }

    private static String colorBefore(String text, int limit) {
        String color = "§f";
        for (int i = 0; i + 1 < limit; i++) {
            if (text.charAt(i) == '§') {
                char code = Character.toLowerCase(text.charAt(++i));
                if ("0123456789abcdef".indexOf(code) >= 0) color = "§" + code;
            }
        }
        return color;
    }
}
