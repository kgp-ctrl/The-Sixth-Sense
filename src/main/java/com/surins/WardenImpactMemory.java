package com.surins;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short, per-attacker damage memory. This never infers a mob's current target. */
final class WardenImpactMemory {
    private static final long RETENTION_NANOS = 2_000_000_000L;
    private final Map<UUID, Long> impacts = new HashMap<>();

    record Impact(boolean recentlyHit, float pulse) {
        private static final Impact NONE = new Impact(false, 0.0f);
    }

    void record(UUID wardenId, long now) {
        impacts.entrySet().removeIf(entry -> now - entry.getValue() > RETENTION_NANOS);
        impacts.put(wardenId, now);
    }

    Impact sample(UUID wardenId, long now) {
        Long hitTime = impacts.get(wardenId);
        if (hitTime == null) return Impact.NONE;
        long elapsed = Math.max(0, now - hitTime);
        if (elapsed >= RETENTION_NANOS) {
            impacts.remove(wardenId);
            return Impact.NONE;
        }

        // A quick inward strike, then a softer release; no instantaneous position jump.
        double seconds = elapsed / 1_000_000_000.0;
        return new Impact(true, pulseAt(seconds));
    }

    static float pulseAt(double seconds) {
        return seconds < 0.10 ? smoothstep(seconds / 0.10)
                : 1.0f - smoothstep((seconds - 0.10) / 0.55);
    }

    void clear() {
        impacts.clear();
    }

    UUID recentAttacker(long now) {
        UUID mostRecent = null;
        long shortestAge = Long.MAX_VALUE;
        for (Map.Entry<UUID, Long> impact : impacts.entrySet()) {
            long age = Math.max(0, now - impact.getValue());
            if (age < RETENTION_NANOS && age < shortestAge) {
                mostRecent = impact.getKey();
                shortestAge = age;
            }
        }
        return mostRecent;
    }

    private static float smoothstep(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return (float) (t * t * (3.0 - 2.0 * t));
    }
}
