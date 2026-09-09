package com.surins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import com.surins.mixin.BossBarHudAccessor;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SixthSenseClient implements ClientModInitializer {

    private static final Identifier WARNING_BOW = Identifier.of("sixthsense", "textures/gui/warning_bow.png");
    private static final Identifier WARNING_CROSSBOW = Identifier.of("sixthsense", "textures/gui/warning_crossbow.png");
    private static final Identifier WARNING_BOOM = Identifier.of("sixthsense", "textures/gui/warning_boom.png");
    private static final Identifier WARNING_GHAST = Identifier.of("sixthsense", "textures/gui/warning_ghast.png");
    private static final Identifier WARNING_SHULKER = Identifier.of("sixthsense", "textures/gui/warning_spark.png");
    private static final Identifier WARNING_WARDEN = Identifier.of("sixthsense", "textures/gui/warning_warden.png");
    private static final Identifier WARNING_ARROW = Identifier.of("sixthsense", "textures/gui/warning_arrow.png");

    private static final int ORIGINAL_FRAME_SIZE = 16;
    private static final int TEXTURE_WIDTH = 16;
    private static final int SKELETON_TEXTURE_HEIGHT = HudCharacterAnimation.WEAPON_TEXTURE_HEIGHT;
    private static final int CROSSBOW_TEXTURE_HEIGHT = HudCharacterAnimation.WEAPON_TEXTURE_HEIGHT;
    private static final int GHAST_TEXTURE_HEIGHT = HudCharacterAnimation.TEXTURE_HEIGHT;
    private static final int SHULKER_TEXTURE_HEIGHT = HudCharacterAnimation.TEXTURE_HEIGHT;
    private static final int WARDEN_TEXTURE_HEIGHT = 160;
    private static final float GHAST_DISTANCE_TEXT_SCALE = 0.62f;

    private static KeyBinding toggleKeyBinding;
    private static KeyBinding raidHudEditorKeyBinding;
    private static boolean isModActive = true;
    
    // YENİ: Mixin tarafından kontrol edilecek mevcut parlayan varlık
    public static Entity currentGlowingEntity = null;

    private static boolean isExplodingAnim = false;
    private static long explosionTriggerTime = 0;

    private enum FadeType { NORMAL, FAST_FADE }

    private static class ThreatState {
        long lastSeenTime = 0;
        long firstSeenTime = 0;
        boolean active = false;

        int drawTime = 0;
        int count = 0;
        float ratio = 0.0f;
        double distance = 0.0;
        double posX = 0.0;
        double posY = 0.0;
        double posZ = 0.0;
        String name = ""; 
        Entity trackedEntity = null;
        
        FadeType fadeType = FadeType.NORMAL;

        void update(boolean seen, int newDrawTime, int newCount, float newRatio, double newDist, double newPosX, double newPosY, double newPosZ) {
            if (seen && !this.active) {
                this.firstSeenTime = System.currentTimeMillis();
            }
            this.active = seen;
            if (seen) {
                this.lastSeenTime = System.currentTimeMillis();
                this.drawTime = newDrawTime;
                this.count = newCount;
                this.ratio = newRatio;
                this.distance = newDist;
                this.posX = newPosX;
                this.posY = newPosY;
                this.posZ = newPosZ;
            }
        }
        
        void updateRaid(boolean activeRaidMobExists, boolean visible, String mobName, double newDist, double newPosX, double newPosY, double newPosZ, Entity newTrackedEntity) {
            if (activeRaidMobExists) {
                if (visible && !this.active) {
                    this.firstSeenTime = System.currentTimeMillis();
                }
                this.active = visible;
                this.lastSeenTime = System.currentTimeMillis();
                this.name = mobName;
                this.distance = newDist;
                this.posX = newPosX;
                this.posY = newPosY;
                this.posZ = newPosZ;
                this.trackedEntity = newTrackedEntity;
            } else {
                this.active = false;
                this.trackedEntity = null;
            }
        }

        float getAlpha() {
            long elapsed = System.currentTimeMillis() - lastSeenTime;
            if (active || elapsed == 0) return 1.0f;

            if (fadeType == FadeType.FAST_FADE) {
                if (elapsed < 250) {
                    return 1.0f - (elapsed / 250.0f);
                }
                return 0.0f;
            }

            if (elapsed < 300) {
                return 1.0f - ((elapsed / 300.0f) * 0.6f);
            } else if (elapsed < 2500) {
                return 0.4f;
            } else if (elapsed < 3000) {
                return 0.4f * (1.0f - ((elapsed - 2500) / 500.0f));
            } else {
                return 0.0f;
            }
        }
        
        float getRaidAlpha(boolean mobAlive) {
            if (!mobAlive) {
                return getAlpha(); 
            }
            if (active) return 0.3f; 
            return 1.0f; 
        }

        boolean shouldRender() {
            return getAlpha() > 0.01f;
        }
        
        boolean shouldRenderRaid(boolean mobAlive) {
            return getRaidAlpha(mobAlive) > 0.01f;
        }
    }

    private static final ThreatState bowThreat = new ThreatState();
    private static final ThreatState crossbowThreat = new ThreatState();
    private static final ThreatState explosionThreat = new ThreatState();
    private static final ThreatState ghastThreat = new ThreatState();
    private static final ThreatState shulkerThreat = new ThreatState();
    private static final ThreatState wardenThreat = new ThreatState();
    private static final HudAnimationMath.WardenClock wardenClock = new HudAnimationMath.WardenClock();
    private static final WardenHudMotion wardenMotion = new WardenHudMotion();
    private static UUID displayedWarden;
    private static Object threatWorld;
    private static final ThreatState raidThreat = new ThreatState();
    
    private static boolean raidMobAlive = false;

    private static boolean hasLineOfSight(Entity entity, MinecraftClient client) {
        if (client.world == null || client.player == null) return false;
        HitResult result = client.world.raycast(new RaycastContext(
                entity.getEyePos(),
                client.player.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                entity
        ));
        return result.getType() == HitResult.Type.MISS;
    }

    private static Map<UUID, ClientBossBar> getBossBars(MinecraftClient client) {
        if (client.inGameHud == null || client.inGameHud.getBossBarHud() == null) {
            return Collections.emptyMap();
        }
        return ((BossBarHudAccessor) client.inGameHud.getBossBarHud()).getBossBars();
    }

    private static boolean isRaidActive(MinecraftClient client) {
        for (ClientBossBar bar : getBossBars(client).values()) {
            String barName = bar.getName().getString().toLowerCase(Locale.ROOT);
            if (barName.contains(Text.translatable("event.minecraft.raid").getString().toLowerCase(Locale.ROOT))
                    || barName.contains("raid") || barName.contains("baskın")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onInitializeClient() {
        SixthSenseConfig.load();
        WardenThreatTracker.initialize();
        
        explosionThreat.fadeType = FadeType.FAST_FADE;

        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sixthsense.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_0,
                "category.sixthsense"
        ));

        raidHudEditorKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sixthsense.editor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.sixthsense"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GhastThreatTracker.beginTick(client);
            if (threatWorld != client.world) {
                threatWorld = client.world;
                for (ThreatState state : new ThreatState[]{bowThreat, crossbowThreat, explosionThreat,
                        ghastThreat, shulkerThreat, wardenThreat, raidThreat}) {
                    state.active = false;
                    state.lastSeenTime = 0;
                    state.trackedEntity = null;
                }
                raidMobAlive = false;
                isExplodingAnim = false;
                displayedWarden = null;
                wardenMotion.reset();
            }
            while (raidHudEditorKeyBinding.wasPressed()) {
                if (client.currentScreen == null && client.player != null && client.world != null) {
                    client.setScreen(new SixthSenseHudEditorScreen());
                }
            }

            while (toggleKeyBinding.wasPressed()) {
                isModActive = !isModActive;
                if (client.player != null) {
                    client.player.sendMessage(SixthSenseLanguage.text(isModActive ? "message.enabled" : "message.disabled"), true);
                }
            }

            if (!isModActive || client.player == null || client.world == null) {
                currentGlowingEntity = null;
                return;
            }

            boolean tickHasBow = false; int maxBowTime = 0; int bowCount = 0;
            boolean tickHasCrossbow = false; int maxCrossbowTime = 0; int crossbowCount = 0;
            boolean tickHasExplosion = false; float maxExplosionRatio = 0.0f;
            boolean tickHasGhast = false; double closestGhastDist = 64.0;
            GhastEntity selectedGhast = null;
            double selectedGhastSqDist = Double.POSITIVE_INFINITY;
            boolean tickHasShulker = false; int shulkerCount = 0;
            boolean tickHasWarden = false; double closestWardenDist = 48.0; double wardenX = 0, wardenY = 0, wardenZ = 0;
            WardenEntity selectedWarden = null;
            UUID targetingWarden = WardenThreatTracker.targetingWarden(client);
            UUID recentAttacker = WardenThreatTracker.recentAttacker(client);
            int selectedWardenPriority = -1;
            
            boolean activeRaidMobExists = false; 
            boolean raidMobVisible = false;
            double closestRaidDist = 128.0; double raidX = 0, raidY = 0, raidZ = 0;
            String raidMobName = "";
            Entity closestRaidEntity = null;

            double closestBowSqDist = 24.0 * 24.0;
            double closestCrossbowSqDist = 24.0 * 24.0;
            double closestGhastSqDist = 64.0 * 64.0;
            double closestWardenSqDist = 48.0 * 48.0;
            double closestRaidSqDist = 128.0 * 128.0;

            boolean isRaidActiveOnClient = isRaidActive(client);

            Box raidSearchBox = client.player.getBoundingBox().expand(128.0);
            List<Entity> raidEntities = client.world.getOtherEntities(client.player, raidSearchBox);
            for (Entity entity : raidEntities) {
                double sqDistance = client.player.squaredDistanceTo(entity);

                if (entity instanceof LivingEntity living) {
                    boolean canSeeMe = hasLineOfSight(living, client);
                    boolean isThreat = (living instanceof Monster) || (living instanceof PlayerEntity);

                    if (SixthSenseConfig.instance.enableRaidWarning && living instanceof RaiderEntity raider && isRaidActiveOnClient) {
                        activeRaidMobExists = true;
                        
                        if (sqDistance < closestRaidSqDist) {
                            closestRaidSqDist = sqDistance;
                            raidX = raider.getX();
                            raidY = raider.getY();
                            raidZ = raider.getZ();
                            closestRaidDist = Math.sqrt(closestRaidSqDist);
                            closestRaidEntity = raider;
                            
                            if (raider.hasCustomName() && raider.getCustomName() != null) {
                                raidMobName = raider.getCustomName().getString();
                            } else {
                                raidMobName = raider.getType().getName().getString();
                            }
                            
                            if (canSeeMe) {
                                raidMobVisible = true;
                            }
                        }
                    }

                    if (isThreat && canSeeMe && sqDistance <= 24.0 * 24.0) {
                        ItemStack mainHand = living.getMainHandStack();
                        ItemStack offHand = living.getOffHandStack();

                        boolean hasBow = SixthSenseConfig.instance.enableBowWarning && (mainHand.isOf(Items.BOW) || offHand.isOf(Items.BOW));
                        boolean hasCrossbow = SixthSenseConfig.instance.enableCrossbowWarning && (mainHand.isOf(Items.CROSSBOW) || offHand.isOf(Items.CROSSBOW));

                        if (hasBow || hasCrossbow) {
                            if (hasBow) { tickHasBow = true; bowCount++; }
                            if (hasCrossbow) { tickHasCrossbow = true; crossbowCount++; }

                            boolean isChargedMain = mainHand.isOf(Items.CROSSBOW) && net.minecraft.item.CrossbowItem.isCharged(mainHand);
                            boolean isChargedOff = offHand.isOf(Items.CROSSBOW) && net.minecraft.item.CrossbowItem.isCharged(offHand);
                            boolean isDrawing = living.isUsingItem();

                            if (isDrawing) {
                                if (living.getActiveItem().isOf(Items.BOW) && sqDistance <= closestBowSqDist) {
                                    closestBowSqDist = sqDistance;
                                    maxBowTime = Math.min(living.getItemUseTime(), 20);
                                } else if (living.getActiveItem().isOf(Items.CROSSBOW) && sqDistance <= closestCrossbowSqDist) {
                                    closestCrossbowSqDist = sqDistance;
                                    int pullTime = Math.max(1, net.minecraft.item.CrossbowItem.getPullTime(living.getActiveItem(), living));
                                    maxCrossbowTime = Math.min(25, Math.round(25.0f * living.getItemUseTime() / pullTime));
                                }
                            } else {
                                if ((isChargedMain || isChargedOff) && sqDistance <= closestCrossbowSqDist) {
                                    closestCrossbowSqDist = sqDistance;
                                    maxCrossbowTime = 25;
                                }
                            }
                        }
                    }

                    if (canSeeMe && SixthSenseConfig.instance.enableExplosionWarning && living instanceof CreeperEntity creeper && sqDistance <= 24.0 * 24.0) {
                        float ratio = creeper.getClientFuseTime(1.0F);
                        if (ratio > 0.0f) {
                            tickHasExplosion = true;
                            maxExplosionRatio = Math.max(maxExplosionRatio, ratio);
                        }
                    }

                    if (SixthSenseConfig.instance.enableWardenWarning && living instanceof WardenEntity warden && warden.isAlive() && sqDistance <= 48.0 * 48.0) {
                        tickHasWarden = true;
                        int priority = warden.getUuid().equals(recentAttacker) ? 2 : warden.getUuid().equals(targetingWarden) ? 1 : 0;
                        if (priority > selectedWardenPriority || (priority == selectedWardenPriority && sqDistance < closestWardenSqDist)) {
                            selectedWardenPriority = priority;
                            selectedWarden = warden;
                            closestWardenSqDist = sqDistance;
                            wardenX = warden.getX();
                            wardenY = warden.getY();
                            wardenZ = warden.getZ();
                            closestWardenDist = Math.sqrt(closestWardenSqDist);
                        }
                    }

                    if (living instanceof GhastEntity ghast) GhastThreatTracker.observe(ghast);
                    if (canSeeMe && SixthSenseConfig.instance.enableGhastWarning && living instanceof GhastEntity ghast && sqDistance <= 64.0 * 64.0) {
                        if (ghast.isShooting() || GhastThreatTracker.recentlyFired(ghast)) {
                            tickHasGhast = true;
                            if (sqDistance < closestGhastSqDist) {
                                closestGhastSqDist = sqDistance;
                                closestGhastDist = Math.sqrt(sqDistance);
                            }
                            if (sqDistance < selectedGhastSqDist) {
                                selectedGhast = ghast;
                                selectedGhastSqDist = sqDistance;
                            }
                        }
                    }
                }
                else {
                    if (SixthSenseConfig.instance.enableExplosionWarning && entity instanceof net.minecraft.entity.TntEntity tnt && sqDistance <= 24.0 * 24.0) {
                        tickHasExplosion = true;
                        int fuse = tnt.getFuse();
                        float ratio = 1.0f - (fuse / 80.0f);
                        maxExplosionRatio = Math.max(maxExplosionRatio, Math.max(0, ratio));
                    }
                    if (SixthSenseConfig.instance.enableExplosionWarning && entity instanceof net.minecraft.entity.vehicle.TntMinecartEntity tntMinecart && sqDistance <= 24.0 * 24.0) {
                        int fuse = tntMinecart.getFuseTicks();
                        if (fuse > 0) {
                            tickHasExplosion = true;
                            float ratio = 1.0f - (fuse / 80.0f);
                            maxExplosionRatio = Math.max(maxExplosionRatio, Math.max(0, ratio));
                        }
                    }

                    if (SixthSenseConfig.instance.enableShulkerWarning && entity instanceof ShulkerBulletEntity && sqDistance <= 24.0 * 24.0) {
                        tickHasShulker = true;
                        shulkerCount++;
                    }
                    if (SixthSenseConfig.instance.enableGhastWarning && entity instanceof FireballEntity fireball && sqDistance <= 64.0 * 64.0) {
                        GhastThreatTracker.observe(fireball);
                        if (fireball.getOwner() != client.player && sqDistance < closestGhastSqDist) {
                            tickHasGhast = true;
                            closestGhastSqDist = sqDistance;
                            closestGhastDist = Math.sqrt(closestGhastSqDist);
                        }
                    }
                }
            }
            
            // Patlama anını tam milisaniyesinde algılayan mantık
            if (!tickHasExplosion && explosionThreat.active && explosionThreat.ratio > 0.8f) {
                isExplodingAnim = true;
                explosionTriggerTime = System.currentTimeMillis();
            } else if (tickHasExplosion && maxExplosionRatio > 0.95f && !isExplodingAnim) {
                isExplodingAnim = true;
                explosionTriggerTime = System.currentTimeMillis();
            } else if (tickHasExplosion && maxExplosionRatio < 0.8f) {
                isExplodingAnim = false;
            }
            
            raidMobAlive = activeRaidMobExists;

            bowThreat.update(tickHasBow, maxBowTime, bowCount, 0, 0, 0, 0, 0);
            crossbowThreat.update(tickHasCrossbow, maxCrossbowTime, crossbowCount, 0, 0, 0, 0, 0);
            explosionThreat.update(tickHasExplosion, 0, 0, maxExplosionRatio, 0, 0, 0, 0);
            ghastThreat.update(tickHasGhast, 0, 0, 0, closestGhastDist, 0, 0, 0);
            if (selectedGhast != null) ghastThreat.trackedEntity = selectedGhast;
            shulkerThreat.update(tickHasShulker, 0, shulkerCount, 0, 0, 0, 0, 0);
            wardenThreat.update(tickHasWarden, 0, 0, 0, closestWardenDist, wardenX, wardenY, wardenZ);
            if (selectedWarden != null) wardenThreat.trackedEntity = selectedWarden;
            raidThreat.updateRaid(activeRaidMobExists, raidMobVisible, raidMobName, closestRaidDist, raidX, raidY, raidZ, closestRaidEntity);

            // Handle entity in-world glowing via Mixin
            if (SixthSenseConfig.instance.enableRaidWarning && SixthSenseConfig.instance.enableRaidGlow && raidThreat.trackedEntity != null) {
                currentGlowingEntity = raidThreat.trackedEntity;
            } else {
                currentGlowingEntity = null;
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!isModActive) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();
            SixthSenseConfig.migrateLegacyPositions(width, height,
                    client.textRenderer.getWidth(Text.translatable("sixthsense.editor.raid_example")) + 24 + client.textRenderer.getWidth("24m"));
            wardenClock.update(System.nanoTime(), wardenThreat.distance, wardenThreat.shouldRender());

            // --- RAID GÖSTERGESİ ---
            if (raidThreat.shouldRenderRaid(raidMobAlive) && SixthSenseConfig.instance.enableRaidWarning) {
                float alpha = raidThreat.getRaidAlpha(raidMobAlive);
                
                int textAlpha = (int)(Math.max(0.1f, alpha) * 255);
                int textColor = (textAlpha << 24) | 0xFFFFFF;

                String mobName = raidThreat.name;
                if (mobName == null || mobName.isEmpty()) mobName = SixthSenseLanguage.string("element.raider");
                
                int rX = (int) Math.round(raidThreat.posX);
                int rY = (int) Math.round(raidThreat.posY);
                int rZ = (int) Math.round(raidThreat.posZ);
                int distance = (int) Math.round(raidThreat.distance);
                
                String coordsText = String.format("%s: %d %d %d", mobName, rX, rY, rZ);
                String distText = distance + "m";
                
                int coordsWidth = client.textRenderer.getWidth(coordsText);
                int distWidth = client.textRenderer.getWidth(distText);
                
                float totalRaidWidth = coordsWidth + 4 + 16 + 4 + distWidth;
                float hudScale = SixthSenseConfig.instance.raidPos.displayScale();
                float scaledTotalWidth = totalRaidWidth * hudScale;
                
                float startX;
                float raidYPos;
                
                int activeBossBarCount = getBossBars(client).size();

                startX = SixthSenseConfig.instance.raidPos.resolveX(width, scaledTotalWidth,
                        (width - scaledTotalWidth) / 2.0f);
                raidYPos = SixthSenseConfig.instance.raidPos.resolveY(height, 16 * hudScale,
                        12.0f + activeBossBarCount * 19.0f);

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                drawContext.getMatrices().push();
                drawContext.getMatrices().translate(startX, raidYPos, 0);
                drawContext.getMatrices().scale(hudScale, hudScale, 1.0f);

                drawContext.drawText(client.textRenderer, coordsText, 0, 4, textColor, true);
                
                int compassX = coordsWidth + 4;
                int compassY = 0;
                
                ItemStack compass = new ItemStack(Items.COMPASS);
                BlockPos targetPos = BlockPos.ofFloored(raidThreat.posX, raidThreat.posY, raidThreat.posZ);
                
                if (client.world != null) {
                    GlobalPos globalPos = GlobalPos.create(client.world.getRegistryKey(), targetPos);
                    compass.set(DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(Optional.of(globalPos), false));
                }
                compass.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);

                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                drawContext.drawItem(compass, compassX, compassY);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

                float distDrawX = compassX + 18;
                float distDrawY = 4;
                drawContext.drawText(client.textRenderer, distText, (int)distDrawX, (int)distDrawY, textColor, true);
                
                drawContext.getMatrices().pop();
                RenderSystem.disableBlend();
            }

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            
            float[] row = HudLayoutMath.warningRow(width, new float[]{
                    autoWidth(bowThreat, SixthSenseConfig.instance.bowPos),
                    autoWidth(crossbowThreat, SixthSenseConfig.instance.crossbowPos),
                    autoWidth(explosionThreat, SixthSenseConfig.instance.explosionPos),
                    autoWidth(ghastThreat, SixthSenseConfig.instance.ghastPos),
                    autoWidth(shulkerThreat, SixthSenseConfig.instance.shulkerPos),
                    autoWidth(wardenThreat, SixthSenseConfig.instance.wardenPos)}, true,
                    18 * SixthSenseConfig.instance.wardenPos.displayScale());
            float targetScreenY = (float)height - 52;

            if (bowThreat.shouldRender()) {
                drawWarningWithLayout(drawContext, client, WARNING_BOW, bowThreat, SixthSenseConfig.instance.bowPos,
                    HudCharacterAnimation.WEAPON_FRAMES, 32, 32, SKELETON_TEXTURE_HEIGHT, -1f, true, row[0], targetScreenY);
            }

            if (crossbowThreat.shouldRender()) {
                drawWarningWithLayout(drawContext, client, WARNING_CROSSBOW, crossbowThreat, SixthSenseConfig.instance.crossbowPos,
                    HudCharacterAnimation.WEAPON_FRAMES, 32, 32, CROSSBOW_TEXTURE_HEIGHT, -1f, true, row[1], targetScreenY);
            }

            if (explosionThreat.shouldRender()) {
                drawWarningWithLayout(drawContext, client, WARNING_BOOM, explosionThreat, SixthSenseConfig.instance.explosionPos,
                    10, 32, 160, 64, explosionThreat.ratio, false, row[2], targetScreenY);
            }

            if (ghastThreat.shouldRender()) {
                float lastX = row[3];
                drawWarningWithLayout(drawContext, client, WARNING_GHAST, ghastThreat, SixthSenseConfig.instance.ghastPos,
                    HudCharacterAnimation.FRAME_COUNT, 32, 32, GHAST_TEXTURE_HEIGHT, -1f, false, row[3], targetScreenY);
                if (SixthSenseConfig.instance.showGhastDistance) {
                    SixthSenseConfig.AnimPos pos = SixthSenseConfig.instance.ghastPos;
                    float scale = pos.displayScale();
                    float drawX = pos.resolveX(client.getWindow().getScaledWidth(), ORIGINAL_FRAME_SIZE * scale, lastX);
                    float drawY = pos.resolveY(client.getWindow().getScaledHeight(), ORIGINAL_FRAME_SIZE * scale, targetScreenY);
                    float alpha = ghastThreat.getAlpha();
                    String distanceText = (int)ghastThreat.distance + "m";
                    float iconSize = ORIGINAL_FRAME_SIZE * scale;
                    int nativeWidth = Math.max(1, client.textRenderer.getWidth(distanceText));
                    int nativeHeight = Math.max(1, client.textRenderer.fontHeight);
                    float textScale = Math.min(GHAST_DISTANCE_TEXT_SCALE * scale,
                            Math.min(iconSize * 0.72f / nativeWidth, iconSize * 0.50f / nativeHeight));
                    float textX = drawX + iconSize - nativeWidth * textScale - 0.5f;
                    float textY = drawY + iconSize - nativeHeight * textScale - 0.5f;
                    drawContext.getMatrices().push();
                    int textColor = ((int)(alpha * 255) << 24) | 0xFFFFFF;
                    drawContext.getMatrices().translate(textX, textY, 0);
                    drawContext.getMatrices().scale(textScale, textScale, 1.0f);
                    drawContext.drawText(client.textRenderer, distanceText, 0, 0, textColor, true);
                    drawContext.getMatrices().pop();
                }
            }

            if (shulkerThreat.shouldRender()) {
                drawWarningWithLayout(drawContext, client, WARNING_SHULKER, shulkerThreat, SixthSenseConfig.instance.shulkerPos,
                    HudCharacterAnimation.FRAME_COUNT, 32, 32, SHULKER_TEXTURE_HEIGHT, -1f, true, row[4], targetScreenY);
            }

            if (wardenThreat.shouldRender()) {
                float alpha = wardenThreat.getAlpha();

                SixthSenseConfig.AnimPos pos = SixthSenseConfig.instance.wardenPos;
                
                float scale = pos.displayScale();
                float drawX = pos.resolveX(client.getWindow().getScaledWidth(), ORIGINAL_FRAME_SIZE * scale, row[5]);
                float drawY = pos.resolveY(client.getWindow().getScaledHeight(), ORIGINAL_FRAME_SIZE * scale, targetScreenY);

                int wardenFrame = wardenClock.frame();
                int vOffset = wardenFrame * ORIGINAL_FRAME_SIZE;
                int halfSize = ORIGINAL_FRAME_SIZE / 2;
                HudAnimationMath.Heartbeat heartbeat = wardenClock.heartbeat(scale, true);
                
                float centerX = drawX + (ORIGINAL_FRAME_SIZE * scale) / 2.0f;
                float centerY = drawY + (ORIGINAL_FRAME_SIZE * scale) / 2.0f;

                Entity tracked = wardenThreat.trackedEntity;
                UUID wardenId = tracked == null ? null : tracked.getUuid();
                if (!java.util.Objects.equals(displayedWarden, wardenId)) {
                    displayedWarden = wardenId;
                    wardenMotion.resetReticle();
                }
                var reticle = WardenThreatTracker.sample(client, wardenId);
                float tickProgress = tickDelta.getTickDelta(false);
                var playerPos = client.player.getLerpedPos(tickProgress);
                var wardenPos = tracked != null ? tracked.getLerpedPos(tickProgress)
                        : new net.minecraft.util.math.Vec3d(wardenThreat.posX, wardenThreat.posY, wardenThreat.posZ);
                double dX = wardenPos.x - playerPos.x;
                double dZ = wardenPos.z - playerPos.z;
                float angleToWarden = (float) Math.toDegrees(Math.atan2(dZ, dX)) - 90.0f;
                wardenMotion.update(System.nanoTime(), angleToWarden - client.player.getYaw(tickProgress),
                        wardenThreat.active && reticle.showReticle());
                float arrowRotation = wardenMotion.angle();

                drawContext.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                
                drawContext.getMatrices().push();
                drawContext.getMatrices().translate(centerX, centerY, 0);
                drawContext.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(arrowRotation));
                float orbitRadius = wardenMotion.arrowRadius() * scale;
                drawContext.getMatrices().translate(0, -orbitRadius, 0);
                float arrowScale = scale * wardenMotion.arrowScale();
                drawContext.getMatrices().scale(arrowScale, arrowScale, 1.0f);
                drawContext.drawTexture(WARNING_ARROW, -halfSize, -halfSize, 0, 0, ORIGINAL_FRAME_SIZE, ORIGINAL_FRAME_SIZE, TEXTURE_WIDTH, ORIGINAL_FRAME_SIZE);
                drawContext.getMatrices().pop();

                if (heartbeat.motionBlurAlpha() > 0.0f) {
                    drawContext.setShaderColor(1.0f, 1.0f, 1.0f, alpha * heartbeat.motionBlurAlpha());
                    drawContext.getMatrices().push();
                    drawContext.getMatrices().translate(centerX - heartbeat.jitterX * 0.6f, centerY - heartbeat.jitterY * 0.6f, 0);
                    float blurScale = scale * heartbeat.scaleMultiplier * 0.985f;
                    drawContext.getMatrices().scale(blurScale, blurScale, 1.0f);
                    drawContext.drawTexture(WARNING_WARDEN, -halfSize, -halfSize, 0, vOffset, ORIGINAL_FRAME_SIZE, ORIGINAL_FRAME_SIZE, TEXTURE_WIDTH, WARDEN_TEXTURE_HEIGHT);
                    drawContext.getMatrices().pop();
                }

                drawContext.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                drawContext.getMatrices().push();
                drawContext.getMatrices().translate(centerX + heartbeat.jitterX, centerY + heartbeat.jitterY, 0);
                float heartScale = scale * heartbeat.scaleMultiplier;
                drawContext.getMatrices().scale(heartScale, heartScale, 1.0f);
                drawContext.drawTexture(WARNING_WARDEN, -halfSize, -halfSize, 0, vOffset, ORIGINAL_FRAME_SIZE, ORIGINAL_FRAME_SIZE, TEXTURE_WIDTH, WARDEN_TEXTURE_HEIGHT);
                drawContext.getMatrices().pop();

                drawContext.setShaderColor(1, 1, 1, 1);
                WardenReticleRenderer.draw(drawContext, centerX, centerY, scale,
                        alpha * wardenMotion.reticleAlpha(), reticle.hitPulse(),
                        client.player.getHealth() / Math.max(1, client.player.getMaxHealth()));

                if (SixthSenseConfig.instance.showWardenDistance) {
                    String distanceText = (int)wardenThreat.distance + "m";
                    drawContext.getMatrices().push();
                    float textScale = 0.6f;
                    drawContext.getMatrices().scale(textScale, textScale, 1.0f);
                    int textColor = ((int)(alpha * 255) << 24) | 0xFFFFFF;
                    drawContext.drawText(client.textRenderer, distanceText, (int)((drawX + 8.0f * scale) / textScale), (int)((drawY + 6.0f * scale) / textScale), textColor, true);
                    drawContext.getMatrices().pop();
                }
            }

            drawContext.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        });
    }

    private static float drawWarningWithLayout(net.minecraft.client.gui.DrawContext drawContext, MinecraftClient client, Identifier texture, ThreatState state, 
                                    SixthSenseConfig.AnimPos pos, int maxFrames, int frameSize, int textureWidth, int textureHeight, float ratioOverride, boolean booleanCount, float dynX, float dynY) {
        float alpha = state.getAlpha();
        int frame;
        if (texture.equals(WARNING_BOOM)) {
            if (isExplodingAnim) {
                if (state.active) {
                    frame = 5; // Patlama tetiklendi ama bomba henüz oyundan silinmedi (ilk patlama karesinde bekle)
                } else {
                    long elapsedFade = System.currentTimeMillis() - state.lastSeenTime;
                    // FAST_FADE sönümleme süresi olan 250ms ile animasyon karelerini tam eşzamanlı ilerlet.
                    // Obje dünyadan silindiği an animasyon akar ve sönümleme bittiği tam o an animasyon da sonlanır.
                    int f = 5 + (int) (elapsedFade / 50); 
                    frame = Math.min(f, maxFrames - 1);
                }
            } else {
                long fuseElapsed = System.currentTimeMillis() - state.firstSeenTime;
                if (fuseElapsed < 100) {
                    frame = 0; // Orijinal ilk kare sadece 1 kere (0.1 saniye) oynatılır (Görsel 1)
                } else {
                    // İlk kareden sonra sadece 2, 3, 4 ve 5. kareler (index 1, 2, 3, 4) döngü halinde oynatılır
                    frame = 1 + (int) (((fuseElapsed - 100) / 100) % 4);
                }
            }
        } else if (ratioOverride >= 0) {
            frame = (int) (ratioOverride * (maxFrames - 0.01f));
            if (frame >= maxFrames) frame = maxFrames - 1;
        } else {
            if (texture.equals(WARNING_BOW)) {
                frame = HudCharacterAnimation.weaponFrame(state.drawTime / 20.0f);
            } else if (texture.equals(WARNING_CROSSBOW)) {
                frame = HudCharacterAnimation.weaponFrame(state.drawTime / 25.0f);
            } else if (texture.equals(WARNING_GHAST)) {
                frame = GhastThreatTracker.frame(state.trackedEntity == null ? null : state.trackedEntity.getUuid());
            } else if (texture.equals(WARNING_SHULKER)) {
                frame = HudCharacterAnimation.frameAt(System.nanoTime());
            } else {
                frame = 0;
            }
        }
        
        float scale = pos.displayScale();
        float drawX = pos.resolveX(client.getWindow().getScaledWidth(), ORIGINAL_FRAME_SIZE * scale, dynX);
        float drawY = pos.resolveY(client.getWindow().getScaledHeight(), ORIGINAL_FRAME_SIZE * scale, dynY); 

        int columns = Math.max(1, textureWidth / frameSize);
        int uOffset = (frame % columns) * frameSize;
        int vOffset = (frame / columns) * frameSize;

        float centerX = drawX + (ORIGINAL_FRAME_SIZE * scale) / 2.0f;
        float centerY = drawY + (ORIGINAL_FRAME_SIZE * scale) / 2.0f;
        
        drawContext.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(centerX, centerY, 0);
        drawContext.getMatrices().scale(scale, scale, 1.0f);
        
        float spriteScale = (float) ORIGINAL_FRAME_SIZE / frameSize;
        drawContext.getMatrices().scale(spriteScale, spriteScale, 1.0f);
        
        int actualHalfSize = frameSize / 2;
        drawContext.drawTexture(texture, -actualHalfSize, -actualHalfSize, uOffset, vOffset, frameSize, frameSize, textureWidth, textureHeight);
        drawContext.getMatrices().pop();
        
        if (booleanCount && state.count > 1) {
            drawWarningCount(drawContext, client, state.count, drawX, drawY, scale, alpha);
        }
        
        if (pos.x == -1) {
            return dynX + (ORIGINAL_FRAME_SIZE * scale) + 4.0f;
        }
        return dynX;
    }

    private static float autoWidth(ThreatState state, SixthSenseConfig.AnimPos pos) {
        return state.shouldRender() && pos.x == -1 ? ORIGINAL_FRAME_SIZE * pos.displayScale() : 0;
    }

    private static void drawWarningCount(net.minecraft.client.gui.DrawContext drawContext, MinecraftClient client,
                                         int count, float drawX, float drawY, float iconScale, float alpha) {
        String countText = String.valueOf(count);
        float iconSize = ORIGINAL_FRAME_SIZE * iconScale;
        float maxTextWidth = iconSize * 0.86f;
        float maxTextHeight = iconSize * 0.62f;

        float textScale = Math.min(0.76f * iconScale, maxTextHeight / client.textRenderer.fontHeight);
        textScale = Math.min(textScale, maxTextWidth / client.textRenderer.getWidth(countText));

        // Keep the counter readable without letting it overwhelm its icon at large GUI scales.
        float guiScale = (float) client.getWindow().getScaleFactor();
        textScale *= Math.min(1.0f, 3.0f / Math.max(1.0f, guiScale));

        float textWidth = client.textRenderer.getWidth(countText) * textScale;
        float textHeight = client.textRenderer.fontHeight * textScale;
        float textX = drawX + iconSize - textWidth - 0.5f;
        float textY = drawY + iconSize - textHeight - 0.5f;
        int textColor = ((int) (alpha * 255) << 24) | 0xFFFFFF;

        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(textX, textY, 0);
        drawContext.getMatrices().scale(textScale, textScale, 1.0f);
        drawContext.drawText(client.textRenderer, countText, 0, 0, textColor, true);
        drawContext.getMatrices().pop();
    }
}
