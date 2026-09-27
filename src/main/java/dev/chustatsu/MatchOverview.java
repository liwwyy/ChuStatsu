package dev.chustatsu;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts match counters from the server's player-list header and footer. */
public final class MatchOverview {
    private static final Pattern BEDS = Pattern.compile("(?i)\\bbeds?\\s+destroyed\\s*[:：]?\\s*(\\d+)");
    private static final Pattern KILLS = Pattern.compile("(?i)(?<!final )\\bkills?\\s*[:：]?\\s*(\\d+)");
    private static final Pattern FINALS = Pattern.compile("(?i)\\bfinal\\s+kills?\\s*[:：]?\\s*(\\d+)");

    private MatchOverview() {}

    public static String parse(String raw) {
        if (raw == null) return null;
        String plain = raw.replaceAll("(?i)§[0-9a-fk-or]", "").replace('\u00a0', ' ');
        Integer beds = value(BEDS, plain);
        Integer kills = value(KILLS, plain);
        Integer finals = value(FINALS, plain);
        if (beds == null || kills == null || finals == null) return null;
        return "§7Beds Destroyed: §f" + beds + " §8• §7Kills: §f" + kills
            + " §8• §7Final Kills: §f" + finals;
    }

    private static Integer value(Pattern pattern, String input) {
        Matcher match = pattern.matcher(input);
        if (!match.find()) return null;
        try { return Integer.parseInt(match.group(1)); }
        catch (NumberFormatException ignored) { return null; }
    }
}
