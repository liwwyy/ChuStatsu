package dev.chustatsu;

import static org.junit.Assert.assertEquals;
import java.util.List;
import dev.chustatsu.stats.StatsView;
import org.junit.Test;

public final class ColumnLayoutTest {
    @Test
    public void keepsUserOrderAndIgnoresUnknownOrDuplicateColumns() {
        assertEquals(List.of("WLR", "Dynamic name box", "FKDR"),
            ColumnLayout.selected(new String[] {"WLR", "Name", "bogus", "FKDR", "Name"}, true));
        assertEquals(List.of("Dynamic name box"), ColumnLayout.selected(new String[] {"HP"}, false));
        assertEquals(List.of("Static name box", "Dynamic name box"),
            ColumnLayout.selected(new String[] {"Static name box", "Dynamic name box"}, true));
    }

    @Test
    public void compactHeadersAndSingleRowStatesMatchTheTableModel() {
        assertEquals("LVL", ColumnLayout.header("Level"));
        assertEquals("HWS", ColumnLayout.header("Winstreak"));
        assertEquals("§c❤", ColumnLayout.header("HP"));
        assertEquals("§5NICKED", ColumnLayout.status(StatsView.error("someone",
            StatsView.Status.NICKED, "Nicked")));
        assertEquals("§cAPI DISABLED", ColumnLayout.status(StatsView.error("someone",
            StatsView.Status.API_DISABLED, "Private")));
        StatsView privatePlayer = new StatsView("someone", StatsView.Status.API_DISABLED,
            42, null, null, null, null, null, null, null, null, "Private");
        assertEquals("§942", ColumnLayout.cell("Level", "someone", privatePlayer, -1, null));
        assertEquals("§cAPI DISABLED", ColumnLayout.cell("FKDR", "someone", privatePlayer, -1, null));
        StatsView rateLimited = StatsView.error("someone", StatsView.Status.ERROR, "Rate limited");
        assertEquals("§cRATELIMITED", ColumnLayout.status(rateLimited));
        assertEquals("§cRATELIMITED", ColumnLayout.cell("FKDR", "someone", rateLimited, -1, null));
    }
}
