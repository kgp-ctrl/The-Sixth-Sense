package com.surins;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

/**
 * Separates confirmed targeting from damage feedback. Vanilla multiplayer does not
 * synchronize a Warden's brain target, so anger or facing direction are never used
 * as evidence that it has selected this player.
 */
public final class WardenThreatTracker {
    private static final double RANGE_SQUARED = 48.0 * 48.0;
    private static final WardenImpactMemory IMPACTS = new WardenImpactMemory();

    // Client-thread state. References only identify a session and are never read on the server thread.
    private static Object currentWorld;
    private static Object currentPlayer;
    private static Object sessionToken = new Object();
    private static boolean initialized;

    // Only immutable requests/results cross the integrated-server thread boundary.
    private static volatile TargetRequest request;
    private static volatile TargetResult result;

    private WardenThreatTracker() { }

    public record Snapshot(boolean targeting, boolean targetingKnown, boolean recentlyHit, float hitPulse) {
        private static final Snapshot NONE = new Snapshot(false, false, false, 0.0f);

        /** Damage feedback can be shown even when target identity is unavailable. */
        public boolean showReticle() {
            return targeting || recentlyHit;
        }
    }

    private record TargetRequest(Object sessionToken, MinecraftServer server, UUID playerId,
                                 RegistryKey<World> dimension) { }

    private record TargetResult(TargetRequest request, Set<UUID> targetingWardens, UUID nearestTargetingWarden) { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(WardenThreatTracker::sampleIntegratedServer);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    /** Call on the client thread for the Warden represented by the heart/compass. */
    public static Snapshot sample(MinecraftClient client, UUID wardenId) {
        ensureSession(client);
        if (client.world == null || client.player == null || wardenId == null
                || !client.player.isAlive() || client.player.isSpectator()) {
            request = null;
            return Snapshot.NONE;
        }

        TargetResult currentResult = requestTargetSnapshot(client);
        boolean known = currentResult != null;
        boolean targeting = known && currentResult.targetingWardens().contains(wardenId);
        WardenImpactMemory.Impact impact = IMPACTS.sample(wardenId, System.nanoTime());
        return new Snapshot(targeting, known, impact.recentlyHit(), impact.pulse());
    }

    /** Allows a Warden targeting this player to take priority over a nearer passive one. */
    public static UUID targetingWarden(MinecraftClient client) {
        ensureSession(client);
        if (client.world == null || client.player == null || !client.player.isAlive() || client.player.isSpectator()) return null;
        TargetResult currentResult = requestTargetSnapshot(client);
        return currentResult == null ? null : currentResult.nearestTargetingWarden();
    }

    /** Recent damage source, not an assertion about that Warden's current target. */
    public static UUID recentAttacker(MinecraftClient client) {
        ensureSession(client);
        if (client.world == null || client.player == null || !client.player.isAlive() || client.player.isSpectator()) return null;
        return IMPACTS.recentAttacker(System.nanoTime());
    }

    /** Runs after vanilla's main-thread packet handler; blocked shield hits use a different packet. */
    public static void onDamage(MinecraftClient client, EntityDamageS2CPacket packet) {
        ensureSession(client);
        if (client.world == null || client.player == null || packet.entityId() != client.player.getId()) return;

        Entity attacker = client.world.getEntityById(packet.sourceCauseId());
        if (!(attacker instanceof WardenEntity)) {
            attacker = client.world.getEntityById(packet.sourceDirectId());
        }
        if (attacker instanceof WardenEntity warden) {
            IMPACTS.record(warden.getUuid(), System.nanoTime());
        }
    }

    public static void reset() {
        currentWorld = null;
        currentPlayer = null;
        sessionToken = new Object();
        request = null;
        result = null;
        IMPACTS.clear();
    }

    private static void ensureSession(MinecraftClient client) {
        if (currentWorld != client.world || currentPlayer != client.player) {
            reset();
            currentWorld = client.world;
            currentPlayer = client.player;
        }
    }

    private static TargetResult requestTargetSnapshot(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        TargetRequest desired = server == null ? null : new TargetRequest(
                sessionToken, server, client.player.getUuid(), client.world.getRegistryKey());
        if (desired == null || !desired.equals(request)) request = desired;
        TargetResult currentResult = result;
        return desired != null && currentResult != null && desired.equals(currentResult.request()) ? currentResult : null;
    }

    /** Fabric invokes this on the server thread; only server-owned entities are inspected. */
    private static void sampleIntegratedServer(MinecraftServer server) {
        TargetRequest currentRequest = request;
        if (currentRequest == null || currentRequest.server() != server) return;

        Set<UUID> targetingWardens = new HashSet<>();
        UUID nearestTargetingWarden = null;
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(currentRequest.playerId());
        if (player != null && player.isAlive() && !player.isSpectator() && !player.isCreative()
                && player.getServerWorld().getRegistryKey().equals(currentRequest.dimension())) {
            for (WardenEntity warden : player.getServerWorld().getEntitiesByClass(
                    WardenEntity.class, player.getBoundingBox().expand(48), Entity::isAlive)) {
                double distanceSquared = warden.squaredDistanceTo(player);
                if (distanceSquared <= RANGE_SQUARED && (warden.getTarget() == player
                        || warden.getBrain().getOptionalRegisteredMemory(MemoryModuleType.ROAR_TARGET).orElse(null) == player)) {
                    targetingWardens.add(warden.getUuid());
                    if (distanceSquared < nearestDistanceSquared) {
                        nearestDistanceSquared = distanceSquared;
                        nearestTargetingWarden = warden.getUuid();
                    }
                }
            }
        }
        // An old request may finish during a world change; its session token prevents reuse.
        result = new TargetResult(currentRequest, Set.copyOf(targetingWardens), nearestTargetingWarden);
    }
}
