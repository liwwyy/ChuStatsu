package dev.chustatsu;

import static org.junit.Assert.assertEquals;
import dev.chustatsu.stats.StatsView;
import java.util.List;
import org.junit.Test;

public final class TeamOrderTest {
    @Test
    public void colorsFollowBedWarsOrderAndKeepSpectatorIdentity() {
        assertEquals(0, TeamOrder.rank("§c", "z-red"));
        assertEquals(1, TeamOrder.rank("§9", "a-blue"));
        assertEquals(2, TeamOrder.rank("§a", "green"));
        assertEquals(3, TeamOrder.rank("§e", "yellow"));
        assertEquals(3, TeamOrder.rank("§6", "orange"));
        assertEquals(4, TeamOrder.rank("§b", "aqua"));
        assertEquals(5, TeamOrder.rank("§f", "white"));
        assertEquals(6, TeamOrder.rank("§d", "pink"));
        assertEquals(7, TeamOrder.rank("§7", "gray"));
        assertEquals(8, TeamOrder.rank("", "unknown"));
    }

    @Test
    public void nickedStatusIncludesLevelButPrivateLevelStaysVisible() {
        List<String> columns = List.of("Head", "Dynamic name box", "Level", "FKDR", "WLR");
        StatsView nicked = StatsView.error("Alice", StatsView.Status.NICKED, "Nicked");
        StatsView privatePlayer = new StatsView("Bob", StatsView.Status.API_DISABLED,
            42, null, null, null, null, null, null, null, null, "Private");
        org.junit.Assert.assertArrayEquals(new int[] {2, 4}, StatsController.statusSpan(columns, nicked));
        org.junit.Assert.assertArrayEquals(new int[] {3, 4}, StatsController.statusSpan(columns, privatePlayer));
        StatsView rateLimited = StatsView.error("Carol", StatsView.Status.ERROR, "Rate limited");
        org.junit.Assert.assertArrayEquals(new int[] {2, 4}, StatsController.statusSpan(columns, rateLimited));
        org.junit.Assert.assertArrayEquals(new int[] {2, 5}, StatsController.statusSpan(
            List.of("Head", "Dynamic name box", "Level", "Ping", "FKDR", "WLR"), rateLimited));
    }
}
