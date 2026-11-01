package com.surins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL30;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** One scene copy and one color/noise pass, after world rendering and before the HUD. */
public final class SenseScreenEffects {
    private static ShaderProgram shader;
    private static SimpleFramebuffer scene;
    static void initialize() {
        CoreShaderRegistrationCallback.EVENT.register(registry -> registry.register(Identifier.of("sixthsense", "sense_screen"),
                VertexFormats.POSITION, program -> shader = program));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (scene != null) { scene.delete(); scene = null; }
        });
    }
    public static void render() {
        var client = MinecraftClient.getInstance(); float amount = SenseView.screenStrength();
        if (shader == null || amount <= .001f || !SenseAbility.available(client)) return;
        var main = client.getFramebuffer();
        var old = RenderSystem.getShader(); int texture = RenderSystem.getShaderTexture(0);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            if (scene == null) scene = new SimpleFramebuffer(main.textureWidth, main.textureHeight, false, MinecraftClient.IS_SYSTEM_MAC);
            else if (scene.textureWidth != main.textureWidth || scene.textureHeight != main.textureHeight)
                scene.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.fbo);
            GL30.glBlitFramebuffer(0, 0, main.textureWidth, main.textureHeight, 0, 0, scene.textureWidth, scene.textureHeight,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            main.beginWrite(false);
            RenderSystem.setShaderTexture(0, scene.getColorAttachment());
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
            RenderSystem.disableBlend(); RenderSystem.setShader(() -> shader);
            shader.getUniformOrDefault("Strength").set(SenseView.strength());
            shader.getUniformOrDefault("StaticStrength").set(SenseView.noiseStrength());
            shader.getUniformOrDefault("Blackout").set(SenseView.blackout());
            shader.getUniformOrDefault("NoiseStep").set(SixthSenseConfig.instance.reduceMotion ? 0f : (float)Math.floor(SenseView.effectTime() * 8) % 4096);
            shader.getUniformOrDefault("ScreenSize").set((float)main.textureWidth, (float)main.textureHeight);
            var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
            buffer.vertex(-1, -1, 0); buffer.vertex(1, -1, 0); buffer.vertex(1, 1, 0); buffer.vertex(-1, 1, 0);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            SenseView.blackFrameShown();
        } finally {
            main.beginWrite(false); RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShader(() -> old); RenderSystem.depthMask(write); RenderSystem.blendFuncSeparate(src, dst, srcA, dstA);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        }
    }
}
