package dev.chustatsu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public final class PikaContextTest {
    @Test
    public void recognizesOnlyPikaHosts() {
        assertTrue(PikaContext.isPikaAddress("play.pika-network.net"));
        assertTrue(PikaContext.isPikaAddress("PIKA-NETWORK.NET:25565"));
        assertTrue(PikaContext.isPikaAddress("play.pika.host."));
        assertFalse(PikaContext.isPikaAddress("pika-network.net.evil.example"));
        assertFalse(PikaContext.isPikaAddress("other-network.net"));
        assertFalse(PikaContext.isPikaAddress(null));
    }

    @Test
    public void classifiesFormattedSidebarWithoutChangingRoomRules() {
        PikaContext.SidebarState waiting = PikaContext.classifyLines(List.of(
            "§bBed §FWars", "§7Map: Aquarium"));
        assertTrue(waiting.bedWars());
        assertTrue(waiting.waiting());
        assertFalse(waiting.inGame());

        PikaContext.SidebarState game = PikaContext.classifyLines(List.of(
            "§bBed §FWars", "§cRed: 1", "§7Map: Aquarium"));
        assertTrue(game.bedWars());
        assertFalse(game.waiting());
        assertTrue(game.inGame());

        PikaContext.SidebarState unrelated = PikaContext.classifyLines(List.of("Map: Aquarium", "Red: 1"));
        assertFalse(unrelated.bedWars());
        assertFalse(unrelated.waiting());
        assertFalse(unrelated.inGame());
    }

    @Test
    public void stripsOnlyMinecraftFormattingCodes() {
        assertEquals("bed wars §z!", PikaContext.plain("§BBed §fWars §z!"));
        assertEquals("§", PikaContext.plain("§"));
    }
}
