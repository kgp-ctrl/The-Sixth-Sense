package com.surins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.imageio.ImageIO;

/** Dependency-free regression checks; run by Gradle check/build. */
public final class HudRegressionChecks {
    public static void main(String[] args) throws Exception {
        sprintDoesNotRestartAnimation();
        animationIsIndependentOfUptimeAndFrameRate();
        anchorsSurviveGuiScaleChangesAndSaving();
        scaleCompensationIsModest();
        translationsAreComplete();
        characterSpritesMatchRenderer();
        thornSpriteIsEditableAndAligned();
        GameplayHudRegressionChecks.run();
        WardenThreatRegressionChecks.run();
        HudTextureReloadChecks.run();
        System.out.println("HUD regression checks passed: heartbeat continuity, anchors, languages, sprites, weapon poses, Ghast reactions, Warden layout, compass and damage feedback.");
    }

    private static void sprintDoesNotRestartAnimation() {
        var clock = new HudAnimationMath.WardenClock();
        long start = 8_000_000_000_000_000L;
        clock.update(start, 48, true);
        int previous = clock.frame();
        int wraps = 0;
        float previousScale = clock.heartbeat(0.7f, true).scaleMultiplier;
        boolean pulseMoved = false;
        for (int frame = 1; frame <= 3600; frame++) {
            // Repeated approaches/retreats plus abrupt switches to another nearby Warden.
            double distance = frame % 480 < 240 ? 48 - (frame % 240) * 0.2 : (frame % 240) * 0.2;
            clock.update(start + frame * 1_000_000_000L / 120, distance, true);
            int current = clock.frame();
            require((current - previous + 10) % 10 <= 1, "Sprinting skipped or restarted a sprite frame");
            if (previous == 9 && current == 0) wraps++;
            var pulse = clock.heartbeat(0.7f, true);
            require(Float.isFinite(pulse.scaleMultiplier) && Float.isFinite(pulse.jitterX), "Invalid pulse");
            require(Math.abs(pulse.scaleMultiplier - previousScale) < 0.02, "Pulse jumped while distance changed");
            pulseMoved |= Math.abs(pulse.scaleMultiplier - previousScale) > 0.0001;
            previousScale = pulse.scaleMultiplier;
            previous = current;
        }
        require(wraps > 10 && pulseMoved, "Animation stopped progressing");
        clock.update(start + 31_000_000_000L, 48, false);
        require(clock.frame() == previous, "Hidden animation reset its phase");
        clock.update(start + 31_008_000_000L, 0, true);
        require((clock.frame() - previous + 10) % 10 <= 1, "Reacquiring a Warden restarted the animation");
    }

    private static void animationIsIndependentOfUptimeAndFrameRate() {
        float reference = runClock(60, 0);
        for (int fps : new int[]{30, 60, 144}) {
            close(reference, runClock(fps, 8_000_000_000_000_000L), "Pulse depends on FPS or system uptime");
        }
    }

    private static float runClock(int fps, long start) {
        var clock = new HudAnimationMath.WardenClock();
        clock.update(start, 12, true);
        for (int frame = 1; frame <= fps * 10; frame++) {
            clock.update(start + frame * 1_000_000_000L / fps, 12, true);
        }
        return clock.heartbeat(0.7f, true).scaleMultiplier;
    }

    private static void anchorsSurviveGuiScaleChangesAndSaving() {
        Gson gson = new Gson();
        float originalSize = 16 * 0.7f;
        float newSize = originalSize * HudLayoutMath.scaleCompensation(3);
        for (float anchorX : new float[]{0, 0.5f, 1}) {
            for (float anchorY : new float[]{0, 0.5f, 1}) {
                var pos = new SixthSenseConfig.AnimPos();
                float x = anchorX * (480 - originalSize);
                float y = anchorY * (270 - originalSize);
                pos.place(x, y, originalSize, originalSize, 480, 270);
                pos = gson.fromJson(gson.toJson(pos), SixthSenseConfig.AnimPos.class);
                close(pos.resolveX(640, newSize, 0), anchorX * (640 - newSize), "Horizontal anchor drifted at 3x");
                close(pos.resolveY(360, newSize, 0), anchorY * (360 - newSize), "Vertical anchor drifted at 3x");
                close(pos.resolveX(480, originalSize, 0), x, "Switching back to 4x accumulated drift");
                close(pos.resolveY(270, originalSize, 0), y, "Switching back to 4x accumulated drift");
            }
        }
        var pos = new SixthSenseConfig.AnimPos();
        pos.place(480 - originalSize - 20, 270 - originalSize - 40, originalSize, originalSize, 480, 270);
        close(pos.resolveX(640, newSize, 0), 640 - newSize - 20, "Right-edge margin changed");
        close(pos.resolveY(360, newSize, 0), 360 - newSize - 40, "Bottom-edge margin changed");
        pos.resolveX(20, newSize, 0);
        close(pos.resolveX(480, originalSize, 0), 480 - originalSize - 20, "Small window overwrote saved position");
        pos.setX(100);
        require(pos.anchorX == null, "Manual coordinate edit retained stale anchor");
        pos.reset(0.7f);
        close(pos.resolveX(640, newSize, 123), 123, "Reset did not restore automatic layout");
        require(pos.anchorX == null && pos.anchorY == null, "Reset retained anchors");
        var legacy = gson.fromJson("{\"x\":100,\"y\":50,\"scale\":0.7}", SixthSenseConfig.AnimPos.class);
        close(legacy.resolveX(480, originalSize, 0), 100, "Legacy X was lost");
        close(legacy.resolveY(270, originalSize, 0), 50, "Legacy Y was lost");
    }

