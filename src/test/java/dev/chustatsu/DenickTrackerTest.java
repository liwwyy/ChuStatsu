package dev.chustatsu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
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

    @Test
    public void identifiesTheOrderedWaitingRoomRewritesFromThePacketFindings() {
        List<String> diagnostics = new ArrayList<>();
        DenickTracker tracker = new DenickTracker(diagnostics::add);
        tracker.onTeam("KGOEG", 0, List.of(), 1000, true);
        tracker.onTeam("KGOEG", 3, List.of("HandsomeproBoy"), 1010, true);
        tracker.onTeam("KGOEG", 3, List.of("EliteArmyGaming"), 1020, true);
        tracker.onTeam("KGOEG", 4, List.of("HandsomeproBoy"), 1100, true);
        tracker.onTeam("KGOEG", 3, List.of("Nimmuu"), 1110, true);
        tracker.onTeam("KGOEG", 4, List.of("EliteArmyGaming"), 1120, true);
        tracker.onTeam("KGOEG", 3, List.of("Nammmu"), 1130, true);
        assertEquals("HandsomeproBoy", tracker.resolve("Nimmuu"));
        assertEquals("EliteArmyGaming", tracker.resolve("Nammmu"));
        assertTrue(diagnostics.stream().anyMatch(line -> line.contains("mapped") && line.contains("nick=Nimmuu")));
    }

    @Test
    public void explainsAmbiguousAndGatedReplacementsWithoutGuessing() {
        List<String> diagnostics = new ArrayList<>();
        DenickTracker tracker = new DenickTracker(diagnostics::add);
        tracker.onTeam("waiting", 0, List.of("OriginalOne", "OriginalTwo"), 1000, false);
        tracker.onTeam("waiting", 4, List.of("OriginalOne"), 1100, false);
        tracker.onTeam("waiting", 3, List.of("NickOne"), 1110, false);
        assertEquals("NickOne", tracker.resolve("NickOne"));
        assertTrue(diagnostics.stream().anyMatch(line -> line.contains("outside_allowed_phase")));
        tracker.onTeam("waiting", 4, List.of("OriginalTwo"), 1200, true);
        tracker.onTeam("waiting", 4, List.of("NickOne"), 1210, true);
        tracker.onTeam("waiting", 3, List.of("AnotherNick"), 1220, true);
        assertEquals("AnotherNick", tracker.resolve("AnotherNick"));
        assertTrue(diagnostics.stream().anyMatch(line -> line.contains("ambiguous_removals")
            && line.contains("OriginalTwo")));
    }

    @Test
    public void sameNameRefreshCanResolveAnInterleavedRemoval() {
        DenickTracker tracker = new DenickTracker();
        tracker.onTeam("waiting", 0, List.of("NormalUser", "RealPlayer"), 1000, true);
        tracker.onTeam("waiting", 4, List.of("NormalUser"), 1100, true);
        tracker.onTeam("waiting", 4, List.of("RealPlayer"), 1110, true);
        tracker.onTeam("waiting", 3, List.of("NormalUser"), 1120, true);
        tracker.onTeam("waiting", 3, List.of("Disguise"), 1130, true);
        assertEquals("RealPlayer", tracker.resolve("Disguise"));
    }
}
