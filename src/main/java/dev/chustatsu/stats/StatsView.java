package dev.chustatsu.stats;

/** Immutable display result. Null numbers mean that the API supplied no value. */
public record StatsView(String username, Status status, Integer level, Integer wins,
                        Integer losses, Integer finalKills, Integer finalDeaths,
                        Integer bestWinstreak, Integer beds, String guild, String rank,
                        String message) {
    public enum Status { LOADING, READY, NO_STATS, NICKED, API_DISABLED, ERROR }

    public static StatsView loading(String username) {
        return new StatsView(username, Status.LOADING, null, null, null, null, null,
            null, null, null, null, "Loading");
    }

    public static StatsView error(String username, Status status, String message) {
        return new StatsView(username, status, null, null, null, null, null,
            null, null, null, null, message);
    }

    public String fkdr() {
        if (finalKills == null) return "-";
        return String.format(java.util.Locale.ROOT, "%.2f", finalKills / (double) Math.max(1, finalDeaths == null ? 0 : finalDeaths));
    }

    public String wlr() {
        if (wins == null) return "-";
        return String.format(java.util.Locale.ROOT, "%.2f", wins / (double) Math.max(1, losses == null ? 0 : losses));
    }

    public String value(String column) {
        return switch (column) {
            case "FKDR" -> ratio(finalKills, finalDeaths, new double[] {1, 2, 3, 5, 7.5, 10});
            case "Level" -> level == null ? "-" : levelColor(level) + level;
            case "WLR" -> ratio(wins, losses, new double[] {.5, 1, 2, 5, 7, 10});
            case "Winstreak" -> graded(bestWinstreak, new double[] {2, 5, 10, 25, 50, 100});
            case "Final kills" -> count(finalKills);
            case "Wins" -> count(wins);
            case "Beds" -> count(beds);
            case "Guild" -> guild == null || guild.isBlank() ? "-" : guild;
            case "Rank" -> rank == null || rank.isBlank() ? "-" : rank;
            default -> "-";
        };
    }

    public String nametagValue(String column) {
        return switch (column) {
            case "Final kills" -> graded(finalKills, new double[] {100, 500, 1000, 5000, 10000, 25000});
            case "Wins" -> graded(wins, new double[] {10, 50, 100, 500, 1000, 5000});
            case "Beds" -> graded(beds, new double[] {50, 100, 500, 1000, 5000, 10000});
            default -> value(column);
        };
    }

    private static String levelColor(int level) {
        String[] colors = {"§7", "§f", "§a", "§b", "§9", "§5", "§d", "§e", "§6", "§c", "§4"};
        return colors[Math.max(0, Math.min(colors.length - 1, level / 10))];
    }

    private static String ratio(Integer numerator, Integer denominator, double[] bands) {
        if (numerator == null) return "§8?";
        double value = numerator / (double) Math.max(1, denominator == null ? 0 : denominator);
        return grade(value, bands) + String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String graded(Integer number, double[] bands) {
        return number == null ? "§8?" : grade(number, bands) + number;
    }

    private static String count(Integer number) {
        return number == null ? "§8?" : "§7" + number;
    }

    private static String grade(double value, double[] thresholds) {
        String[] colors = {"§7", "§a", "§e", "§6", "§c", "§4", "§d"};
        int index = 0;
        for (double threshold : thresholds) if (value >= threshold) index++;
        return colors[index];
    }

    public Double sortValue(int index) {
        if (status != Status.READY) return null;
        return switch (index) {
            case 0 -> finalKills == null ? null : finalKills / (double) Math.max(1, finalDeaths == null ? 0 : finalDeaths);
            case 1 -> wins == null ? null : wins / (double) Math.max(1, losses == null ? 0 : losses);
            case 2 -> bestWinstreak == null ? null : bestWinstreak.doubleValue();
            case 3 -> finalKills == null ? null : finalKills.doubleValue();
            case 4 -> wins == null ? null : wins.doubleValue();
            case 5 -> beds == null ? null : beds.doubleValue();
            case 6 -> level == null ? null : level.doubleValue();
            default -> null;
        };
    }
}
