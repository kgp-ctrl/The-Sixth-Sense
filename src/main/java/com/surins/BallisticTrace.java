package com.surins;

import net.minecraft.util.math.Vec3d;

/** Vanilla arrow motion without random spread. The scene decides the first collision. */
final class BallisticTrace {
    enum Hit { NONE, BLOCKED, TARGET }
    interface Scene {
        Hit hit(Vec3d from, Vec3d to);
        boolean water(Vec3d position);
    }
    static boolean hitsTarget(Vec3d start, Vec3d velocity, Scene scene) {
        Vec3d pos = start;
        for (int tick = 0; tick < 120; tick++) {
            Vec3d next = pos.add(velocity);
            if (next.squaredDistanceTo(start) > 128 * 128) return false;
            Hit hit = scene.hit(pos, next);
            if (hit != Hit.NONE) return hit == Hit.TARGET;
            velocity = velocity.multiply(scene.water(pos) ? 0.6 : (double) 0.99f).add(0, -0.05, 0);
            pos = next;
        }
        return false;
    }
}
