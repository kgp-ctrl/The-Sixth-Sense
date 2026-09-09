package com.surins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class SixthSenseConfig {

    public static class AnimPos {
        public int x = -1;
        public int y = -1;
        public float scale = 0.7f; 
        // Null anchors identify legacy absolute GUI coordinates. x/y retain Auto (-1).
        public Float anchorX;
        public Float anchorY;
        public float offsetX;
        public float offsetY;
        
        public AnimPos() {}
        public AnimPos(float defaultScale) { this.scale = defaultScale; }

        public float displayScale() {
            return scale * HudLayoutMath.scaleCompensation(MinecraftClient.getInstance().getWindow().getScaleFactor());
        }

        public float resolveX(int screenWidth, float elementWidth, float automatic) {
            return x == -1 ? automatic : anchorX == null ? HudLayoutMath.clamp(x, elementWidth, screenWidth)
                    : HudLayoutMath.resolve(offsetX, elementWidth, screenWidth, anchorX);
        }

        public float resolveY(int screenHeight, float elementHeight, float automatic) {
            return y == -1 ? automatic : anchorY == null ? HudLayoutMath.clamp(y, elementHeight, screenHeight)
                    : HudLayoutMath.resolve(offsetY, elementHeight, screenHeight, anchorY);
        }

        public void place(float left, float top, float elementWidth, float elementHeight, int width, int height) {
            x = Math.round(left);
            y = Math.round(top);
            anchorX = HudLayoutMath.nearestAnchor(left, elementWidth, width);
            anchorY = HudLayoutMath.nearestAnchor(top, elementHeight, height);
            offsetX = HudLayoutMath.offset(left, elementWidth, width, anchorX);
            offsetY = HudLayoutMath.offset(top, elementHeight, height, anchorY);
        }

        public void setX(int value) {
            if (x != value) { x = value; anchorX = null; }
        }

        public void setY(int value) {
            if (y != value) { y = value; anchorY = null; }
        }

        public void refreshCoordinates(int width, int height, float contentWidth) {
            // The settings fields show current GUI coordinates; anchors remain authoritative.
            float visibleScale = displayScale();
            if (x != -1) x = Math.round(resolveX(width, contentWidth * visibleScale, 0));
            if (y != -1) y = Math.round(resolveY(height, 16 * visibleScale, 0));
        }

        public void reset(float defaultScale) {
            x = y = -1;
            anchorX = anchorY = null;
            offsetX = offsetY = 0;
            scale = defaultScale;
        }
    }

    /** Convert old saved coordinates once, using the first available viewport. */
    public static void migrateLegacyPositions(int width, int height, float raidContentWidth) {
        boolean changed = false;
        AnimPos[] positions = {instance.raidPos, instance.bowPos, instance.crossbowPos, instance.explosionPos,
                instance.ghastPos, instance.shulkerPos, instance.wardenPos};
        for (AnimPos pos : positions) {
            float elementWidth = (pos == instance.raidPos ? raidContentWidth : 16) * pos.displayScale();
            float elementHeight = 16 * pos.displayScale();
            if (pos.x != -1 && pos.anchorX == null) {
                pos.anchorX = HudLayoutMath.nearestAnchor(pos.x, elementWidth, width);
                pos.offsetX = HudLayoutMath.offset(pos.x, elementWidth, width, pos.anchorX);
                changed = true;
            }
            if (pos.y != -1 && pos.anchorY == null) {
                pos.anchorY = HudLayoutMath.nearestAnchor(pos.y, elementHeight, height);
                pos.offsetY = HudLayoutMath.offset(pos.y, elementHeight, height, pos.anchorY);
                changed = true;
            }
        }
        if (changed) save();
    }

    // Genel Tehlike Ayarları
    public boolean enableBowWarning = true;
    public boolean enableCrossbowWarning = true;
    public boolean enableExplosionWarning = true;
    public boolean enableGhastWarning = true;
    public boolean enableShulkerWarning = true;
    public boolean enableWardenWarning = true;
    
    // Baskın (Raid) Ayarları
    public boolean enableRaidWarning = true;
    public boolean enableRaidGlow = true; // Entity highlight effect

    // İsteğe Bağlı Özellikler
    public boolean showWardenDistance = false;
    public boolean showGhastDistance = true;
    public boolean editorGridEnabled = true;
    
    // Animasyon Pozisyonları
    public AnimPos raidPos = new AnimPos(0.6f); // 0.8'den 0.6'ya düşürüldü (-25%)
    public AnimPos bowPos = new AnimPos();
    public AnimPos crossbowPos = new AnimPos();
    public AnimPos explosionPos = new AnimPos();
    public AnimPos ghastPos = new AnimPos();
    public AnimPos shulkerPos = new AnimPos();
    public AnimPos wardenPos = new AnimPos();

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("sixthsense.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static SixthSenseConfig instance = new SixthSenseConfig();

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
                SixthSenseConfig loadedConfig = GSON.fromJson(reader, SixthSenseConfig.class);
                instance = loadedConfig != null ? loadedConfig : new SixthSenseConfig();
                ensureAnimationPositions();
            } catch (IOException | RuntimeException e) {
                e.printStackTrace();
                instance = new SixthSenseConfig();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.toPath().getParent());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(instance, writer);
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }

    private static void ensureAnimationPositions() {
        if (instance.raidPos == null) instance.raidPos = new AnimPos(0.6f);
        if (instance.bowPos == null) instance.bowPos = new AnimPos();
        if (instance.crossbowPos == null) instance.crossbowPos = new AnimPos();
        if (instance.explosionPos == null) instance.explosionPos = new AnimPos();
        if (instance.ghastPos == null) instance.ghastPos = new AnimPos();
        if (instance.shulkerPos == null) instance.shulkerPos = new AnimPos();
        if (instance.wardenPos == null) instance.wardenPos = new AnimPos();
    }
}
