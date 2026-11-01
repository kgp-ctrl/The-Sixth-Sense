package com.surins;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Regressions for direction, frame-rate-independent fading and conservative dive detection. */
final class PhantomRegressionChecks {
    static void run() {
        modePolicy();
        edgeProjection();
        cameraTurns();
        fadeTiming();
        movementEvidence();
        scaleLimits();
    }

    private static void modePolicy() {
        check(HudVisibilityPolicy.allows(true, false, false, false, true), "Survival suppressed");
        check(!HudVisibilityPolicy.allows(true, false, true, false, true), "Creative default leaks warnings");
        check(HudVisibilityPolicy.allows(true, true, true, false, true), "Creative opt-in ignored");
        for (boolean creative : new boolean[]{false, true}) {
            check(!HudVisibilityPolicy.allows(false, true, creative, false, true), "Global toggle loses precedence");
            check(!HudVisibilityPolicy.allows(true, true, creative, true, true), "Spectator warning");
            check(!HudVisibilityPolicy.allows(true, true, creative, false, false), "Dead player warning");
        }
    }

    private static void edgeProjection() {
        for (int[] view : new int[][]{{1920, 1080}, {640, 360}, {480, 270}, {320, 240}, {240, 400}}) {
            int w = view[0], h = view[1];
            var front = PhantomHudMath.project(0, 0, -20, 0, 70, w, h, 24, 22);
            check(front.inView(), "Front-facing Phantom is offscreen");
            var backRight = PhantomHudMath.project(10, 0, 20, 0, 70, w, h, 24, 22);
            check(!backRight.inView() && backRight.x() > w / 2f, "Behind-right flipped left");
            var backLeft = PhantomHudMath.project(-10, 0, 20, 0, 70, w, h, 24, 22);
            check(!backLeft.inView() && backLeft.x() < w / 2f, "Behind-left flipped right");
            check(PhantomHudMath.project(0, 20, 0, 0, 70, w, h, 24, 22).y() < h / 2f,
                    "Above target points downward");
            check(PhantomHudMath.project(0, 0, 20, 0, 70, w, h, 24, 22).y() > h / 2f,
                    "Directly rearward fallback missing");
            for (int x = -20; x <= 20; x++) for (int y = -20; y <= 20; y++) {
                var p = PhantomHudMath.project(x, y, 0, 0, 70, w, h, 24, 22);
                check(Float.isFinite(p.x()) && Float.isFinite(p.y()), "Camera-plane singularity");
                check(p.x() >= 17 - 0.001 && p.x() <= w - 17 + 0.001
                        && p.y() >= 16 - 0.001 && p.y() <= h - 16 + 0.001, "Marker clipped by screen");
            }
        }
        check(!PhantomHudMath.project(12, 0, -10, 0, 50, 400, 400, 24, 22).inView(), "Narrow FOV ignored");
        check(PhantomHudMath.project(12, 0, -10, 0, 110, 400, 400, 24, 22).inView(), "Wide FOV ignored");
        check(PhantomHudMath.project(11, 0, -10, 2, 90, 400, 400, 24, 22).inView(), "Partial viewport entry ignored");
        var diagonal=PhantomHudMath.project(-20,20,20,0,70,480,270,24,24);
        near(240-diagonal.x(),135-diagonal.y(),"Widescreen distorts a 45-degree screen bearing");
    }

    private static void cameraTurns() {
        // Minecraft yaw 0 faces world +Z, with world -X on the screen's right.
        Vector3f target = new Vector3f(-10, 0, -20).rotate(new Quaternionf().rotationY((float) Math.PI).conjugate());
        check(target.x > 0 && target.z > 0, "Camera transform mirrored behind-right");
        var p = PhantomHudMath.project(target.x, target.y, target.z, 0, 70, 480, 270, 24, 22);
        check(!p.inView() && p.x() > 240, "World-space behind-right bearing lost");
    }

    private static void fadeTiming() {
        for (int fps : new int[]{5, 20, 60, 144}) {
            var fade = new PhantomHudMath.Fade();
            long start = 10_000_000_000L;
            fade.to(true, start);
            near(fade.sample(start + 400_000_000), .5f, "Fade-in is not 0.8 seconds");
            near(fade.sample(start + 800_000_000), 1, "Fade-in incomplete");
            long spotted = start + 2_000_000_000;
            fade.to(false, spotted);
            for (int frame = 0; frame <= fps; frame++) {
                long now = spotted + Math.round(frame * 500_000_000.0 / fps);
                fade.to(false, now); // Repeated observations must not restart the transition.
                near(fade.sample(now), 1f - (float) frame / fps, "Fade depends on FPS or restarts");
            }
            near(fade.sample(spotted + 500_000_000), 0, "Marker persists after half a second");
        }
        var interrupted = new PhantomHudMath.Fade();
        interrupted.to(true, 1_000_000_000);
        interrupted.to(false, 2_000_000_000);
        float before = interrupted.sample(2_400_000_000L);
        interrupted.to(true, 2_400_000_000L);
        near(interrupted.sample(2_400_000_000L), before, "Camera reversal flashes marker");
    }

    private static void movementEvidence() {
        check(PhantomHudMath.approaching(0, -10, 20, 0, -0.2, 0.4), "Direct incoming dive missed");
        check(!PhantomHudMath.approaching(0, -10, 20, 0.4, 0, 0), "Circling Phantom triggers warning");
        check(!PhantomHudMath.approaching(0, -10, 20, 0, 0.2, -0.4), "Retreat triggers warning");
        check(!PhantomHudMath.approaching(0, -10, 20, 0, 0, 0), "Stationary Phantom triggers warning");
        check(!PhantomHudMath.approaching(0, -10, 20, 0.1, -0.2, 0.4), "Nearby pass mistaken for collision course");
        check(!PhantomHudMath.approaching(0, -50, 100, 0, -0.2, 0.4), "Range cap ignored");
    }

    private static void scaleLimits() {
        near(PhantomHudMath.safeScale(Float.NaN), .65f, "Invalid config scale");
        near(PhantomHudMath.safeScale(1000), 5, "Huge marker scale not clamped");
        near(PhantomHudMath.safeScale(-1), 0.1f, "Negative marker scale");
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.0001, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
