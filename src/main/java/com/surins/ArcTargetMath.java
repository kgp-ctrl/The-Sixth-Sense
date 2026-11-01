package com.surins;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A target must lie below the whole reticle, not merely miss its center ray. */
final class ArcTargetMath {
    private ArcTargetMath() { }
    static boolean outsideNearRange(Box bounds, Vec3d eye) {
        // Distance to the body, rather than its feet/center, also handles large close entities.
        double dx = Math.max(0, Math.max(bounds.minX - eye.x, eye.x - bounds.maxX));
        double dy = Math.max(0, Math.max(bounds.minY - eye.y, eye.y - bounds.maxY));
        double dz = Math.max(0, Math.max(bounds.minZ - eye.z, eye.z - bounds.maxZ));
        return dx * dx + dy * dy + dz * dz >= 16 * 16;
    }
    static boolean belowReticle(Box bounds, Vec3d camera, Quaternionf rotation,
                                double fov, int width, int height) {
        Quaternionf inverse = new Quaternionf(rotation).conjugate();
        double scale = height / (2 * Math.tan(Math.toRadians(fov) / 2));
        double left = Double.POSITIVE_INFINITY, top = left, right = -left, bottom = -left;
        for (double x : new double[]{bounds.minX, bounds.maxX})
            for (double y : new double[]{bounds.minY, bounds.maxY})
                for (double z : new double[]{bounds.minZ, bounds.maxZ}) {
                    Vector3f v = new Vector3f((float)(x-camera.x), (float)(y-camera.y), (float)(z-camera.z)).rotate(inverse);
                    if (v.z >= -.05) return false;
                    double sx = v.x / -v.z * scale, sy = -v.y / -v.z * scale;
                    left = Math.min(left,sx); right = Math.max(right,sx);
                    top = Math.min(top,sy); bottom = Math.max(bottom,sy);
                }
        return belowReticle(left, top, right, bottom, width, height);
    }
    static boolean belowReticle(double left, double top, double right, double bottom, int width, int height) {
        // 8 GUI pixels clears the complete vanilla crosshair area, not only the central dot.
        return top > 8 && top < height / 2.0 && bottom > 0
                && right > -width / 2.0 && left < width / 2.0;
    }
}
