package dev.chustatsu;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TableMotionTest {
    @Test
    public void reversesWithoutJumpingWhenVisibilityChanges() {
        TableMotion motion = new TableMotion();
        assertEquals(0f, motion.progress(false, 0, 100, 100), .001f);
        assertEquals(0f, motion.progress(true, 10, 100, 100), .001f);
        assertEquals(.5f, motion.progress(true, 60, 100, 100), .001f);
        assertEquals(.5f, motion.progress(false, 60, 100, 100), .001f);
        assertEquals(.25f, motion.progress(false, 110, 100, 100), .001f);
        assertEquals(0f, motion.progress(false, 160, 100, 100), .001f);
    }

    @Test
    public void posesEndAtTheNaturalTablePosition() {
        for (int mode = 0; mode <= 4; mode++) {
            TableMotion.Pose pose = TableMotion.pose(mode, 1f);
            assertEquals(0f, pose.y(), .001f);
            assertEquals(1f, pose.scale(), .001f);
        }
        assertTrue(TableMotion.pose(1, 0f).y() < 0);
        assertTrue(TableMotion.pose(2, 0f).scale() < 1);
    }
}
