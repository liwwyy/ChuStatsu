package dev.chustatsu;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class MatchOverviewTest {
    @Test
    public void readsOnlyCompleteMatchCounters() {
        String parsed = MatchOverview.parse("§cBeds Destroyed: 2\nKills: 5\nFinal Kills: 3");
        assertTrue(parsed.contains("Beds Destroyed: §f2"));
        assertTrue(parsed.contains("Kills: §f5"));
        assertTrue(parsed.contains("Final Kills: §f3"));
        assertNull(MatchOverview.parse("Kills: 5\nFinal Kills: 3"));
    }
}
