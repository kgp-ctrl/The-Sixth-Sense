package com.surins;

/** Sprite geometry and timing shared by the live Shulker/Ghast HUD and editor. */
final class HudCharacterAnimation {
    static final int FRAME_SIZE = 32;
    static final int FRAME_COUNT = 16;
    static final int TEXTURE_HEIGHT = FRAME_SIZE * FRAME_COUNT;
    static final int WEAPON_FRAMES = 8;
    static final int WEAPON_TEXTURE_HEIGHT = FRAME_SIZE * WEAPON_FRAMES;
    static final int FRAME_MILLIS = 100;

    private HudCharacterAnimation() {}

    static int frameAt(long nowNanos) {
        return (int) Math.floorMod(nowNanos / (FRAME_MILLIS * 1_000_000L), FRAME_COUNT);
    }

    static int weaponFrame(float drawProgress) {
        return Math.max(0, Math.min(WEAPON_FRAMES - 1, (int) (drawProgress * WEAPON_FRAMES)));
    }
}
