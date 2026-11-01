package com.surins;

import net.minecraft.client.gui.DrawContext;

/** Observes vanilla's actual heart draws; never replaces or cancels health rendering. */
public final class WhisperHeart {
    private static boolean visible, tracking, half;
    private static float x, y, width, height, baseY;
    private WhisperHeart() { }
    public static void beginFrame() { visible = tracking = false; }
    public static void beginBar(DrawContext context, int px, int py) {
        var m = context.getMatrices().peek().getPositionMatrix();
        baseY = m.m01()*px + m.m11()*py + m.m31(); tracking = true;
    }
    public static void endBar() { tracking = false; }
    public static void capture(DrawContext context, int px, int py, boolean halfHeart) {
        if (!tracking) return;
        half = halfHeart;
        // Vanilla draws in descending heart order: the last draw is slot zero, including jitter/regen.
        var m = context.getMatrices().peek().getPositionMatrix();
        x = m.m00()*px + m.m10()*py + m.m30();
        y = m.m01()*px + m.m11()*py + m.m31();
        width = 9*m.m00(); height = 9*m.m11();
        visible = width > 0 && height > 0;
    }
    static boolean visible() { return visible; }
    static boolean half() { return half; }
    static float baseY() { return baseY; }
    static float x() { return x; }
    static float y() { return y; }
    static float width() { return width; }
    static float height() { return height; }
}
