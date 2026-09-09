package com.surins;

/** Reaction timeline: idle 0..3, opening 4..7, fired 8..11, closing 12..15. */
final class GhastAnimationClock {
    private boolean charging;
    private long chargeStart = Long.MIN_VALUE;
    private long shotTime = Long.MIN_VALUE;
    private long closeTime = Long.MIN_VALUE;
    private int closeFrame = 12;

    void observe(boolean newCharging, long now) {
        if (newCharging && !charging) chargeStart = now;
        if (!newCharging && charging) {
            closeTime = now;
            closeFrame = 15 - (int) Math.min(3, Math.max(0, now - chargeStart) / 110);
        }
        charging = newCharging;
    }

    void fired(long now) {
        // A world event and projectile spawn can report the same shot.
        if (shotTime == Long.MIN_VALUE || now - shotTime > 150) shotTime = now;
    }

    boolean recentlyFired(long now) {
        return shotTime != Long.MIN_VALUE && now - shotTime < 600;
    }

    boolean wasChargingRecently(long now) {
        return charging || closeTime != Long.MIN_VALUE && now - closeTime < 200;
    }

    int frame(long now) {
        if (shotTime != Long.MIN_VALUE) {
            long elapsed = Math.max(0, now - shotTime);
            if (elapsed < 240) return 8 + (int) (elapsed / 60);
            if (elapsed < 480) return 12 + (int) ((elapsed - 240) / 60);
        }
        if (charging) return 4 + (int) Math.min(3, Math.max(0, now - chargeStart) / 110);
        if (closeTime != Long.MIN_VALUE && now - closeTime < 240) {
            return Math.min(15, closeFrame + (int) Math.max(0, now - closeTime) / 60);
        }
        return (int) Math.floorMod(now / 150, 4);
    }

    /** Editor demonstration only; actual gameplay requires a charge or shot event. */
    static int previewFrame(long elapsed) {
        long phase = Math.floorMod(elapsed, 2400);
        var clock = new GhastAnimationClock();
        if (phase >= 800) clock.observe(true, 800);
        if (phase >= 1300) { clock.fired(1300); clock.observe(false, 1300); }
        return clock.frame(phase);
    }
}
