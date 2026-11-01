package com.surins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Inward corners use the vanilla reticle's background inversion, sliding one pixel inward. */
final class AimCrosshair {
    private AimCrosshair() { }
    static void draw(DrawContext context, int x, int y, ReticleAnimation.Frame frame, float opacity) {
        float alpha = Math.max(0, Math.min(1, frame.alpha() * opacity));
        if (frame.progress() <= .001f || alpha == 0) return;
        context.draw();
        var shader = RenderSystem.getShader();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            RenderSystem.enableBlend();
            // Smoothly interpolate from the background to its inverse; full opacity matches vanilla.
            RenderSystem.blendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ZERO);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
            float radius = 3 - (frame.sprite() + 1) / 4f;
            int a = Math.round(alpha * 255), color = a << 24 | a << 16 | a << 8 | a;
            for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
                float cx = x + radius * sx, cy = y + radius * sy;
                pixel(buffer, matrix, cx, cy, color);
                pixel(buffer, matrix, cx + sx, cy, color);
                pixel(buffer, matrix, cx, cy + sy, color);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally {
            RenderSystem.blendFuncSeparate(src, dst, srcA, dstA);
            if (!blend) RenderSystem.disableBlend();
            RenderSystem.setShader(() -> shader);
        }
    }
    private static void pixel(BufferBuilder b, Matrix4f m, float x, float y, int color) {
        b.vertex(m, x, y, 0).color(color); b.vertex(m, x, y + 1, 0).color(color);
        b.vertex(m, x + 1, y + 1, 0).color(color); b.vertex(m, x + 1, y, 0).color(color);
    }
}
