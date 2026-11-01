package com.surins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.LivingEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/** Full-body, depth-tested light wash on the single predicted off-reticle bow target. */
final class ArcTargetRenderer {
    private static ShaderProgram maskShader, fillShader;
    private static SimpleFramebuffer mask;
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        public VertexConsumer vertex(float x, float y, float z) { return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer texture(float u, float v) { return this; }
        public VertexConsumer overlay(int u, int v) { return this; }
        public VertexConsumer light(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
    };

    private static final java.util.List<BufferAllocator> layerMemory=new java.util.ArrayList<>();
    private static BufferAllocator quadMemory;
    private static final class LayerBuffer {
        final BufferBuilder builder;
        LayerBuffer(int index){
            if(index==layerMemory.size())layerMemory.add(new BufferAllocator(65536));
            builder=new BufferBuilder(layerMemory.get(index),VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        }
    }

    static void initialize() {
        CoreShaderRegistrationCallback.EVENT.register(registry -> {
            registry.register(Identifier.of("sixthsense", "arc_mask"),
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, shader -> maskShader = shader);
            registry.register(Identifier.of("sixthsense", "arc_fill"),
                    VertexFormats.POSITION_TEXTURE_COLOR, shader -> fillShader = shader);
        });
        WorldRenderEvents.LAST.register(ArcTargetRenderer::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { if (mask != null) { mask.delete(); mask = null; } layerMemory.forEach(BufferAllocator::close);layerMemory.clear();if(quadMemory!=null){quadMemory.close();quadMemory=null;} });
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (maskShader == null || fillShader == null || !SixthSenseClient.warningsAllowed(client)
                || client.options.hudHidden || client.currentScreen != null || context.matrixStack() == null) return;
        float delta = context.tickCounter().getTickDelta(false);
        LivingEntity target = BowAimAssist.highlightTarget(client, delta);
        if (target == null) return;
        var main = client.getFramebuffer();
        var previousShader = RenderSystem.getShader();
        int previousTexture = RenderSystem.getShaderTexture(0);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int src=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcA=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        Map<RenderLayer, LayerBuffer> buffers = new LinkedHashMap<>();
        if(quadMemory==null)quadMemory=new BufferAllocator(1024);
        try {
            if (mask == null) {
                mask = new SimpleFramebuffer(main.textureWidth, main.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
                mask.setClearColor(0, 0, 0, 0);
            } else if (mask.textureWidth != main.textureWidth || mask.textureHeight != main.textureHeight) {
                mask.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
            }
            mask.setTexFilter(GL11.GL_LINEAR);
            mask.clear(MinecraftClient.IS_SYSTEM_MAC);
            mask.copyDepthFrom(main);
            mask.beginWrite(false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            var renderer = client.getEntityRenderDispatcher().getRenderer(target);
            var matrices = context.matrixStack();
            var position = target.getLerpedPos(delta).subtract(context.camera().getPos())
                    .add(renderer.getPositionOffset(target, delta));
            matrices.push();
            try {
                matrices.translate(position.x, position.y, position.z);
                renderer.render(target, target.getYaw(delta), delta, matrices, layer -> {
                    // Keep model and armor textures separate so transparent skin/wing holes stay holes.
                    if (!(layer instanceof RenderLayer.MultiPhase phase)
                            || layer.getVertexFormat() != VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
                            || phase.getPhases().texture.getId().isEmpty()) return DISCARD;
                    return buffers.computeIfAbsent(layer, key -> new LayerBuffer(buffers.size())).builder;
                }, renderer.getLight(target, delta));
            } finally { matrices.pop(); }
            int color = BowAimAssist.highlightColor(client, target);
            for (var entry : buffers.entrySet()) {
                var built = entry.getValue().builder.endNullable();
                if (built == null) continue;
                RenderSystem.setShader(() -> maskShader);
                var texture = ((RenderLayer.MultiPhase)entry.getKey()).getPhases().texture.getId().orElseThrow();
                RenderSystem.setShaderTexture(0, texture);
                maskShader.getUniformOrDefault("GlowAlpha").set(.48f * SixthSenseConfig.instance.warningOpacity);
                maskShader.getUniformOrDefault("GlowColor").set(((color >> 16) & 255) / 255f,
                        ((color >> 8) & 255) / 255f, (color & 255) / 255f);
                // Armor uses vanilla's view-offset layering; match it so helmets also pass depth testing.
                var layering = ((RenderLayer.MultiPhase)entry.getKey()).getPhases().layering;
                layering.startDrawing();
                try { BufferRenderer.drawWithGlobalProgram(built); }
                finally { layering.endDrawing(); }
            }
            main.beginWrite(false);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(() -> fillShader);
            RenderSystem.setShaderTexture(0, mask.getColorAttachment());
            var quad = new BufferBuilder(quadMemory, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            quad.vertex(-1, -1, 0).texture(0, 0).color(-1);
            quad.vertex(1, -1, 0).texture(1, 0).color(-1);
            quad.vertex(1, 1, 0).texture(1, 1).color(-1);
            quad.vertex(-1, 1, 0).texture(0, 1).color(-1);
            BufferRenderer.drawWithGlobalProgram(quad.end());
        } finally {
            layerMemory.forEach(BufferAllocator::clear);quadMemory.clear();
            RenderSystem.blendFuncSeparate(src,dst,srcA,dstA);
            main.beginWrite(false);
            RenderSystem.setShaderTexture(0, previousTexture);
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.depthMask(depthWrite);
            RenderSystem.depthFunc(depthFunction);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        }
    }
}
