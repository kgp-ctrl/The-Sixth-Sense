package com.surins;

/** Small time-based motions that never change the heart's default center. */
final class WardenHudMotion {
    private boolean initialized;
    private long previousNanos;
    private float angle;
    private float reticleAlpha;
    private double reticleProgress;
    private double time;

    void update(long now, float targetAngle, boolean reticleVisible) {
        if (!initialized) { angle = targetAngle; previousNanos = now; initialized = true; }
        double elapsed = Math.max(0, (now - previousNanos) / 1_000_000_000.0);
        double delta = Math.min(0.1, elapsed);
        previousNanos = now;
        float difference = wrap(targetAngle - angle);
        angle = wrap(angle + difference * (float) -Math.expm1(-delta / 0.065));
        reticleProgress = Math.max(0, Math.min(1, reticleProgress + elapsed * (reticleVisible ? 1 : -4)));
        reticleAlpha = (float) (reticleProgress * reticleProgress * (3 - 2 * reticleProgress));
        time = (time + delta) % (Math.PI * 2);
    }

    float angle() { return angle; }
    float reticleAlpha() { return reticleAlpha; }
    float arrowRadius() {
        // Let the compass clear the thorn brackets as they fade in.
        return HudAnimationMath.WARDEN_ORBIT_RADIUS + 3.5f * reticleAlpha + 0.30f * (float) Math.sin(time * 3);
    }
    float arrowScale() { return HudAnimationMath.WARDEN_ARROW_SCALE * (1 + 0.035f * (float) Math.sin(time * 3)); }

    void reset() { initialized = false; resetReticle(); time = 0; }
    void resetReticle() { reticleAlpha = 0; reticleProgress = 0; }

    static float thornScale(float impact) {
        return 1 - 0.12f * Math.max(0, Math.min(1, impact));
    }

    static float wrap(float value) {
        return (value % 360 + 540) % 360 - 180;
    }
}
