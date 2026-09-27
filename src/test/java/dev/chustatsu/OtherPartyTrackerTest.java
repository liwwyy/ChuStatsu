package dev.chustatsu;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class OtherPartyTrackerTest {
    @Test
    public void groupsOnlyCloseArrivalsAndForgetsLeavers() {
        OtherPartyTracker tracker = new OtherPartyTracker();
        tracker.setWaiting(true);
        tracker.arrived("Alpha", 1000);
        tracker.arrived("Bravo", 1200);
        tracker.arrived("Charlie", 3000);
        tracker.arrived("Delta", 3150);
        assertEquals(0, tracker.group("Alpha", 350, name -> false));
        assertEquals(1, tracker.group("Delta", 350, name -> false));
        assertEquals(-1, tracker.group("Alpha", 100, name -> false));
        tracker.left("Bravo");
        assertEquals(-1, tracker.group("Alpha", 350, name -> false));
        tracker.setWaiting(false);
        assertEquals(-1, tracker.group("Delta", 350, name -> false));
    }

    @Test
    public void ownPartyDoesNotCreateAGroup() {
        OtherPartyTracker tracker = new OtherPartyTracker();
        tracker.setWaiting(true);
        tracker.arrived("Alpha", 1000);
        tracker.arrived("Bravo", 1010);
        assertEquals(-1, tracker.group("Alpha", 350, name -> name.equals("bravo")));
    }
}
