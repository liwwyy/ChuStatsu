package dev.chustatsu;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class SocialTrackerTest {
    @Test
    public void tracksPartyMessagesAndDisband() {
        SocialTracker tracker = new SocialTracker();
        tracker.observeChat("Party ▏ Alice joined the party!");
        assertTrue(tracker.isParty("alice"));
        tracker.observeChat("Party ▏ Alice left the party!");
        assertFalse(tracker.isParty("Alice"));
        tracker.observeChat("Party ▏ Bob joined the party!");
        tracker.observeChat("Party ▏ The party has been disbanded!");
        assertFalse(tracker.isParty("Bob"));
    }

    @Test
    public void reconcilesMembersFromPartyList() {
        SocialTracker tracker = new SocialTracker();
        tracker.observeChat("Party ▏ Your Party");
        tracker.observeChat("Party ▏ Owner: [VIP] Alice");
        tracker.observeChat("Party ▏ Members: (2) Bob, Carol");
        assertTrue(tracker.isParty("Alice"));
        assertTrue(tracker.isParty("Bob"));
        assertTrue(tracker.isParty("Carol"));
    }
}
