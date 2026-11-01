package com.surins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-Phantom state; the integrated server publishes immutable attack snapshots only. */
public final class PhantomThreatTracker {
    private static PhantomCrosshair.State crosshair = new PhantomCrosshair.State();
    private static final Map<UUID, Threat> THREATS = new HashMap<>();
    private static Object world;
    private static Object player;
    private static Object session = new Object();
    private static volatile Request request;
    private static volatile Result result;

    private record Request(Object session, MinecraftServer server, UUID player, RegistryKey<World> dimension) { }
    private record Result(Request request, Set<UUID> diving) { }

    private static final class Threat {
        PhantomEntity entity;
        Vec3d previous;
        Vec3d velocity = Vec3d.ZERO;
        int approachingTicks;
        boolean diving;
        boolean noticed;
        boolean visible;
        long lastDive;
        long hitAt;

        Threat(PhantomEntity entity) {
            this.entity = entity;
            previous = entity.getPos();
        }
    }

    private PhantomThreatTracker() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(PhantomThreatTracker::sampleServer);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void reset() {
        THREATS.clear();
        crosshair = new PhantomCrosshair.State();
        world = null;
        player = null;
        session = new Object();
        request = null;
        result = null;
    }

    public static void tick(MinecraftClient client) {
        if (world != client.world || player != client.player) {
            reset();
            world = client.world;
            player = client.player;
        }
        if (!SixthSenseClient.warningsAllowed(client) || !SixthSenseConfig.instance.enablePhantomWarning) {
            THREATS.clear();
            crosshair = new PhantomCrosshair.State();
            request = null;
            result = null;
            return;
        }
        MinecraftServer server = client.getServer();
        Request desired = server == null ? null : new Request(session, server,
                client.player.getUuid(), client.world.getRegistryKey());
        if (!java.util.Objects.equals(desired, request)) request = desired;
        Result snapshot = result;
        boolean known = desired != null && snapshot != null && desired.equals(snapshot.request());
        long now = System.nanoTime();
        Set<UUID> nearby = new HashSet<>();
        for (PhantomEntity phantom : client.world.getEntitiesByClass(PhantomEntity.class,
                client.player.getBoundingBox().expand(64), Entity::isAlive)) {
            if (phantom.squaredDistanceTo(client.player) > 64 * 64) continue;
            UUID id = phantom.getUuid();
            nearby.add(id);
            Threat threat = THREATS.computeIfAbsent(id, ignored -> new Threat(phantom));
            threat.entity = phantom;
            Vec3d movement = phantom.getPos().subtract(threat.previous);
            threat.previous = phantom.getPos();
            threat.velocity = threat.velocity.multiply(0.4).add(movement.multiply(0.6));
            Vec3d toPlayer = client.player.getEyePos().subtract(phantom.getBoundingBox().getCenter());
            Vec3d velocity = threat.velocity;
            boolean estimate = PhantomHudMath.approaching(toPlayer.x, toPlayer.y, toPlayer.z,
                    velocity.x, velocity.y, velocity.z);
            threat.approachingTicks = estimate ? threat.approachingTicks + 1 : 0;
            // Never substitute an estimate while waiting for an integrated-server snapshot.
            boolean diving = server != null ? known && snapshot.diving().contains(id)
                    : threat.approachingTicks >= 2;
            if (now - threat.hitAt < 500_000_000L) diving = false;
            if (diving) threat.lastDive = now;
            threat.diving = diving || server == null && threat.lastDive != 0
                    && now - threat.lastDive < 250_000_000L && now - threat.hitAt >= 500_000_000L
                    && velocity.y <= .02;
            // Acquire in vanilla's 16 x 64 x 16 search volume. Once noticed, keep the
            // same nearby Phantom through its takeoff/circling/dive cycle, up to 64 blocks.
            threat.noticed |= phantom.getBoundingBox().expand(16, 64, 16).intersects(client.player.getBoundingBox());
            threat.visible = threat.noticed && (clearSight(client, client.player.getEyePos(), phantom.getEyePos(), phantom)
                    || clearSight(client, client.player.getEyePos(), phantom.getBoundingBox().getCenter(), phantom));

        }
        THREATS.entrySet().removeIf(entry -> !nearby.contains(entry.getKey()));
        updateCrosshair(now);
    }

