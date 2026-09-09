package com.surins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.WorldEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Uses synchronized charge flags and shot events, never an autonomous firing loop. */
public final class GhastThreatTracker {
    private static final Map<UUID, GhastAnimationClock> CLOCKS = new HashMap<>();
    private static final Map<UUID, Long> SEEN = new HashMap<>();
    private static final Map<UUID, Long> FIREBALLS = new HashMap<>();
    private static ClientWorld world;

    private GhastThreatTracker() {}

    static long now() { return System.nanoTime() / 1_000_000L; }

    static void beginTick(MinecraftClient client) {
        if (world != client.world) {
            world = client.world;
            CLOCKS.clear(); SEEN.clear(); FIREBALLS.clear();
        }
        long now = now();
        SEEN.entrySet().removeIf(entry -> {
            if (now - entry.getValue() < 5000) return false;
            CLOCKS.remove(entry.getKey());
            return true;
        });
        FIREBALLS.entrySet().removeIf(entry -> now - entry.getValue() > 10_000);
    }

    static void observe(GhastEntity ghast) {
        long now = now();
        SEEN.put(ghast.getUuid(), now);
        CLOCKS.computeIfAbsent(ghast.getUuid(), id -> new GhastAnimationClock()).observe(ghast.isShooting(), now);
    }

    static void observe(FireballEntity fireball) {
        long now = now();
        if (FIREBALLS.putIfAbsent(fireball.getUuid(), now) != null) return;
        if (fireball.age > 4 || !(fireball.getOwner() instanceof GhastEntity ghast)) return;
        GhastAnimationClock clock = CLOCKS.get(ghast.getUuid());
        // Silent Ghasts omit the sound event. Correlate a fresh owned projectile
        // with a charge already observed, instead of replaying old flying fireballs.
        if (clock != null && clock.wasChargingRecently(now)) clock.fired(now);
    }

    static boolean recentlyFired(GhastEntity ghast) {
        GhastAnimationClock clock = CLOCKS.get(ghast.getUuid());
        return clock != null && clock.recentlyFired(now());
    }

    static int frame(UUID ghastId) {
        GhastAnimationClock clock = CLOCKS.get(ghastId);
        return clock == null ? (int) Math.floorMod(now() / 150, 4) : clock.frame(now());
    }

    public static void onWorldEvent(int eventId, BlockPos pos) {
        if (eventId != WorldEvents.GHAST_SHOOTS) return;
        MinecraftClient client = MinecraftClient.getInstance();
        beginTick(client);
        if (world == null) return;
        GhastEntity closest = null;
        double best = 12;
        for (GhastEntity ghast : world.getEntitiesByClass(GhastEntity.class, new Box(pos).expand(3), entity -> true)) {
            double distance = ghast.squaredDistanceTo(pos.toCenterPos());
            if (distance < best) { closest = ghast; best = distance; }
        }
        if (closest != null) {
            SEEN.put(closest.getUuid(), now());
            CLOCKS.computeIfAbsent(closest.getUuid(), id -> new GhastAnimationClock()).fired(now());
        }
    }
}
