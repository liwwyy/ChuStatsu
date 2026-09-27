package dev.chustatsu.stats;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class StatsViewColorTest {
    @Test
    public void ratiosAndLevelUsePikaStyleBands() {
        StatsView view = new StatsView("liywy", StatsView.Status.READY, 42, 5, 2,
            12, 3, 10, 4, null, "VIP", "");
        assertEquals("§9" + 42, view.value("Level"));
        assertEquals("§6" + "4.00", view.value("FKDR"));
        assertEquals("§6" + "2.50", view.value("WLR"));
        assertEquals("§6" + 10, view.value("Winstreak"));
        assertEquals("§7" + 5, view.nametagValue("Wins"));
    }
}
