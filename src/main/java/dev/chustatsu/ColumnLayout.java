package dev.chustatsu;

import dev.chustatsu.stats.StatsView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Keeps OneConfig's ordered, checkable column selection safe for rendering. */
public final class ColumnLayout {
    private static final Set<String> HUD = Set.of("Head", "Dynamic name box", "Static name box", "Rank", "FKDR", "Level", "WLR", "Winstreak",
        "Final kills", "Wins", "Beds", "Guild", "Ping");
    private static final Set<String> TAB = Set.of("Head", "Dynamic name box", "Static name box", "Rank", "FKDR", "Level", "WLR", "Winstreak",
        "Final kills", "Wins", "Beds", "Guild", "Ping", "HP");

    private ColumnLayout() {}

    public static List<String> selected(String[] configured, boolean tab) {
        Set<String> allowed = tab ? TAB : HUD;
        Set<String> seen = new HashSet<>();
        ArrayList<String> columns = new ArrayList<>();
        if (configured != null) {
            for (String name : configured) {
                String migrated = "Name".equals(name) ? "Dynamic name box" : name;
                if (allowed.contains(migrated) && seen.add(migrated)) columns.add(migrated);
            }
        }
        return columns.isEmpty() ? List.of("Dynamic name box") : List.copyOf(columns);
    }

    public static String cell(String column, String visibleName, StatsView stats, int ping, String hp) {
        if (isName(column)) return visibleName;
        if (column.equals("Head")) return "";
        if (column.equals("Ping")) return PingText.format(ping, ChuStatsuConfig.removePingMs);
        if (column.equals("HP")) return hp == null ? "?" : hp;
        if (column.equals("Level") && stats != null && stats.level() != null) return stats.value("Level");
        if (column.equals("Level") && stats != null && status(stats) != null) return "§8-";
        if (stats == null || stats.status() == StatsView.Status.LOADING) return "...";
        if (stats.status() != StatsView.Status.READY) {
            String status = status(stats);
            return status != null ? status : "§c" + stats.message();
        }
        return stats.value(column);
    }

    public static String header(String column) {
        return switch (column) {
            case "Head" -> "";
            case "Dynamic name box", "Static name box" -> "NAME";
            case "Level" -> "LVL";
            case "Winstreak" -> "HWS";
            case "Final kills" -> "FK";
            case "HP" -> "§c❤";
            default -> column.toUpperCase(java.util.Locale.ROOT);
        };
    }

    public static boolean isName(String column) {
        return "Dynamic name box".equals(column) || "Static name box".equals(column);
    }

    public static String status(StatsView stats) {
        if (stats == null) return null;
        return switch (stats.status()) {
            case NICKED -> "§5NICKED";
            case API_DISABLED -> "§cAPI DISABLED";
            case NO_STATS -> "§7NO STATS";
            case ERROR -> rateLimited(stats) ? "§cRATELIMITED"
                : "§c" + (stats.message() == null || stats.message().isBlank()
                    ? "API ERROR" : stats.message().toUpperCase(java.util.Locale.ROOT));
            default -> null;
        };
    }

    public static boolean rateLimited(StatsView stats) {
        return stats != null && stats.status() == StatsView.Status.ERROR
            && "Rate limited".equalsIgnoreCase(stats.message());
    }
}
