package dev.chustatsu;

import static org.junit.Assert.assertEquals;
import java.util.List;
import org.junit.Test;

public final class DenickTrackerTest {
    @Test
    public void mapsOnlyUniqueWaitingRoomReplacement() {
        DenickTracker tracker = new DenickTracker();
        tracker.onTeam("red", 0, List.of("ActualName"), 1000, true);
        tracker.onTeam("red", 4, List.of("ActualName"), 1100, true);
        tracker.onTeam("red", 3, List.of("NickName"), 1200, true);
        assertEquals("ActualName", tracker.resolve("NickName"));
        tracker.reset();
        assertEquals("NickName", tracker.resolve("NickName"));
    }

    @Test
    public void ignoresAmbiguousLateAndInGameChanges() {
        DenickTracker tracker = new DenickTracker();
        tracker.onTeam("red", 0, List.of("ActualName"), 1000, true);
        tracker.onTeam("red", 4, List.of("ActualName"), 1100, true);
        tracker.onTeam("red", 3, List.of("NickName"), 3000, true);
        assertEquals("NickName", tracker.resolve("NickName"));
        tracker.onTeam("red", 4, List.of("NickName"), 3100, false);
        tracker.onTeam("red", 3, List.of("InGameNick"), 3200, false);
        assertEquals("InGameNick", tracker.resolve("InGameNick"));
        tracker.onTeam("blue", 0, List.of("One", "Two"), 4000, true);
        tracker.onTeam("blue", 4, List.of("One", "Two"), 4100, true);
        tracker.onTeam("blue", 3, List.of("Maybe"), 4200, true);
        assertEquals("Maybe", tracker.resolve("Maybe"));
    }
}
