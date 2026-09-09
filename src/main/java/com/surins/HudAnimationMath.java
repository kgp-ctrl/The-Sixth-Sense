package com.surins;

/**
 * Shared, GUI-coordinate animation values used by the live Warden warning and
 * the editor preview. Keeping the calculation here prevents the preview from
 * feeling different from the in-game HUD at another GUI scale.
 */
final class HudAnimationMath {

    static final float WARDEN_ORBIT_RADIUS = 10.5f;
    static final float WARDEN_ARROW_SCALE = 0.36f;
    private static final double TAU = Math.PI * 2.0;

    private HudAnimationMath() {
    }

    /** One clock per owner: integrate speed instead of recomputing phase from distance. */
    static final class WardenClock {
        private long previousNanos;
        private boolean initialized;
        private double proximity;
        private double cycle;
        private double motionTime;

        void update(long nowNanos, double distance, boolean visible) {
            double target = Math.max(0.0, Math.min(1.0, (48.0 - distance) / 48.0));
            if (!initialized) {
                previousNanos = nowNanos;
                proximity = target;
                initialized = true;
            }
            double elapsed = Math.max(0.0, Math.min(0.1, (nowNanos - previousNanos) / 1_000_000_000.0));
            previousNanos = nowNanos;
            if (!visible) return;

            proximity += (target - proximity) * -Math.expm1(-elapsed / 0.18);
            double frameSeconds = 0.170 - 0.095 * proximity;
            cycle = (cycle + elapsed / (10.0 * frameSeconds)) % 1.0;
            motionTime = (motionTime + elapsed) % TAU;
        }

        int frame() {
            return (int) (cycle * 10.0);
        }

        Heartbeat heartbeat(float elementScale, boolean animated) {
            if (!animated) return new Heartbeat(0.70f, 0, 0, 0, (float) proximity);
            double phase = cycle * TAU;
            float mainBeat = (float) Math.pow(Math.max(0.0, Math.sin(phase)), 4.0);
            float secondBeat = 0.28f * (float) Math.pow(Math.max(0.0, Math.sin(phase - 0.62)), 6.0);
            float beat = Math.min(1.0f, mainBeat + secondBeat);
            float p = (float) proximity;
            float scale = 0.70f + beat * (0.025f + 0.040f * p);
            float strength = beat * (0.04f + 0.20f * p) * elementScale;
            float jitterX = strength * (float) (0.62 * Math.sin(motionTime * 53)
                    + 0.38 * Math.sin(motionTime * 97 + 0.9));
            float jitterY = strength * (float) (0.58 * Math.sin(motionTime * 61 + 0.4)
                    + 0.42 * Math.sin(motionTime * 109));
            return new Heartbeat(scale, jitterX, jitterY, beat, p);
        }
    }

    static final class Heartbeat {
        final float scaleMultiplier;
        final float jitterX;
        final float jitterY;
        final float beat;
        final float proximity;

        private Heartbeat(float scaleMultiplier, float jitterX, float jitterY, float beat, float proximity) {
            this.scaleMultiplier = scaleMultiplier;
            this.jitterX = jitterX;
            this.jitterY = jitterY;
            this.beat = beat;
            this.proximity = proximity;
        }

        float motionBlurAlpha() {
            return beat * (0.04f + 0.08f * proximity);
        }
    }
}
