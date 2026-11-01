package com.surins;

/** Target and visibility always travel together; the search box is not the range test. */
final class RaidSelection<T> {
    private T target;
    private double distanceSquared = 128 * 128;
    private boolean visible;
    boolean accepts(double distance, boolean alive) {
        return alive && Double.isFinite(distance) && distance >= 0
                && distance <= 128 * 128 && (target == null || distance < distanceSquared);
    }
    void select(T candidate, double distance, boolean alive, boolean seen) {
        if (candidate != null && accepts(distance, alive)) {
            target = candidate; distanceSquared = distance; visible = seen;
        }
    }
    T target() { return target; }
    boolean visible() { return visible; }
}
