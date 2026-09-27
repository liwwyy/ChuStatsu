package dev.chustatsu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Waiting-room arrival groups are a visual hint, not proof of party membership. */
final class OtherPartyTracker {
    private final Map<String, Long> arrivals = new HashMap<>();
    private boolean waiting;

    void setWaiting(boolean nowWaiting) {
        if (!nowWaiting) arrivals.clear();
        waiting = nowWaiting;
    }

    void arrived(String name, long time) {
        if (waiting && valid(name)) arrivals.put(name.toLowerCase(Locale.ROOT), time);
    }

    void left(String name) {
        if (name != null) arrivals.remove(name.toLowerCase(Locale.ROOT));
    }

    int group(String name, int windowMs, java.util.function.Predicate<String> excluded) {
        if (!waiting || name == null) return -1;
        String key = name.toLowerCase(Locale.ROOT);
        if (!arrivals.containsKey(key) || excluded.test(key)) return -1;
        List<Map.Entry<String, Long>> ordered = new ArrayList<>();
        for (var entry : arrivals.entrySet()) {
            if (!excluded.test(entry.getKey())) ordered.add(entry);
        }
        ordered.sort(Comparator.comparingLong((Map.Entry<String, Long> entry) -> entry.getValue())
            .thenComparing(Map.Entry::getKey));
        int color = 0;
        for (int start = 0; start < ordered.size();) {
            int end = start + 1;
            long first = ordered.get(start).getValue();
            while (end < ordered.size() && ordered.get(end).getValue() - first <= windowMs) end++;
            if (end - start >= 2) {
                for (int i = start; i < end; i++) {
                    if (ordered.get(i).getKey().equals(key)) return color % 8;
                }
                color++;
            }
            start = end;
        }
        return -1;
    }

    private static boolean valid(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }
}
