package com.surins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
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
            return x == -1 ? HudLayoutMath.clamp(automatic, elementWidth, screenWidth) : anchorX == null ? HudLayoutMath.clamp(x, elementWidth, screenWidth)
                    : HudLayoutMath.resolve(offsetX, elementWidth, screenWidth, anchorX);
        }

        public float resolveY(int screenHeight, float elementHeight, float automatic) {
            return y == -1 ? HudLayoutMath.clamp(automatic, elementHeight, screenHeight) : anchorY == null ? HudLayoutMath.clamp(y, elementHeight, screenHeight)
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

    // Global policy is always accessible through H, without optional config mods.
    public boolean hudEnabled = true;
    public boolean showInCreative = false;
    public float phantomScale = 0.65f;
    public boolean enablePhantomWarning = true;
    public boolean enableAimAssist = true;
    public boolean reduceMotion = false;
    public boolean prioritizeThreats = false;
    public float warningOpacity = 1;
    public boolean innerVoice = false;

    public float phantomDisplayScale() {
        return PhantomHudMath.safeScale(phantomScale)
                * HudLayoutMath.scaleCompensation(MinecraftClient.getInstance().getWindow().getScaleFactor());
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

    private static Path configFile() { return FabricLoader.getInstance().getConfigDir().resolve("sixthsense.json"); }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static SixthSenseConfig instance = new SixthSenseConfig();

    public static void load() {
        Path path = configFile();
        if (!Files.exists(path)) { save(); return; }
        try {
            instance = decode(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            LogUtils.getLogger().warn("Could not read Sixth Sense settings; trying last valid backup", error);
            try {
                instance = decode(Files.readString(ConfigFiles.backup(path), StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException backupError) {
                instance = new SixthSenseConfig();
            }
            // Preserve malformed input for recovery instead of overwriting the only copy.
            try { Files.copy(path, path.resolveSibling("sixthsense.corrupt-" + System.currentTimeMillis() + ".json")); }
            catch (IOException preserveError) { LogUtils.getLogger().warn("Could not preserve damaged settings", preserveError); }
        }
        instance.validate();
    }

    static SixthSenseConfig decode(String json) {
        SixthSenseConfig config = GSON.fromJson(json, SixthSenseConfig.class);
        if (config == null) throw new IllegalArgumentException("Empty settings");
        config.validate();
        return config;
    }

    private static boolean validJson(String json) {
        try { decode(json); return true; } catch (RuntimeException ignored) { return false; }
    }

    public static void save() {
        instance.validate();
        try {
            ConfigFiles.write(configFile(), GSON.toJson(instance), SixthSenseConfig::validJson);
        } catch (IOException | RuntimeException error) {
            LogUtils.getLogger().warn("Could not save Sixth Sense settings; previous file retained", error);
        }
    }

    void validate() {
        if (raidPos == null) raidPos = new AnimPos(.6f);
        if (bowPos == null) bowPos = new AnimPos();
        if (crossbowPos == null) crossbowPos = new AnimPos();
        if (explosionPos == null) explosionPos = new AnimPos();
        if (ghastPos == null) ghastPos = new AnimPos();
        if (shulkerPos == null) shulkerPos = new AnimPos();
        if (wardenPos == null) wardenPos = new AnimPos();
        for (AnimPos pos : new AnimPos[]{raidPos,bowPos,crossbowPos,explosionPos,ghastPos,shulkerPos,wardenPos}) {
            pos.scale = finite(pos.scale, .1f, 5, pos == raidPos ? .6f : .7f);
            pos.x = Math.max(-1, Math.min(32767, pos.x)); pos.y = Math.max(-1, Math.min(32767, pos.y));
            if (pos.anchorX != null && (!Float.isFinite(pos.anchorX) || pos.anchorX < 0 || pos.anchorX > 1)) pos.anchorX = null;
            if (pos.anchorY != null && (!Float.isFinite(pos.anchorY) || pos.anchorY < 0 || pos.anchorY > 1)) pos.anchorY = null;
            pos.offsetX = finite(pos.offsetX, -32767, 32767, 0); pos.offsetY = finite(pos.offsetY, -32767, 32767, 0);
        }
        phantomScale = finite(phantomScale, .1f, 5, .65f);
        warningOpacity = finite(warningOpacity, .2f, 1, 1);
    }
    private static float finite(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    /** Presets tune presentation only: saved placements and warning switches stay intact. */
    static void applyPreset(int preset) {
        instance.reduceMotion = preset == 0;
        instance.prioritizeThreats = preset == 0;
        instance.warningOpacity = preset == 0 ? .8f : 1;
        instance.showGhastDistance = preset != 0;
        instance.showWardenDistance = preset == 2;
        save();
    }
    static boolean saveProfile() {
        try { ConfigFiles.write(configFile().resolveSibling("sixthsense-profile.json"), GSON.toJson(instance), SixthSenseConfig::validJson); return true; }
        catch (IOException | RuntimeException error) { LogUtils.getLogger().warn("Could not save HUD profile", error); return false; }
    }
    static boolean restoreProfile() {
        try {
            instance = decode(Files.readString(configFile().resolveSibling("sixthsense-profile.json"), StandardCharsets.UTF_8));
            save(); return true;
        } catch (IOException | RuntimeException error) { LogUtils.getLogger().warn("Could not restore HUD profile", error); return false; }
    }
}
