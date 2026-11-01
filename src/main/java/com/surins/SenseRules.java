package com.surins;

/** Shared, deterministic rules; activation subtracts a fixed raw-XP cost. */
final class SenseRules {
    static final int MIN_LEVEL = 20, DURATION = 60 * 20, RADIUS = 52, REST_NIGHTS = 1;
    static final int XP_COST = 109; // Level 18 -> 19 (52) + 19 -> 20 (57).
    static final double SUN_DOT = Math.sqrt((1+.94)/2); // Half the previous angular margin.
    static String rejection(boolean local, int level, int active, int nights, boolean targeted) {
        if (!local) return "multiplayer";
        if (active > 0) return "active";
        if (nights > 0) return "tired";
        if (level < MIN_LEVEL) return "level";
        return targeted ? "targeted" : null;
    }
    // Called only from vanilla's completed-sleep callback, never from a manual bed exit.
    static boolean rested(int sleepTicks) { return sleepTicks >= 100; }
    private SenseRules() { }
}
