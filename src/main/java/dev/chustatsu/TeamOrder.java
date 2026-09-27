package dev.chustatsu;

import java.util.Locale;

/** Fixed BedWars team order, independent of server scoreboard registration names. */
final class TeamOrder {
    private TeamOrder() {}

    static int rank(String color, String teamName) {
        String code = color == null ? "" : color.toLowerCase(Locale.ROOT);
        if (code.length() >= 2 && code.charAt(0) == '§') {
            return switch (code.charAt(1)) {
                case 'c', '4' -> 0; // Red
                case '9', '1' -> 1; // Blue
                case 'a', '2' -> 2; // Green
                case 'e', '6' -> 3; // Yellow / orange
                case 'b', '3' -> 4; // Aqua
                case 'f' -> 5;      // White
                case 'd', '5' -> 6; // Pink
                case '7', '8', '0' -> 7; // Gray
                default -> namedRank(teamName);
            };
        }
        return namedRank(teamName);
    }

    private static int namedRank(String teamName) {
        String name = teamName == null ? "" : teamName.toLowerCase(Locale.ROOT);
        if (name.contains("red")) return 0;
        if (name.contains("blue")) return 1;
        if (name.contains("green")) return 2;
        if (name.contains("yellow") || name.contains("orange")) return 3;
        if (name.contains("aqua") || name.contains("cyan")) return 4;
        if (name.contains("white")) return 5;
        if (name.contains("pink") || name.contains("magenta")) return 6;
        if (name.contains("gray") || name.contains("grey")) return 7;
        return 8;
    }

}
