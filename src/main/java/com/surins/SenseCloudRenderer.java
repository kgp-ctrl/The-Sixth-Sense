package com.surins;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Uses the owner's interpolated model and pose. No sampled server coordinates or trailing spring. */
public final class SenseCloudRenderer {
    private static final Map<UUID, Cloud> clouds = new HashMap<>();
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        public VertexConsumer vertex(float x, float y, float z) { return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer texture(float u, float v) { return this; }
        public VertexConsumer overlay(int u, int v) { return this; }
        public VertexConsumer light(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
    };
    private static net.minecraft.entity.Entity drawingOwner;
    public static boolean renderingPresence(net.minecraft.entity.Entity entity) { return drawingOwner == entity; }
    private static ShaderProgram shader, maskShader, contourShader;
    private static SimpleFramebuffer contourMask;
    private static final List<BufferAllocator> layerMemory = new ArrayList<>();
    private static Object world;
    private static List<SenseAbility.Presence> membership;
    private static int generation;
    private static boolean wasActive;
    private static float afterglow;
    private static final class Cloud {
        int entityId, generation; float gathered, opacity; boolean noticed, seen;
        Cloud(SenseAbility.Presence p) { entityId = p.entityId(); noticed = p.noticed(); }
    }
    static void initialize() {
        CoreShaderRegistrationCallback.EVENT.register(registry -> registry.register(Identifier.of("sixthsense", "sense_presence"),
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, program -> shader = program));
        CoreShaderRegistrationCallback.EVENT.register(registry -> {
            registry.register(Identifier.of("sixthsense", "arc_mask"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, program -> maskShader = program);
            registry.register(Identifier.of("sixthsense", "sense_contour"), VertexFormats.POSITION_TEXTURE_COLOR, program -> contourShader = program);
        });
        WorldRenderEvents.LAST.register(SenseCloudRenderer::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { layerMemory.forEach(BufferAllocator::close); layerMemory.clear(); clouds.clear(); if (contourMask != null) { contourMask.delete(); contourMask = null; } });
    }
    static void reset() { clouds.clear(); membership = null; world = null; wasActive = false; afterglow = 0; }
    static Vec3d anchor(MobEntity owner, float delta) { return owner.getLerpedPos(delta); }
    private static void render(WorldRenderContext context) {
        var client = MinecraftClient.getInstance();
        if (world != client.world || !SenseAbility.available(client)) { reset(); world = client.world; }
        var state = SenseAbility.snapshot(client);
        if (state == null || context.matrixStack() == null || !client.player.isAlive()
                || client.player.isSpectator() || client.player.isSleeping()) { reset(); world = client.world; return; }
        boolean active = state.active() > 0;
        if (wasActive && !active) {
            afterglow = 5; clouds.values().removeIf(cloud -> !cloud.seen); membership = null;
        }
        wasActive = active;
        if (active) afterglow = 0;
        else {
            afterglow = Math.max(0, afterglow - SenseView.frameSeconds());
            if (afterglow == 0) { clouds.clear(); membership = null; return; }
        }
        float dt = SenseView.frameSeconds();
        if (active && membership != state.presences()) {
            membership = state.presences(); generation++;
            for (var p : membership) {
                Cloud cloud = clouds.computeIfAbsent(p.id(), ignored -> new Cloud(p));
                cloud.entityId = p.entityId(); cloud.noticed = p.noticed(); cloud.generation = generation;
            }
        }
        var iterator = clouds.entrySet().iterator();
        while (active && iterator.hasNext()) {
            Cloud cloud = iterator.next().getValue();
            cloud.opacity = SenseVisualMath.approach(cloud.opacity, cloud.generation == generation ? 1 : 0, dt / .4f);
            cloud.gathered = SenseVisualMath.approach(cloud.gathered, cloud.noticed ? 1 : 0, dt / .45f);
            if (cloud.opacity == 0 && cloud.generation != generation) iterator.remove();
        }
        if (clouds.isEmpty() || shader == null || maskShader == null || contourShader == null || !client.options.getPerspective().isFirstPerson() || client.options.hudHidden) return;
        float delta = context.tickCounter().getTickDelta(false);
        var oldShader = RenderSystem.getShader(); int texture = RenderSystem.getShaderTexture(0);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var main = client.getFramebuffer();
        boolean hasContour = !active; // Only the five-second residual effect uses an outline.
        try {
            if (hasContour) {
                if (contourMask == null) {
                    contourMask = new SimpleFramebuffer(main.textureWidth, main.textureHeight, false, MinecraftClient.IS_SYSTEM_MAC);
                    contourMask.setClearColor(0, 0, 0, 0);
                } else if (contourMask.textureWidth != main.textureWidth || contourMask.textureHeight != main.textureHeight)
                    contourMask.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
                contourMask.setTexFilter(GL11.GL_NEAREST);
                contourMask.clear(MinecraftClient.IS_SYSTEM_MAC);
                main.beginWrite(false);
            }
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.enableCull();
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            for (var entry : clouds.entrySet()) {
                Cloud cloud = entry.getValue();
                var entity = client.world.getEntityById(cloud.entityId);
                if (!(entity instanceof MobEntity owner) || !owner.isAlive() || !owner.getUuid().equals(entry.getKey())) continue;
                if (context.frustum() != null && !context.frustum().isVisible(owner.getBoundingBox().expand(.35))) continue;
                float alpha = cloud.opacity * (active ? SenseView.strength() : afterglow / 5f);
                if (alpha <= .001f) continue;
                cloud.seen = true;
                if (active) {
                    main.beginWrite(false); RenderSystem.enableBlend();
                    float awareness = SenseVisualMath.smooth(cloud.gathered);
                    shader.getUniformOrDefault("Awareness").set(awareness);
                    drawBody(context, owner, delta, shader, alpha * (.70f + .28f * awareness), awareness > .001f);
                } else {
                    contourMask.beginWrite(false); RenderSystem.disableBlend();
                    drawBody(context, owner, delta, maskShader, .26f * alpha, false);
                }
            }
            main.beginWrite(false);
            if (hasContour) {
                RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
                RenderSystem.setShader(() -> contourShader); RenderSystem.setShaderTexture(0, contourMask.getColorAttachment());
                var buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                buffer.vertex(-1, -1, 0).texture(0, 0).color(-1); buffer.vertex(1, -1, 0).texture(1, 0).color(-1);
                buffer.vertex(1, 1, 0).texture(1, 1).color(-1); buffer.vertex(-1, 1, 0).texture(0, 1).color(-1);
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
        } finally {
            main.beginWrite(false);
            RenderSystem.setShaderTexture(0, texture); RenderSystem.setShader(() -> oldShader); RenderSystem.depthMask(write);
            RenderSystem.blendFuncSeparate(src, dst, srcA, dstA);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        }
    }
    private static void drawBody(WorldRenderContext context, MobEntity owner, float delta, ShaderProgram program, float alpha, boolean texturedLayers) {
        var client = MinecraftClient.getInstance(); var matrices = context.matrixStack(); matrices.push();
        try {
            var renderer = client.getEntityRenderDispatcher().getRenderer(owner);
            var bodyTexture = renderer.getTexture(owner);
            var position = anchor(owner, delta).subtract(context.camera().getPos()).add(renderer.getPositionOffset(owner, delta));
            matrices.translate(position.x, position.y, position.z);
            Map<Identifier, BufferBuilder> buffers = new LinkedHashMap<>();
            drawingOwner = owner;
            renderer.render(owner, owner.getYaw(delta), delta, matrices, layer -> {
                if (!(layer instanceof RenderLayer.MultiPhase phase)
                        || layer.getVertexFormat() != VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL) return DISCARD;
                var layerTexture = phase.getPhases().texture.getId();
                if (layerTexture.isEmpty() || !texturedLayers && !layerTexture.get().equals(bodyTexture)) return DISCARD;
                return buffers.computeIfAbsent(layerTexture.get(), ignored -> {
                    int index = buffers.size();
                    if (index == layerMemory.size()) layerMemory.add(new BufferAllocator(65536));
                    return new BufferBuilder(layerMemory.get(index), VertexFormat.DrawMode.QUADS,
                            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                });
            }, 0xF000F0);
            for (var layer : buffers.entrySet()) {
                var built = layer.getValue().endNullable();
                if (built == null) continue;
                RenderSystem.setShader(() -> program); RenderSystem.setShaderTexture(0, layer.getKey());
                program.getUniformOrDefault("PresenceAlpha").set(alpha);
                program.getUniformOrDefault("GlowAlpha").set(alpha);
                program.getUniformOrDefault("GlowColor").set(.72f, .31f, .40f);
                program.getUniformOrDefault("NoiseStep").set(SixthSenseConfig.instance.reduceMotion ? 0f : (float)Math.floor(SenseView.effectTime() * 10) % 4096);
                BufferRenderer.drawWithGlobalProgram(built);
            }
        } finally { drawingOwner = null; layerMemory.forEach(BufferAllocator::clear); matrices.pop(); }
    }

}
