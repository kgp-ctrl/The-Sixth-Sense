package com.surins;

/** Shared by HUD rendering, threat detection and local entity outlines. */
final class HudVisibilityPolicy {
    private HudVisibilityPolicy() { }

    static boolean allows(boolean enabled, boolean showInCreative, boolean creative,
                          boolean spectator, boolean alive) {
        return enabled && alive && !spectator && (!creative || showInCreative);
    }
}