    private static void sampleServer(MinecraftServer server) {
        Request current = request;
        if (current == null || current.server() != server) return;
        Set<UUID> diving = new HashSet<>();
        var target = server.getPlayerManager().getPlayer(current.player());
        if (target != null && target.isAlive() && !target.isSpectator() && !target.isCreative()
                && target.getServerWorld().getRegistryKey().equals(current.dimension())) {
            for (PhantomEntity phantom : target.getServerWorld().getEntitiesByClass(PhantomEntity.class,
                    target.getBoundingBox().expand(64), Entity::isAlive)) {
                if (phantom.getTarget() == target) {
                    if (phantom.movementType == PhantomEntity.PhantomMovementType.SWOOP) diving.add(phantom.getUuid());
                }
            }
        }
        result = new Result(current, Set.copyOf(diving));
    }

    private static boolean clearSight(MinecraftClient client, Vec3d from, Vec3d to, Entity entity) {
        return client.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, entity)).getType() == HitResult.Type.MISS;
    }

    static float markerWidth(MinecraftClient client) {
        // Preserve old saved size values while capping this crosshair glyph to a discreet footprint.
        return PhantomCrosshair.width(SixthSenseConfig.instance.phantomDisplayScale());
    }

    private static void updateCrosshair(long now) {
        Threat selected = null;
        double nearest = Double.POSITIVE_INFINITY;
        if (player instanceof net.minecraft.entity.player.PlayerEntity viewer) {
            for (Threat threat : THREATS.values()) {
                if (!threat.entity.isAlive() || !threat.visible) continue;
                double distance = threat.entity.squaredDistanceTo(viewer);
                // An active dive takes precedence over a nearby Phantom that already struck.
                if (selected == null || threat.diving && !selected.diving
                        || threat.diving == selected.diving && (distance < nearest
                        || distance == nearest && threat.entity.getUuid().compareTo(selected.entity.getUuid()) < 0)) {
                    selected = threat; nearest = distance;
                }
            }
        }
        crosshair.update(selected != null, selected != null && selected.diving, Math.sqrt(nearest),
                selected == null ? 0 : selected.hitAt, now);
    }

    public static void render(DrawContext context, MinecraftClient client, float tickDelta) {
        if (!client.options.getPerspective().isFirstPerson() || !SixthSenseConfig.instance.enablePhantomWarning) return;
        long now = System.nanoTime();
        updateCrosshair(now);
        float width = markerWidth(client);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        float reticleX = (context.getScaledWindowWidth() - 15) / 2 + 7.5f;
        float reticleY = (context.getScaledWindowHeight() - 15) / 2 + 7.5f;
        PhantomCrosshair.draw(context, reticleX,
                PhantomCrosshair.centerY(reticleY, width), width,
                crosshair.alpha(now)*SixthSenseConfig.instance.warningOpacity, crosshair.eyes(), crosshair.fold());
        RenderSystem.disableBlend();
    }

    static void drawIcon(DrawContext context, float centerX, float centerY, float width, float alpha) {
        long now = System.nanoTime();
        float green = SixthSenseConfig.instance.reduceMotion ? 1 : (float)(.5+.5*Math.sin(now/1e9*1.5));
        PhantomCrosshair.draw(context, centerX, centerY, Math.min(22,width), alpha, green, 0);
    }

    public static void onDamage(MinecraftClient client, EntityDamageS2CPacket packet) {
        if (!SixthSenseClient.warningsAllowed(client) || packet.entityId() != client.player.getId()) return;
        Entity attacker = client.world.getEntityById(packet.sourceCauseId());
        if (!(attacker instanceof PhantomEntity)) attacker = client.world.getEntityById(packet.sourceDirectId());
        if (!(attacker instanceof PhantomEntity)) return;
        Threat threat = THREATS.get(attacker.getUuid());
        if (threat != null) {
            threat.hitAt = System.nanoTime();
            threat.diving = false;
            updateCrosshair(threat.hitAt);
        }
    }
}
