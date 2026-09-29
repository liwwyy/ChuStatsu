package dev.chustatsu;

/** Time based table visibility motion. The same state works across rapid key presses. */
final class TableMotion {
    private float value;
    private float from;
    private boolean target;
    private long startedAt;
    private int durationMs;

    float progress(boolean show, long nowMs, int showMs, int hideMs) {
        if (show != target) {
            value = current(nowMs);
            from = value;
            target = show;
            startedAt = nowMs;
            durationMs = Math.max(1, show ? showMs : hideMs);
        }
        value = current(nowMs);
        return value;
    }

    void snapHidden() {
        snap(false);
    }

    boolean isHidden() {
        return !target && value <= 0f;
    }

    void snap(boolean show) {
        value = show ? 1f : 0f;
        from = value;
        target = show;
        durationMs = 0;
    }

    private float current(long nowMs) {
        if (durationMs == 0) return target ? 1f : 0f;
        float t = Math.max(0f, Math.min(1f, (nowMs - startedAt) / (float) durationMs));
        return from + ((target ? 1f : 0f) - from) * t;
    }

    static Pose pose(int mode, float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        float eased = 1f - (float) Math.pow(1f - p, 3);
        return switch (mode) {
            case 1 -> new Pose(-18f * (1f - eased), 1f);
            case 2 -> new Pose(0f, .85f + .15f * eased);
            case 3 -> {
                float t = p - 1f;
                yield new Pose(0f, 1f + .22f * (2.70158f * t * t * t + 1.70158f * t * t));
            }
            case 4 -> new Pose(0f, p < .8f ? .08f + 1.04f * (p / .8f)
                : 1.12f - .12f * ((p - .8f) / .2f));
            default -> new Pose(0f, 1f);
        };
    }

    record Pose(float y, float scale) {}
}
