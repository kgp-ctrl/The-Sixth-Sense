package com.surins;

/** Observable timeline/layout invariants, independent of the Minecraft renderer. */
final class GameplayHudRegressionChecks {
    static void run() {
        ghastReactsOnlyToChargeAndShots();
        wardenStaysCentered();
        compassTakesTheShortPath();
        weaponPosesFollowProgress();
    }

    private static void ghastReactsOnlyToChargeAndShots() {
        var clock = new GhastAnimationClock();
        for (long time = 0; time < 5000; time += 17) {
            require(clock.frame(time) < 4, "Idle Ghast spontaneously fired");
        }
        clock.observe(true, 5000);
        require(clock.frame(5000) == 4, "Charge did not start opening the mouth");
        require(clock.frame(5400) == 7, "Charge did not open the mouth");
        clock.fired(5450);
        clock.observe(false, 5450);
        require(clock.frame(5450) == 8, "Fireball did not trigger the firing expression");
        clock.fired(5490);
        require(clock.frame(5510) == 9, "Duplicate shot event restarted the mouth");
        require(clock.frame(5690) == 12, "Ghast did not start closing after firing");
        require(clock.frame(5930) < 4, "Ghast mouth stayed open after recovery");
        require(!clock.recentlyFired(6050), "Old fireball kept a firing pose alive");

        clock.observe(true, 7000);
        clock.observe(false, 7120);
        require(clock.frame(7120) == 14, "Cancelled charge snapped to a fully open mouth");
        for (long time = 7120; time < 8000; time += 16) {
            require(clock.frame(time) < 8 || clock.frame(time) >= 12, "Cancelled charge fired");
        }
    }

    private static void wardenStaysCentered() {
        for (int screen : new int[]{320, 480, 640, 960}) {
            for (float scale : new float[]{0.7f, 0.805f, 1.4f}) {
                for (int mask = 0; mask < 64; mask++) {
                    float[] widths = new float[6];
                    for (int i = 0; i < 6; i++) {
                        if ((mask & (1 << i)) != 0) widths[i] = 16 * scale * (i == 5 ? 1 : 0.8f + i * 0.1f);
                    }
                    float[] row = HudLayoutMath.warningRow(screen, widths, true, 18 * scale);
                    if (widths[5] > 0) close(row[5] + widths[5] / 2, screen / 2.0f,
                            "Another warning moved the Warden off center");
                    float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
                    for (int i = 0; i < 6; i++) {
                        if (widths[i] == 0) continue;
                        min = Math.min(min, row[i]); max = Math.max(max, row[i] + widths[i]);
                        require(row[i] >= 0 && row[i] + widths[i] <= screen, "Default warning outside viewport");
                        for (int j = i + 1; j < 6; j++) {
                            if (widths[j] > 0) require(row[i] + widths[i] <= row[j] || row[j] + widths[j] <= row[i],
                                    "Automatic warnings overlap");
                        }
                    }
                    if (widths[5] == 0 && mask != 0) close((min + max) / 2, screen / 2.0f,
                            "Normal row lost its centered layout without a Warden");
                }
            }
        }
    }

    private static void compassTakesTheShortPath() {
        var motion = new WardenHudMotion();
        long start = 8_000_000_000_000_000L;
        motion.update(start, 179, false);
        motion.update(start + 16_000_000L, -179, true);
        require(Math.abs(WardenHudMotion.wrap(motion.angle() - 179)) < 1, "Compass spun across the long angle boundary");
        require(motion.reticleAlpha() > 0 && motion.reticleAlpha() < 0.5, "Thorns popped in instead of fading");
        float initialRadius = motion.arrowRadius();
        for (int i = 2; i <= 60; i++) motion.update(start + i * 16_000_000L, -179, true);
        close(motion.angle(), -179, "Compass failed to settle");
        require(Math.abs(motion.arrowRadius() - initialRadius) > 0.01, "Compass has no gentle motion");
        for (int i = 61; i <= 180; i++) motion.update(start + i * 16_000_000L, -179, false);
        require(motion.reticleAlpha() < 0.001, "Thorns remained visible after targeting ended");
        motion.reset();
        require(motion.reticleAlpha() == 0, "Reticle survived a session reset");
    }

    private static void weaponPosesFollowProgress() {
        require(HudCharacterAnimation.weaponFrame(0) == 0, "Resting weapon contains a drawn arrow");
        int previous = -1;
        for (int tick = 0; tick <= 25; tick++) {
            int frame = HudCharacterAnimation.weaponFrame(tick / 25.0f);
            require(frame >= previous && frame < 8, "Weapon pose moved backward during draw");
            previous = frame;
        }
        require(previous == 7 && HudCharacterAnimation.weaponFrame(2) == 7, "Charged weapon did not hold its final pose");
    }

    private static void close(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.001, message);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
