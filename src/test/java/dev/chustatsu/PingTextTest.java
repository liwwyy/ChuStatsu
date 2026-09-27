package dev.chustatsu;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class PingTextTest {
    @Test
    public void colorsLatencyAndOptionallyShowsUnits() {
        assertEquals("§a90", PingText.format(90, true));
        assertEquals("§e91ms", PingText.format(91, false));
        assertEquals("§6200ms", PingText.format(200, false));
        assertEquals("§c201", PingText.format(201, true));
        assertEquals("§4400", PingText.format(400, true));
        assertEquals("§8?", PingText.format(0, true));
    }
}
