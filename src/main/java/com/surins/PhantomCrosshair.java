package com.surins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Compact glyph with proximity-driven wing folding; the head and eyes stay still. */
final class PhantomCrosshair {
    static final Identifier TEXTURE = Identifier.of("sixthsense", "textures/gui/phantom_crosshair.png");
    static final int TEXTURE_WIDTH = 64, TEXTURE_HEIGHT = 26;
    private static final int LEFT = 0, TOP = 0, WIDTH = 64, HEIGHT = 26;
    private PhantomCrosshair() { }

    static final class State {
        private final PhantomHudMath.Fade fade = new PhantomHudMath.Fade();
        private float eyes, fold;
        private long previous, hitAt;
        void update(boolean visible, boolean incoming, double distance, long hitAt, long now) {
            fade.to(visible, now);
            this.hitAt = hitAt;
            boolean recovering = hitAt != 0 && now - hitAt < 800_000_000L;
            double dt = previous == 0 ? 0 : Math.max(0, (now - previous) / 1e9);
            previous = now;
            float target = incoming && !recovering ? 1 : 0;
            eyes += (target - eyes) * (float)-Math.expm1(-dt / .09);
            fold += (target * PhantomHudMath.fold(distance) - fold) * (float)-Math.expm1(-dt / .12);
        }
        float alpha(long now) { return fade.sample(now) * PhantomHudMath.recovery(hitAt, now); }
        float eyes() { return eyes; }
        float fold() { return fold; }
    }

    static float centerY(float reticleY, float width) {
        // Wing tips sit level with the top of the normal crosshair, with the center unobscured.
        return reticleY - 4 - width * HEIGHT / (2f * WIDTH);
    }

    static float width(float scale) { return Math.min(22, Math.max(6, 18 * scale)); }

    static void draw(DrawContext context, float x, float y, float width, float alpha, float eyes, float fold) {
        if (alpha <= .005f) return;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        float scale = width / WIDTH;
        context.getMatrices().scale(scale, scale, 1);
        context.setShaderColor(1, 1, 1, alpha);
        // Rotate the existing wing regions toward the reticle; no extra PNG or flapping loop.
        wing(context, 0, 26, 26, -42 * fold);
        wing(context, 38, 26, 38, 42 * fold);
        context.drawTexture(TEXTURE, -6, -HEIGHT/2, 26, 0, 12, HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        context.setShaderColor(1 - .60f * eyes, 1 - .08f * eyes, 1 - .82f * eyes, alpha);
        // Eye texels in the compact texture; tint never recolors the wings.
        for (int eyeX = 28; eyeX <= 34; eyeX += 6)
            context.drawTexture(TEXTURE, -WIDTH/2 + eyeX-LEFT, -HEIGHT/2 + 4-TOP,
                    eyeX, 4, 2, 2, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        context.getMatrices().pop();
        context.setShaderColor(1, 1, 1, 1);
    }

    private static void wing(DrawContext context, int sourceX, int width, int pivotX, float angle) {
        context.getMatrices().push();
        context.getMatrices().translate(pivotX - WIDTH / 2f, 4 - HEIGHT / 2f, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        context.drawTexture(TEXTURE, sourceX - pivotX, -4, sourceX, 0, width, HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        context.getMatrices().pop();
    }
}
