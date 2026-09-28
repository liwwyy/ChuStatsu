package dev.chustatsu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.polyfrost.oneconfig.api.hud.v1.Section;

public final class TabPositionTest {
    @Test
    public void migratesOnlyTheUninitializedLeftAnchoredDefault() {
        assertTrue(StatsController.shouldCenterNewTab(Section.TopLeft, 0f));
        assertFalse(StatsController.shouldCenterNewTab(Section.TopLeft, 960f));
        assertFalse(StatsController.shouldCenterNewTab(Section.TopCenter, 0f));
    }

    @Test
    public void centersTheScaledOuterPanel() {
        assertEquals(167, StatsController.centeredTabX(480, 147, 1f));
        assertEquals(126, StatsController.centeredTabX(480, 147, 1.5f));
    }
}
