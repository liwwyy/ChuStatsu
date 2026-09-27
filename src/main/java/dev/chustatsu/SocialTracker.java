package dev.chustatsu;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks party chat and friends supplied by the shared Pika profile cache. */
public final class SocialTracker {
    private static final Pattern JOIN = Pattern.compile("(?:^|▏\\s*)(?:✚\\s*)?(?:\\[[^]]+\\]\\s*)?([A-Za-z0-9_]{3,16}) joined the party", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEAVE = Pattern.compile("(?:^|▏\\s*)(?:▬\\s*)?(?:\\[[^]]+\\]\\s*)?([A-Za-z0-9_]{3,16}) (?:left|was kicked from) the party", Pattern.CASE_INSENSITIVE);
    private static final Pattern OWNER = Pattern.compile("▏\\s*Owner:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMBERS = Pattern.compile("▏\\s*Members:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private final Set<String> party = new HashSet<>();
    private volatile Set<String> friends = Set.of();
    private String localName;
    private boolean inParty;
    private boolean capture;
    private final Set<String> captured = new HashSet<>();

    public void observeChat(String raw) {
        if (raw == null) return;
        String text = raw.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
        if (text.matches("(?i)^Party\\s*▏\\s*.*disbanded.*" ) || text.matches("(?i)^Party\\s*▏\\s*You (?:have )?(?:left|been kicked).*")) {
            party.clear(); inParty = false; capture = false; return;
        }
        Matcher joined = JOIN.matcher(text);
        if (joined.find()) {
            inParty = true;
            party.add(key(joined.group(1)));
            if (localName != null) party.add(key(localName));
            return;
        }
        Matcher left = LEAVE.matcher(text);
        if (left.find()) {
            String name = key(left.group(1));
            if (localName != null && name.equals(key(localName))) {
                party.clear(); inParty = false;
            } else party.remove(name);
            return;
        }
        if (text.matches("(?i).*▏\\s*Your Party\\b.*")) {
            capture = true; captured.clear();
            if (localName != null) captured.add(key(localName));
            return;
        }
        if (!capture) return;
        Matcher owner = OWNER.matcher(text);
        if (owner.find()) {
            addLastName(captured, owner.group(1));
            return;
        }
        Matcher members = MEMBERS.matcher(text);
        if (members.find()) {
            for (String part : members.group(1).split(",")) addLastName(captured, part);
            party.clear(); party.addAll(captured);
            inParty = true; capture = false;
        }
    }

    private static void addLastName(Set<String> set, String text) {
        Matcher matcher = NAME.matcher(text.replaceAll("\\[[^]]+]", " "));
        String last = null;
        while (matcher.find()) last = matcher.group();
        if (last != null) set.add(key(last));
    }

    public void updateLocalName(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) return;
        if (username.equalsIgnoreCase(localName)) return;
        localName = username;
        party.clear(); inParty = false; capture = false;
        friends = Set.of();
    }

    public void updateFriends(Set<String> names) {
        if (names != null && localName != null) friends = Set.copyOf(names);
    }

    public boolean isParty(String username) { return inParty && party.contains(key(username)); }
    public boolean isFriend(String username) { return friends.contains(key(username)); }
    public void reset() { party.clear(); friends = Set.of(); localName = null; inParty = false; capture = false; }
    private static String key(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
}
