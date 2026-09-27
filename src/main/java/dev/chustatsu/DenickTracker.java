package dev.chustatsu;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Correlates a unique remove/add pair on one waiting-room scoreboard team. */
public final class DenickTracker {
    private static final long WINDOW_MS = 1500;
    private final Map<String, Set<String>> teams = new HashMap<>();
    private final Map<String, Change> removed = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    private record Change(String name, String originalName, long time) {}

    public void onTeam(String team, int action, Collection<String> members, long now, boolean waiting) {
        if (team == null) return;
        String id = team.toLowerCase(Locale.ROOT);
        if (action == 1) {
            teams.remove(id);
            removed.remove(id);
            return;
        }
        if (members == null) return;
        if (action == 0) {
            teams.put(id, validMembers(members));
            removed.remove(id);
            return;
        }
        if (action != 3 && action != 4) return;
        Set<String> roster = teams.get(id);
        if (roster == null) return;
        Set<String> changes = validMembers(members);
        if (changes.size() != 1 || !waiting) {
            removed.remove(id);
            if (action == 3) roster.addAll(changes);
            else roster.removeAll(changes);
            return;
        }
        String name = changes.iterator().next();
        if (action == 4) {
            Change previous = removed.remove(id);
            if (roster.remove(name) && previous == null) {
                String originalName = members.iterator().next();
                removed.put(id, new Change(name, originalName, now));
            }
            return;
        }
        if (!roster.add(name)) return;
        Change previous = removed.remove(id);
        if (previous != null && !previous.name.equals(name)
            && now >= previous.time && now - previous.time <= WINDOW_MS
            && !present(previous.name)) {
            aliases.put(name, previous.originalName);
        }
    }

    private boolean present(String name) {
        for (Set<String> roster : teams.values()) if (roster.contains(name)) return true;
        return false;
    }

    private static Set<String> validMembers(Collection<String> members) {
        Set<String> names = new HashSet<>();
        for (String member : members) {
            if (member != null && member.matches("[A-Za-z0-9_]{3,16}")) names.add(member.toLowerCase(Locale.ROOT));
        }
        return names;
    }

    public String resolve(String visibleName) {
        if (visibleName == null) return null;
        return aliases.getOrDefault(visibleName.toLowerCase(Locale.ROOT), visibleName);
    }

    public void reset() {
        teams.clear();
        removed.clear();
        aliases.clear();
    }
}
