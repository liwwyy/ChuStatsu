package dev.chustatsu;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.team.Team;

/** Reads only client-visible server and sidebar state. */
public final class PikaContext {
    private PikaContext() {}

    public static boolean isPikaAddress(String address) {
        if (address == null) return false;
        String host = address.toLowerCase(Locale.ROOT).trim().split(":", 2)[0];
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.equals("pika-network.net") || host.endsWith(".pika-network.net")
            || host.equals("pika.host") || host.endsWith(".pika.host");
    }

    public static boolean active(Minecraft mc, boolean bedWarsOnly) {
        return active(mc, bedWarsOnly, true);
    }

    public static boolean active(Minecraft mc, boolean bedWarsOnly, boolean onlyOnPika) {
        if (mc == null || mc.world == null || mc.player == null || mc.isSingleplayer()) return false;
        if (onlyOnPika && (mc.getCurrentServerEntry() == null || !isPikaAddress(mc.getCurrentServerEntry().ip))) return false;
        return !bedWarsOnly || hasBedWarsSidebar(mc);
    }

    public static boolean hasBedWarsSidebar(Minecraft mc) {
        if (mc == null || mc.world == null) return false;
        for (String line : sidebarLines(mc)) {
            if (plain(line).replaceAll("\\s+", "").contains("bedwars")) return true;
        }
        return false;
    }

    public static boolean inWaitingRoom(Minecraft mc) {
        if (!active(mc, true, false)) return false;
        boolean map = false;
        for (String line : sidebarLines(mc)) {
            String normalized = plain(line);
            if (normalized.contains("red")) return false;
            if (normalized.contains("map:")) map = true;
        }
        return map;
    }

    public static boolean inGame(Minecraft mc) {
        if (!active(mc, true, false)) return false;
        for (String line : sidebarLines(mc)) {
            if (plain(line).contains("red")) return true;
        }
        return false;
    }

    public static List<String> sidebarLines(Minecraft mc) {
        if (mc == null || mc.world == null) return List.of();
        Scoreboard board = mc.world.getScoreboard();
        if (board == null) return List.of();
        ScoreboardObjective sidebar = null;
        if (mc.player != null) {
            Team localTeam = board.getTeamOfMember(mc.player.getName());
            if (localTeam != null && localTeam.getColor() != null && localTeam.getColor().isColor()) {
                sidebar = board.getDisplayObjective(3 + localTeam.getColor().getId());
            }
        }
        if (sidebar == null) sidebar = board.getDisplayObjective(1);
        if (sidebar == null) return List.of();
        ArrayList<String> lines = new ArrayList<>();
        lines.add(sidebar.getDisplayName());
        for (ScoreboardScore score : board.getScores(sidebar)) {
            String owner = score.getOwner();
            if (owner == null || owner.startsWith("#")) continue;
            Team team = board.getTeamOfMember(owner);
            lines.add(team == null ? owner : team.getMemberDisplayName(owner));
        }
        return lines;
    }

    private static String plain(String value) {
        return value == null ? "" : value.replaceAll("(?i)§[0-9a-fk-or]", "").toLowerCase(Locale.ROOT);
    }
}
