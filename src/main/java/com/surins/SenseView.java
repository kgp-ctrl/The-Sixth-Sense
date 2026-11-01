package com.surins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;

/** Presentation only: leaves options, chunk loading and vanilla status-effect registries untouched. */
public final class SenseView {
    private static float cinema, glare, sunNoise;
    private static final SunExposure sunlight = new SunExposure();
    private static Object world;
    private static long previous;
    private static float frameSeconds;
    private static double effectTime;
    private static boolean sunExposed, sunAnnounced, wasSleeping;
    private static int wakeGrace, sunTicks;
    private SenseView() { }
    static void initialize() {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context -> animate(MinecraftClient.getInstance()));
        HudRenderCallback.EVENT.register((context, counter) -> render(context));
    }
    static void reset() {
        world = null; cinema = glare = sunNoise = frameSeconds = 0; sunlight.clear(); effectTime = 0; previous = 0;
        sunExposed = sunAnnounced = wasSleeping = false; wakeGrace = sunTicks = 0;
    }
    private static boolean validateWorld(MinecraftClient client) {
        if (world != client.world || !SenseAbility.available(client)) { reset(); world = client.world; }
        return SenseAbility.available(client);
    }
    static void tick(MinecraftClient client) {
        if (!validateWorld(client)) return;
        boolean sleeping = client.player.isSleeping();
        if (sleeping || wasSleeping && !sleeping) wakeGrace = 30;
        else if (wakeGrace > 0) wakeGrace--;
        wasSleeping = sleeping;
        var state = SenseAbility.snapshot(client);
        boolean fatigued = state != null && state.active() == 0 && state.nights() > 0;
        if (state == null || state.nights() == 0 || state.active() > 0) sunAnnounced = false;
        sunExposed = fatigued && wakeGrace == 0 && client.currentScreen == null && client.options.getPerspective().isFirstPerson()
                && !client.player.isSleeping() && !client.player.isSpectator() && sunFacing(client);
        sunTicks = sunExposed ? Math.min(8, sunTicks + 1) : 0;
        if (!fatigued || sleeping) sunlight.clear();
        if (!client.isPaused() && client.currentScreen == null) {
            int milestone = sunlight.tick(sunExposed);
            if (milestone != 0) SenseWhisper.show("sun_" + milestone);
            if (sunlight.ready()) {
                // Only rotate after a fully black world frame has actually been presented.
                client.player.setPitch(35); client.player.prevPitch = 35;
                sunlight.lookedAway(); sunExposed = false; wakeGrace = 20;
            }
        }
        if (sunTicks >= 8 && !sunAnnounced) { SenseWhisper.show("sun"); sunAnnounced = true; }
    }
    private static void animate(MinecraftClient client) {
        if (!validateWorld(client)) return;
        long now = System.nanoTime();
        float dt = previous == 0 || client.isPaused() ? 0 : Math.min(.1f, (now - previous) / 1_000_000_000f);
        previous = now; frameSeconds = dt; effectTime += dt;
        var state = SenseAbility.snapshot(client);
        boolean firstPerson = client.options.getPerspective().isFirstPerson() && client.player != null
                && !client.player.isSleeping() && !client.player.isSpectator();
        float target = state != null && state.active() > 0 && firstPerson ? 1 : 0;
        cinema = SenseVisualMath.approach(cinema, target, dt / (target > cinema ? .8f : .6f));
        float sun = firstPerson && sunExposed ? 1 : 0;
        glare = SenseVisualMath.approach(glare, sun, dt / .35f);
        sunNoise = SenseVisualMath.approach(sunNoise, firstPerson && sunExposed ? sunlight.noise() : 0, dt / .4f);
    }
    private static boolean sunFacing(MinecraftClient client) {
        var player = client.player; var level = client.world;
        if (!level.getDimension().hasSkyLight() || !level.isDay() || level.isRaining()
                || player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.CARVED_PUMPKIN)
                || player.isSubmergedInWater() || level.getLightLevel(LightType.SKY, player.getBlockPos()) < 12) return false;
        double angle = level.getSkyAngle(1) * Math.PI * 2;
        Vec3d sun = new Vec3d(-Math.sin(angle), Math.cos(angle), 0);
        Vec3d eye = player.getEyePos();
        return sun.y > 0 && player.getRotationVec(1).dotProduct(sun) > SenseRules.SUN_DOT
                && level.raycast(new RaycastContext(eye, eye.add(sun.multiply(128)), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS;
    }
    public static double fov(double original) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!validateWorld(client) || client.player.isUsingSpyglass() || !client.options.getPerspective().isFirstPerson()) return original;
        return original * (1 + .10 * smooth(cinema));
    }
    public static void fog(Camera camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!validateWorld(client) || glare <= 0 || camera.getSubmersionType() != net.minecraft.block.enums.CameraSubmersionType.NONE) return;
        float end = RenderSystem.getShaderFogEnd();
        float reduced = Math.min(end, client.options.getViewDistance().getValue() * 8f);
        if (reduced >= end) return;
        float next = end + (reduced - end) * smooth(glare);
        RenderSystem.setShaderFogEnd(next);
        RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), next * .7f));
    }
    static float frameSeconds() { return frameSeconds; }
    static double effectTime() { return effectTime; }
    static float screenStrength() { return Math.max(strength(), Math.max(noiseStrength(), blackout())); }
    static float noiseStrength() { return validateWorld(MinecraftClient.getInstance()) ? sunNoise : 0; }
    static float blackout() { return validateWorld(MinecraftClient.getInstance()) ? sunlight.closure() : 0; }
    static void blackFrameShown() { if (sunlight.closure() >= 1) sunlight.blackFrame = true; }
    static float strength() { return validateWorld(MinecraftClient.getInstance()) ? smooth(cinema) : 0; }
    private static float smooth(float v) { return v * v * (3 - 2 * v); }
    private static void render(DrawContext context) {
        var client = MinecraftClient.getInstance();
        if (!validateWorld(client) || client.options.hudHidden || Math.max(cinema, glare) <= 0) return;
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        float active = smooth(cinema), squint = smooth(glare);
        int bar = Math.round(height * (.03f * active + .065f * squint));
        int feather = Math.round(height * (.09f * active + .12f * squint));
        int dark = Math.round(255 * Math.min(.60f, .38f * active + .50f * squint)) << 24;
        context.fill(0, 0, width, bar, 0xFF000000);
        context.fill(0, height - bar, width, height, 0xFF000000);
        if (feather > 0) {
            context.fillGradient(0, bar, width, bar + feather, dark, 0);
            context.fillGradient(0, height - bar - feather, width, height - bar, 0, dark);
        }

    }
}
