package com.surins;

/** Reversible 0.4-second four-step playback; acquisition reveals the compact inward corners. */
final class ReticleAnimation {
    private boolean active;
    private float from;
    private long changed;
    record Frame(float progress) {
        int sprite() { return Math.min(3, Math.max(0, (int) Math.ceil(progress * 4) - 1)); }
        float alpha() { return Math.min(1, progress * 4); }
    }
    void reset() { active = false; from = 0; changed = 0; }
    void target(boolean next, long now) {
        if (next == active) return;
        from = sample(now).progress(); active = next; changed = now;
    }
    Frame sample(long now) {
        if (changed == 0) return new Frame(0);
        float t = Math.max(0, Math.min(1, (now - changed) / 400_000_000f));
        return new Frame(from + ((active ? 1 : 0) - from) * t);
    }
}
