package com.surins;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Gameplay state belongs to each server player, never to a mob or another player. */
public final class SenseGameplay {
    private static final Identifier SPEED_ID = Identifier.of("sixthsense", "sensing_speed");
    private static final EntityAttributeModifier SPEED = new EntityAttributeModifier(SPEED_ID, .10,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    public static boolean active(ServerPlayerEntity player) {
        return player.getServer()!=null && SenseServer.supported(player) && player.isAlive() && !player.isSpectator()
                && !player.isSleeping() && SenseServer.state(player.getServer()).player(player.getUuid()).active > 0;
    }
    static void update(ServerPlayerEntity player, boolean enabled) {
        var speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            if (enabled && !speed.hasModifier(SPEED_ID)) speed.addTemporaryModifier(SPEED);
            else if (!enabled && speed.hasModifier(SPEED_ID)) speed.removeModifier(SPEED_ID);
        }
        var entry = SenseServer.state(player.getServer()).player(player.getUuid());
        if (!enabled) entry.lastSneakGroundTick = Integer.MIN_VALUE;
        else if (player.isSneaking() && player.isOnGround()) entry.lastSneakGroundTick = player.age;
    }
    public static double visibility(LivingEntity target, Entity observer, double original) {
        return target instanceof ServerPlayerEntity player && observer instanceof MobEntity && active(player)
                ? original * .4 : original;
    }
    static boolean inRange(ServerPlayerEntity player, MobEntity mob) {
        if (mob.squaredDistanceTo(player) > SenseRules.RADIUS * SenseRules.RADIUS) return false;
        if (player.getY() - mob.getY() <= 24) return true;
        // Open drops retain the full range. Only an occluded lower body is treated as underground.
        Vec3d eye = player.getEyePos();
        var world = player.getServerWorld();
        for (int sample = 0; sample < 2; sample++) {
            double height = sample == 0 ? mob.getStandingEyeHeight() : mob.getHeight() * .5;
            if (world.raycast(new RaycastContext(eye, mob.getPos().add(0, height, 0),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS)
                return true;
        }
        return false;
    }
    static boolean behind(Vec3d forward, Vec3d fromTarget) {
        double horizontal = Math.sqrt(fromTarget.x * fromTarget.x + fromTarget.z * fromTarget.z);
        return horizontal > .001 && (forward.x * fromTarget.x + forward.z * fromTarget.z) / horizontal < -.5;
    }
    public static boolean assassination(net.minecraft.entity.player.PlayerEntity attacker, Entity target) {
        if (!(attacker instanceof ServerPlayerEntity player) || !(target instanceof MobEntity mob) || !active(player)
                || !mob.isAlive() || mob.getTarget() == player || !player.getMainHandStack().isIn(ItemTags.SWORDS)
                || player.isOnGround() || player.fallDistance <= 0 || player.isClimbing() || player.isTouchingWater()
                || player.hasVehicle() || player.isFallFlying() || player.isSprinting() || player.getAttackCooldownProgress(.5f) < .9f
                || !player.canSee(mob)) return false;
        var entry = SenseServer.state(player.getServer()).player(player.getUuid());
        long elapsed = (long)player.age - entry.lastSneakGroundTick;
        if (elapsed < 0 || elapsed > 16 || !behind(Vec3d.fromPolar(0, mob.getHeadYaw()), player.getPos().subtract(mob.getPos()))) return false;
        entry.lastSneakGroundTick = Integer.MIN_VALUE; // One prepared strike; no repeated aerial bonus.
        return true;
    }
    private SenseGameplay() { }
}