    private static void scaleCompensationIsModest() {
        close(HudLayoutMath.scaleCompensation(4), 1, "4x sizing changed");
        close(HudLayoutMath.scaleCompensation(3), 1.15f, "3x sizing was not increased by 15%");
        close(HudLayoutMath.scaleCompensation(6), 1, "Large GUI scales were enlarged");
    }

    private static void translationsAreComplete() throws Exception {
        JsonObject english = readLanguage("en_us");
        Set<String> keys = english.keySet();
        require(keys.size() == 66, "Unexpected English translation coverage");
        for (String code : new String[]{"zh_cn", "hi_in", "es_es", "ar_sa", "tr_tr", "az_az"}) {
            JsonObject translated = readLanguage(code);
            require(translated.keySet().equals(keys), "Missing/extra translation keys in " + code);
            for (String key : keys) {
                String value = translated.get(key).getAsString();
                require(!value.isBlank(), "Empty translation: " + code + ":" + key);
                require(placeholders(value) == placeholders(english.get(key).getAsString()),
                        "Formatting arguments differ: " + code + ":" + key);
            }
        }
    }

    private static JsonObject readLanguage(String code) throws Exception {
        String path = "/assets/sixthsense/lang/" + code + ".json";
        var stream = HudRegressionChecks.class.getResourceAsStream(path);
        require(stream != null, "Language resource missing: " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }

    private static int placeholders(String text) {
        return text.split("%s", -1).length - 1;
    }

    private static void characterSpritesMatchRenderer() throws Exception {
        for (String name : new String[]{"warning_spark", "warning_ghast", "warning_bow", "warning_crossbow"}) {
            boolean weapon = name.equals("warning_bow") || name.equals("warning_crossbow");
            int frames = weapon ? HudCharacterAnimation.WEAPON_FRAMES : HudCharacterAnimation.FRAME_COUNT;
            try (var stream = HudRegressionChecks.class.getResourceAsStream(
                    "/assets/sixthsense/textures/gui/" + name + ".png")) {
                require(stream != null, "Missing character sprite: " + name);
                var image = ImageIO.read(stream);
                require(image.getWidth() == HudCharacterAnimation.FRAME_SIZE
                                && image.getHeight() == frames * HudCharacterAnimation.FRAME_SIZE,
                        "Sprite dimensions disagree with the renderer: " + name);
                require(image.getColorModel().hasAlpha(), "Sprite lost transparency: " + name);
                for (int frame = 0; frame < frames; frame++) {
                    boolean opaque = false;
                    boolean transparent = false;
                    for (int y = 0; y < HudCharacterAnimation.FRAME_SIZE; y++) {
                        for (int x = 0; x < HudCharacterAnimation.FRAME_SIZE; x++) {
                            int alpha = image.getRGB(x, frame * HudCharacterAnimation.FRAME_SIZE + y) >>> 24;
                            opaque |= alpha == 255;
                            transparent |= alpha == 0;
                        }
                    }
                    require(opaque && transparent, "Blank frame or opaque canvas: " + name + ":" + frame);
                    long start = frame * HudCharacterAnimation.FRAME_MILLIS * 1_000_000L;
                    require(HudCharacterAnimation.frameAt(start) == frame, "Character frame skipped");
                    require(HudCharacterAnimation.frameAt(start + 99_999_999L) == frame,
                            "Character frame duration changed");
                }
            }
        }
        require(HudCharacterAnimation.frameAt(1_600_000_000L) == 0, "Character loop does not wrap to frame 0");
    }

    private static void thornSpriteIsEditableAndAligned() throws Exception {
        try (var stream = HudRegressionChecks.class.getResourceAsStream(
                "/assets/sixthsense/textures/gui/warning_warden_thorns.png")) {
            require(stream != null, "Missing editable Warden thorn texture");
            var image = ImageIO.read(stream);
            require(image.getWidth() == 64 && image.getHeight() == 128
                    && image.getColorModel().hasAlpha(), "Thorn atlas must be 64 x 128 RGBA");
            boolean[] normal = new boolean[2], blood = new boolean[2];
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int baseAlpha = image.getRGB(x, y) >>> 24;
                    int bloodAlpha = image.getRGB(x, y + 64) >>> 24;
                    normal[x / 32] |= baseAlpha > 0;
                    blood[x / 32] |= bloodAlpha > 0;
                    require(bloodAlpha == 0 || baseAlpha > 0, "Blood overlay extends outside the thorn artwork");
                }
            }
            require(normal[0] && normal[1] && blood[0] && blood[1], "A thorn or blood-overlay half is empty");
            require(image.getRGB(32, 32) >>> 24 == 0, "The heart center should remain transparent");
        }
    }

    private static void close(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.001f, message + ": " + actual + " vs " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
