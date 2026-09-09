package com.surins;

import java.util.UUID;

/** Checks damage isolation, time boundaries, and session reset without launching Minecraft. */
public final class WardenThreatRegressionChecks {
    public static void run() {
        for (int fps : new int[]{5, 30, 60, 144}) {
            WardenHudMotion motion = new WardenHudMotion();
            motion.update(0, 0, true);
            float lastAlpha = 0;
            for (int frame = 1; frame <= fps; frame++) {
                motion.update(frame * 1_000_000_000L / fps, 0, true);
                require(motion.reticleAlpha() >= lastAlpha, "Target fade moved backwards");
                if (frame < fps / 2) require(motion.reticleAlpha() < 0.5, "Target fade completed too early");
                lastAlpha = motion.reticleAlpha();
            }
            require(Math.abs(lastAlpha - 1) < 0.0001, "Target fade must take one second at every frame rate");
            motion.resetReticle();
            motion.update(1_016_000_000L, 0, true);
            require(motion.reticleAlpha() < 0.01, "Target fade progress survived reset");
        }
        require(WardenHudMotion.thornScale(0) == 1, "Idle thorns changed size");
        require(Math.abs(WardenHudMotion.thornScale(1) - 0.88f) < 0.0001, "Impact must shrink thorns by twelve percent");
        require(WardenHudMotion.thornScale(0.5f) < 1 && WardenHudMotion.thornScale(0.5f) > 0.88f, "Impact shrink is not gradual");
        UUID attacker = new UUID(0, 1);
        UUID otherWarden = new UUID(0, 2);
        long start = 8_000_000_000_000_000L;
        WardenImpactMemory memory = new WardenImpactMemory();
        require(!memory.sample(attacker, start).recentlyHit(), "A wandering Warden produced damage feedback");
        memory.record(attacker, start);
        require(attacker.equals(memory.recentAttacker(start + 100_000_000)), "Confirmed attacker cannot take selection priority");
        require(!memory.sample(otherWarden, start + 100_000_000).recentlyHit(), "Damage was attributed to a different Warden");
        require(memory.sample(attacker, start).pulse() == 0, "Impact snapped inward on its first frame");
        require(memory.sample(attacker, start + 100_000_000).pulse() > 0.99f, "Warden hit never contracted the reticle");
        require(memory.sample(attacker, start + 650_000_000).pulse() == 0, "Reticle failed to release after a hit");
        require(memory.sample(attacker, start + 1_000_000_000).recentlyHit(), "Confirmed-hit feedback disappeared too soon");
        require(!memory.sample(attacker, start + 2_000_000_000).recentlyHit(), "Hit feedback was mistaken for permanent targeting");
        require(memory.recentAttacker(start + 2_000_000_000) == null, "Expired attacker kept selection priority");

        memory.record(attacker, start + 3_000_000_000L);
        float previous = 0;
        for (int frame = 0; frame < 180; frame++) {
            float pulse = memory.sample(attacker, start + 3_000_000_000L + frame * 1_000_000_000L / 240).pulse();
            require(Float.isFinite(pulse) && pulse >= 0 && pulse <= 1, "Impact left valid render bounds");
            require(Math.abs(pulse - previous) < 0.07f, "Impact animation jumped between rendered frames");
            previous = pulse;
        }
        memory.record(attacker, start + 4_000_000_000L);
        memory.record(otherWarden, start + 4_010_000_000L);
        require(otherWarden.equals(memory.recentAttacker(start + 4_020_000_000L)), "Selection missed a more recent Warden hit");
        memory.clear();
        require(!memory.sample(attacker, start + 4_100_000_000L).recentlyHit(), "Warden hit survived a disconnect/respawn reset");
        require(memory.recentAttacker(start + 4_100_000_000L) == null, "Attacker selection survived a disconnect/respawn reset");
    }

    public static void main(String[] args) {
        run();
        System.out.println("Warden threat checks passed: attacker isolation, expiry, smooth impact, session reset.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
