package com.surins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Editable PNG: normal thorns in the upper frame, blood-tip overlay below. */
final class WardenReticleRenderer {
    private static final Identifier TEXTURE = Identifier.of("sixthsense", "textures/gui/warning_warden_thorns.png");
    static final int FRAME_SIZE = 64;
    static final int TEXTURE_HEIGHT = 128;
    private WardenReticleRenderer() {}

    static void draw(DrawContext context, float centerX, float centerY, float scale,
                     float alpha, float impact, float healthFraction) {
        if (alpha < 0.01f) return;
        float blood = Math.max(0, Math.min(1, (0.65f - healthFraction) / 0.65f));
        float inward = 4.8f * impact;
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        float thornScale = scale * 0.5f * WardenHudMotion.thornScale(impact);
        context.getMatrices().scale(thornScale, thornScale, 1);
        for (int side : new int[]{-1, 1}) {
            context.getMatrices().push();
            context.getMatrices().translate(-side * inward, 0, 0);
            int half = FRAME_SIZE / 2;
            int u = side < 0 ? 0 : half;
            int x = side < 0 ? -half : 0;
            context.setShaderColor(1, 1, 1, alpha);
            context.drawTexture(TEXTURE, x, -half, u, 0, half, FRAME_SIZE, FRAME_SIZE, TEXTURE_HEIGHT);
            if (blood > 0) {
                context.setShaderColor(1, 1, 1, alpha * blood);
                context.drawTexture(TEXTURE, x, -half, u, FRAME_SIZE, half, FRAME_SIZE, FRAME_SIZE, TEXTURE_HEIGHT);
            }
            context.getMatrices().pop();
        }
        context.getMatrices().pop();
        context.setShaderColor(1, 1, 1, 1);
    }
}
