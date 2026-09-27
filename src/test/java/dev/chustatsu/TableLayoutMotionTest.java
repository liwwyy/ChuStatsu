package dev.chustatsu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public final class TableLayoutMotionTest {
    @Test
    public void enteringLeavingMovingAndResizingRetainContinuity() {
        TableLayoutMotion<String> motion = new TableLayoutMotion<>();
        long start = 1000;
        motion.update(List.of("alice", "bob"), value -> value, start, 160, 150, true, true);
        motion.size(100, 30, start, 150);
        assertEquals(100f, motion.width(start, 150), .01f);

        var moved = motion.update(List.of("bob", "carol"), value -> value, start + 10, 160, 150, true, true);
        motion.size(140, 40, start + 10, 150);
        assertEquals(3, moved.size());
        assertTrue(moved.stream().anyMatch(row -> row.key().equals("alice") && row.leaving()));
        assertEquals(0f, motion.entry("carol", start + 10, 160), .01f);
        assertTrue(motion.move("bob", start + 10, 150) > 0);
        assertEquals(100f, motion.width(start + 10, 150), .01f);
        assertTrue(motion.width(start + 85, 150) > 100f);

        var settled = motion.update(List.of("bob", "carol"), value -> value, start + 200, 160, 150, true, true);
        assertEquals(2, settled.size());
        assertEquals(140f, motion.width(start + 200, 150), .01f);
    }

    @Test
    public void noExitAnimationRemovesTheRowImmediately() {
        TableLayoutMotion<String> motion = new TableLayoutMotion<>();
        motion.update(List.of("alice"), value -> value, 1000, 160, 150, true, false);
        assertEquals(0, motion.update(List.of(), value -> value, 1010, 160, 150, true, false).size());
    }
}
