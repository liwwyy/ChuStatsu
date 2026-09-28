package dev.chustatsu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Correlates only unambiguous same-team roster replacements. */
public final class DenickTracker {
    private static final long WINDOW_MS = 1500;
    private final Map<String, Set<String>> teams = new HashMap<>();
    private final Map<String, List<Change>> removed = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();
    private final Consumer<String> diagnostic;

    private record Change(String key, String originalName, long time) {}

    public DenickTracker() {
        this(message -> {});
    }

    public DenickTracker(Consumer<String> diagnostic) {
        this.diagnostic = diagnostic;
    }

    public void onTeam(String team, int action, Collection<String> members, long now, boolean tracking) {
        if (team == null) {
            diagnostic.accept("failed reason=null_team action=" + action);
            return;
        }
        String id = team.toLowerCase(Locale.ROOT);
        if (action == 1) {
            teams.remove(id);
            removed.remove(id);
            return;
        }
        if (action == 0) {
            teams.put(id, validMembers(members));
            removed.remove(id);
            return;
        }
        if (action != 3 && action != 4) return;
        Set<String> roster = teams.get(id);
        if (roster == null) {
            diagnostic.accept("failed reason=missing_team_roster team=" + team + " action=" + action
                + " members=" + members);
            return;
        }
        Set<String> changes = validMembers(members);
        if (!tracking || changes.size() != 1) {
            removed.remove(id);
            if (action == 3) roster.addAll(changes);
            else roster.removeAll(changes);
            diagnostic.accept("skipped reason=" + (!tracking ? "outside_allowed_phase" : "non_single_valid_member")
                + " team=" + team + " action=" + action + " members=" + members
                + " validCount=" + changes.size());
            return;
        }
        String name = changes.iterator().next();
        String suppliedName = members.stream().filter(member -> member != null
            && member.equalsIgnoreCase(name)).findFirst().orElse(name);
        List<Change> pending = removed.computeIfAbsent(id, ignored -> new ArrayList<>());
        pending.removeIf(change -> {
            long age = now - change.time;
            if (age >= 0 && age <= WINDOW_MS) return false;
            diagnostic.accept("failed reason=removal_expired team=" + team
                + " original=" + change.originalName + " elapsedMs=" + age);
            return true;
        });
        if (action == 4) {
            if (!roster.remove(name)) {
                diagnostic.accept("failed reason=removed_name_not_in_team team=" + team + " name=" + name);
                return;
            }
            pending.add(new Change(name, suppliedName, now));
            if (pending.size() > 32) {
                pending.clear();
                diagnostic.accept("failed reason=too_many_pending_removals team=" + team);
            } else {
                diagnostic.accept("candidate team=" + team + " original=" + suppliedName
                    + " pending=" + pending.size());
            }
            return;
        }
        if (!roster.add(name)) {
            diagnostic.accept("failed reason=added_name_already_in_team team=" + team + " name=" + name);
            return;
        }
        if (pending.removeIf(change -> change.key.equals(name))) {
            diagnostic.accept("skipped reason=same_name_roster_refresh team=" + team + " name=" + name);
            return;
        }
        if (pending.isEmpty()) {
            diagnostic.accept("failed reason=no_recent_same_team_removal team=" + team + " nick=" + name);
            return;
        }
        if (pending.size() != 1) {
            diagnostic.accept("failed reason=ambiguous_removals team=" + team + " nick=" + name
                + " candidates=" + pending.stream().map(Change::originalName).toList());
            pending.clear();
            return;
        }
        Change previous = pending.remove(0);
        if (present(previous.key)) {
            diagnostic.accept("failed reason=original_still_on_team team=" + team + " nick=" + name
                + " original=" + previous.originalName);
            return;
        }
        String existing = aliases.get(name);
        if (existing != null && !existing.equalsIgnoreCase(previous.originalName)) {
            diagnostic.accept("failed reason=conflicting_mapping team=" + team + " nick=" + name
                + " existing=" + existing + " candidate=" + previous.originalName);
            return;
        }
        aliases.put(name, previous.originalName);
        diagnostic.accept("mapped team=" + team + " nick=" + suppliedName + " original=" + previous.originalName
            + " reason=unique_same_team_remove_add elapsedMs=" + (now - previous.time));
    }

    private boolean present(String name) {
        for (Set<String> roster : teams.values()) if (roster.contains(name)) return true;
        return false;
    }

    private static Set<String> validMembers(Collection<String> members) {
        Set<String> names = new HashSet<>();
        if (members == null) return names;
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
