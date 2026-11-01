package com.surins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.block.ButtonBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Visual feedback only: never changes aim, fires, sends packets or spawns projectiles. */
public final class BowAimAssist {
    private static final ReticleAnimation ANIMATION = new ReticleAnimation();
    private static Object world, player;
    private static long checked;
    private static boolean predicted;
    private static LivingEntity hitTarget;
    private static double impactSpeed;
    private static Box targetBox;
    private BowAimAssist() { }

    static void tick(MinecraftClient client) {
        if (world != client.world || player != client.player || !allowed(client)) {
            reset(); world = client.world; player = client.player;
        }
        if (!drawing(client)) { predicted = false; hitTarget = null; targetBox = null; ANIMATION.target(false, System.nanoTime()); checked = 0; }
    }
    private static void reset() { ANIMATION.reset(); predicted = false; hitTarget = null; targetBox = null; checked = 0; }
    private static boolean allowed(MinecraftClient client) {
        return SixthSenseClient.warningsAllowed(client) && SixthSenseConfig.instance.enableAimAssist
                && client.currentScreen == null && !client.options.hudHidden
                && client.options.getPerspective().isFirstPerson();
    }
    private static boolean drawing(MinecraftClient client) {
        return client.player != null && client.player.isUsingItem()
                && client.player.getActiveItem().getItem() instanceof BowItem
                && !client.player.getProjectileType(client.player.getActiveItem()).isEmpty();
    }
    static void update(MinecraftClient client) {
        if (!allowed(client)) { reset(); return; }
        if (client.getDebugHud().shouldShowDebugHud() && !client.player.hasReducedDebugInfo()
                && !client.options.getReducedDebugInfo().getValue()) { reset(); return; }
        long now = System.nanoTime();
        boolean drawing = drawing(client);
        // Shared by world highlighting and HUD, so there is only one trace per sampling interval.
        if (drawing && (checked == 0 || now - checked >= 16_000_000L)) {
            predicted = predict(client); checked = now;
        }
        if (!drawing) { predicted = false; hitTarget = null; targetBox = null; checked = 0; }
        ANIMATION.target(drawing && predicted && distantTarget(client), now);
    }
    public static void render(DrawContext context, MinecraftClient client) {
        update(client);
        draw(context, (context.getScaledWindowWidth() - 15) / 2 + 7,
                (context.getScaledWindowHeight() - 15) / 2 + 7, ANIMATION.sample(System.nanoTime()),
                SixthSenseConfig.instance.warningOpacity);
    }

    static LivingEntity highlightTarget(MinecraftClient client, float tickDelta) {
        update(client);
        if (!predicted || hitTarget == null || !hitTarget.isAlive() || hitTarget.isInvisible()
                || !distantTarget(client)) return null;
        var camera = client.gameRenderer.getCamera();
        return ArcTargetMath.belowReticle(hitTarget.getBoundingBox(), camera.getPos(), camera.getRotation(),
                client.gameRenderer.getFov(camera, tickDelta, true), client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight()) ? hitTarget : null;
    }
    private static boolean distantTarget(MinecraftClient client) {
        return targetBox != null && ArcTargetMath.outsideNearRange(targetBox, client.player.getEyePos());
    }
    static int highlightColor(MinecraftClient client, LivingEntity target) {
        return BowDamageEstimate.color(target.getHealth() + target.getAbsorptionAmount(),
                BowDamageEstimate.damage(client.player, target, impactSpeed));
    }

    private static boolean predict(MinecraftClient client) {
        hitTarget = null; targetBox = null; impactSpeed = 0;
        var shooter = client.player;
        float pull = BowItem.getPullProgress(shooter.getItemUseTime());
        if (pull < .1f) return false; // Vanilla does not fire below this draw strength.
        Vec3d movement = shooter.getMovement();
        Vec3d velocity = shooter.getRotationVec(1).multiply(pull * 3)
                .add(movement.x, shooter.isOnGround() ? 0 : movement.y, movement.z);
        Vec3d start = new Vec3d(shooter.getX(), shooter.getEyeY() - (double) .1f, shooter.getZ());
        return BallisticTrace.hitsTarget(start, velocity, new BallisticTrace.Scene() {
            public boolean water(Vec3d pos) { return client.world.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER); }
            public BallisticTrace.Hit hit(Vec3d from, Vec3d to) {
                if (!client.world.isChunkLoaded(BlockPos.ofFloored(to))) return BallisticTrace.Hit.BLOCKED;
                var block = client.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, shooter) {
                    @Override public net.minecraft.util.shape.VoxelShape getBlockShape(net.minecraft.block.BlockState state,
                            net.minecraft.world.BlockView world, BlockPos pos) {
                        return state.getBlock() instanceof ButtonBlock ? state.getOutlineShape(world, pos)
                                : super.getBlockShape(state, world, pos);
                    }
                });
                boolean blocked = block.getType() != HitResult.Type.MISS;
                boolean button = blocked && client.world.getBlockState(block.getBlockPos()).getBlock() instanceof ButtonBlock;
                Vec3d end = blocked ? block.getPos() : to;
                double nearest = from.squaredDistanceTo(end);
                Entity first = null;
                for (Entity candidate : client.world.getOtherEntities(shooter, new Box(from, end).expand(1),
                        e -> !e.isSpectator() && e.canBeHitByProjectile() && !e.isConnectedThroughVehicle(shooter))) {
                    Box box = candidate.getBoundingBox().expand(.3f);
                    var intersection = box.raycast(from, end);
                    double distance = box.contains(from) ? 0 : intersection.map(from::squaredDistanceTo).orElse(Double.POSITIVE_INFINITY);
                    if (distance < nearest || !blocked && distance == nearest) {
                        nearest = distance; first = candidate;
                    }
                }
                if (first != null) {
                    boolean living = first instanceof LivingEntity && first.isAlive()
                            && (!(first instanceof PlayerEntity other) || shooter.shouldDamagePlayer(other));
                    if (living) { hitTarget = (LivingEntity) first; impactSpeed = from.distanceTo(to); }
                    boolean target = living || first instanceof EndCrystalEntity && first.isAlive();
                    if (target) targetBox = first.getBoundingBox();
                    return target ? BallisticTrace.Hit.TARGET : BallisticTrace.Hit.BLOCKED;
                }
                if (button) {
                    targetBox = client.world.getBlockState(block.getBlockPos()).getOutlineShape(client.world,
                            block.getBlockPos()).getBoundingBox().offset(block.getBlockPos());
                    return BallisticTrace.Hit.TARGET;
                }
                return blocked ? BallisticTrace.Hit.BLOCKED : BallisticTrace.Hit.NONE;
            }
        });
    }

    static void draw(DrawContext context, int x, int y, ReticleAnimation.Frame frame, float opacity) {
        AimCrosshair.draw(context, x, y, frame, opacity);
    }
}
