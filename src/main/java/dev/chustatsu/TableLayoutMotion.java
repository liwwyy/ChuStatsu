package dev.chustatsu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Animates table dimensions and the positions of arriving, moving, and leaving rows. */
final class TableLayoutMotion<T> {
    record Display<T>(T value, String key, int index, boolean leaving) {}

    private record Departure<T>(T value, int index, long startedAt) {}

    private final Map<String, T> previous = new HashMap<>();
    private final Map<String, Integer> indices = new HashMap<>();
    private final Map<String, Long> enteredAt = new HashMap<>();
    private final Map<String, Departure<T>> departing = new HashMap<>();
    private final Map<String, Float> movedFrom = new HashMap<>();
    private final Map<String, Long> movedAt = new HashMap<>();
    private float fromWidth, fromHeight, targetWidth, targetHeight;
    private long sizeChangedAt;
    private boolean initialized;

    List<Display<T>> update(List<T> current, Function<T, String> name, long now,
                            int exitDuration, int moveDuration, boolean animate,
                            boolean keepDepartures) {
        if (!animate) {
            clear();
            initialized = true;
        }
        Set<String> present = new HashSet<>();
        ArrayList<Display<T>> display = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            T value = current.get(i);
            String key = name.apply(value);
            present.add(key);
            Integer old = indices.get(key);
            if (old == null && initialized) enteredAt.put(key, now);
            if (old != null && old != i) {
                movedFrom.put(key, move(key, now, moveDuration) + old - i);
                movedAt.put(key, now);
            }
            indices.put(key, i);
            previous.put(key, value);
            departing.remove(key);
            display.add(new Display<>(value, key, i, false));
        }
        for (String key : new HashSet<>(previous.keySet())) {
            if (!present.contains(key)) {
                if (animate && keepDepartures)
                    departing.putIfAbsent(key, new Departure<>(previous.get(key), indices.getOrDefault(key, 0), now));
                previous.remove(key);
                indices.remove(key);
                enteredAt.remove(key);
                movedFrom.remove(key);
                movedAt.remove(key);
            }
        }
        if (animate && keepDepartures) {
            List<Map.Entry<String, Departure<T>>> leaving = new ArrayList<>(departing.entrySet());
            leaving.sort(Comparator.comparingInt(item -> item.getValue().index));
            for (Map.Entry<String, Departure<T>> item : leaving) {
                Departure<T> departure = item.getValue();
                if (progress(now, departure.startedAt, exitDuration) >= 1f) departing.remove(item.getKey());
                else display.add(Math.min(departure.index, display.size()),
                    new Display<>(departure.value, item.getKey(), departure.index, true));
            }
        } else departing.clear();
        initialized = true;
        return display;
    }

    void size(float width, float height, long now, int duration) {
        if (!initialized || targetWidth == 0) {
            fromWidth = targetWidth = width;
            fromHeight = targetHeight = height;
        } else if (width != targetWidth || height != targetHeight) {
            fromWidth = width(now, duration);
            fromHeight = height(now, duration);
            targetWidth = width;
            targetHeight = height;
            sizeChangedAt = now;
        }
    }

    float width(long now, int duration) {
        return fromWidth + (targetWidth - fromWidth) * ease(progress(now, sizeChangedAt, duration));
    }

    float height(long now, int duration) {
        return fromHeight + (targetHeight - fromHeight) * ease(progress(now, sizeChangedAt, duration));
    }

    float entry(String key, long now, int duration) {
        Long start = enteredAt.get(key);
        return start == null ? 1f : ease(progress(now, start, duration));
    }

    float exit(String key, long now, int duration) {
        Departure<T> departure = departing.get(key);
        return departure == null ? 1f : ease(progress(now, departure.startedAt, duration));
    }

    float move(String key, long now, int duration) {
        Float from = movedFrom.get(key);
        Long start = movedAt.get(key);
        return from == null || start == null ? 0f : from * (1f - ease(progress(now, start, duration)));
    }

    void clear() {
        previous.clear();
        indices.clear();
        enteredAt.clear();
        departing.clear();
        movedFrom.clear();
        movedAt.clear();
        initialized = false;
        fromWidth = fromHeight = targetWidth = targetHeight = 0;
    }

    private static float progress(long now, long start, int duration) {
        if (start == 0 || duration <= 0) return 1f;
        return Math.max(0f, Math.min(1f, (now - start) / (float) duration));
    }

    private static float ease(float t) { return t * t * (3f - 2f * t); }
}
