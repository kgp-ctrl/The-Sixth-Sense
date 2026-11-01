package com.surins;

/** Geometry and timing independent of Minecraft, also used by regression checks. */
final class PhantomHudMath {
    private PhantomHudMath() { }

    record Projection(boolean inView, float x, float y) { }

    static float safeScale(float scale) {
        return Float.isFinite(scale) ? Math.max(0.1f, Math.min(5, scale)) : 0.65f;
    }

    // Camera-local coordinates: right +X, up +Y, forward -Z.
    static Projection project(double x, double y, double z, double radius, double fov,
                              int width, int height, float markerWidth, float markerHeight) {
        double tan = Math.tan(Math.toRadians(Math.max(1, Math.min(175, fov))) / 2);
        double aspect = (double) Math.max(1, width) / Math.max(1, height);
        double forward = -z;
        boolean inView = forward > 0 && Math.abs(x) <= forward * tan * aspect + radius
                && Math.abs(y) <= forward * tan + radius;
        // Both axes share the vertical-FOV pixel scale. Applying aspect here again
        // would squeeze horizontal bearings and pin too many targets to the top.
        double dx = x / tan;
        double dy = -y / tan;
        // A directly rearward target has no unique horizontal bearing: use the bottom edge.
        if (Math.abs(dx) + Math.abs(dy) < 0.00001) { dx = 0; dy = 1; }
        double halfWidth = Math.max(0, width / 2.0 - markerWidth / 2.0 - 5);
        double halfHeight = Math.max(0, height / 2.0 - markerHeight / 2.0 - 5);
        double factor = Math.min(Math.abs(dx) < 0.00001 ? Double.POSITIVE_INFINITY : halfWidth / Math.abs(dx),
                Math.abs(dy) < 0.00001 ? Double.POSITIVE_INFINITY : halfHeight / Math.abs(dy));
        return new Projection(inView, (float) (width / 2.0 + dx * factor),
                (float) (height / 2.0 + dy * factor));
    }

    /** A conservative client-only estimate: descending and on a collision course. */
    static boolean approaching(double dx, double dy, double dz, double vx, double vy, double vz) {
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double speedSq = vx * vx + vy * vy + vz * vz;
        if (distanceSq < 4 || distanceSq > 64 * 64 || speedSq < 0.0064) return false;
        double dot = dx * vx + dy * vy + dz * vz;
        if (dot <= 0 || dot / Math.sqrt(distanceSq * speedSq) < 0.94) return false;
        // dy is player minus Phantom: descent, or the last near-horizontal approach.
        if (!(vy < -0.025 && dy < 0 || distanceSq < 64 && Math.abs(dy) < 2)) return false;
        double missSq = Math.max(0, distanceSq - dot * dot / speedSq);
        return missSq <= 9;
    }

    static float fold(double distance) {
        float t = (float)Math.max(0, Math.min(1, (24 - distance) / 22));
        return t * t * (3 - 2 * t);
    }

    static float recovery(long hitAt, long now) {
        if (hitAt == 0) return 1;
        float t = Math.max(0, Math.min(1, (now - hitAt) / 800_000_000f));
        return .6f + .4f * t;
    }

    /** Interruptible transitions preserve their current alpha, including rapid camera turns. */
    static final class Fade {
        private float from;
        private float target;
        private long started;
        private long duration;

        float sample(long now) {
            if (duration == 0) return target;
            double t = Math.max(0, Math.min(1, (double) (now - started) / duration));
            return from + (target - from) * (float) t;
        }

        void to(boolean visible, long now) {
            float next = visible ? 1 : 0;
            if (next == target) return;
            from = sample(now);
            target = next;
            started = now;
            duration = visible ? 800_000_000L : 500_000_000L;
        }
    }
}
