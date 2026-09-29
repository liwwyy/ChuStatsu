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
    record SidebarState(boolean bedWars, boolean waiting, boolean inGame) {}

    private static final SidebarState NO_SIDEBAR = new SidebarState(false, false, false);
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
        return active(mc, bedWarsOnly, onlyOnPika, null);
    }

    static boolean active(Minecraft mc, boolean bedWarsOnly, boolean onlyOnPika, SidebarState sidebar) {
        if (mc == null || mc.world == null || mc.player == null || mc.isSingleplayer()) return false;
        if (onlyOnPika && (mc.getCurrentServerEntry() == null || !isPikaAddress(mc.getCurrentServerEntry().ip))) return false;
        return !bedWarsOnly || (sidebar == null ? hasBedWarsSidebar(mc) : sidebar.bedWars());
    }

    public static boolean hasBedWarsSidebar(Minecraft mc) {
        return sidebarState(mc).bedWars();
    }

    public static boolean inWaitingRoom(Minecraft mc) {
        return mc != null && mc.player != null && !mc.isSingleplayer()
            && sidebarState(mc).waiting();
    }

    public static boolean inGame(Minecraft mc) {
        return mc != null && mc.player != null && !mc.isSingleplayer()
            && sidebarState(mc).inGame();
    }

    static SidebarState sidebarState(Minecraft mc) {
        return mc == null || mc.world == null ? NO_SIDEBAR : classifyLines(sidebarLines(mc));
    }

    static SidebarState classifyLines(List<String> lines) {
        boolean bedWars = false, map = false, red = false;
        for (String line : lines) {
            String normalized = plain(line);
            if (!bedWars && containsWithoutWhitespace(normalized, "bedwars")) bedWars = true;
            if (!map && normalized.contains("map:")) map = true;
            if (!red && normalized.contains("red")) red = true;
        }
        return new SidebarState(bedWars, bedWars && map && !red, bedWars && red);
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

    static String plain(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '§' && i + 1 < value.length() && formattingCode(value.charAt(i + 1))) {
                i++;
            } else result.append(current);
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean formattingCode(char code) {
        char lower = code >= 'A' && code <= 'Z' ? (char) (code + ('a' - 'A')) : code;
        return lower >= '0' && lower <= '9' || lower >= 'a' && lower <= 'f'
            || lower >= 'k' && lower <= 'o' || lower == 'r';
    }

    private static boolean containsWithoutWhitespace(String value, String needle) {
        int matched = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == ' ' || current >= '\t' && current <= '\r') continue;
            matched = current == needle.charAt(matched) ? matched + 1
                : current == needle.charAt(0) ? 1 : 0;
            if (matched == needle.length()) return true;
        }
        return false;
    }
}
