package com.surins;

/** Game-tick clock; pauses with the game and never depends on daylight commands or wall time. */
final class SunExposure {
    int ticks, closingTicks;
    boolean closing, opening, blackFrame;
    int tick(boolean exposed) {
        if (opening) { closingTicks = Math.max(0, closingTicks - 1); if (closingTicks == 0) opening = false; return 0; }
        if (closing && !exposed) { lookedAway(); return 0; }
        if (closing) { closingTicks = Math.min(12, closingTicks + 1); return 0; }
        if (!exposed) { ticks = 0; return 0; }
        ticks++;
        if (ticks == 600) { closing = true; return 30; }
        return ticks == 140 ? 7 : ticks == 300 ? 15 : 0;
    }
    float noise() { return Math.clamp((ticks - 40) / 560f, 0, 1); }
    float closure() { return SenseVisualMath.smooth(closingTicks / 12f); }
    boolean ready() { return closing && closingTicks == 12 && blackFrame; }
    void lookedAway() { closing = false; opening = true; ticks = 0; blackFrame = false; }
    void clear() { ticks = closingTicks = 0; closing = opening = blackFrame = false; }
}
