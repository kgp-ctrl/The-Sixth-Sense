package com.surins;

/** Frame-rate-independent transitions; unrelated FOV queries never advance visual time. */
final class SenseVisualMath {
    static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + Math.max(0, step)) : Math.max(target, value - Math.max(0, step));
    }
    static float smooth(float value) { return value * value * (3 - 2 * value); }
    private SenseVisualMath() { }
}
