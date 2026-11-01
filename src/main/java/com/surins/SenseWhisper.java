package com.surins;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.biome.BiomeKeys;

/** Quiet first-person thoughts: a native pixel orb beside a compact thought above the hearts. */
final class SenseWhisper {
    private static String key;
    private static long previousFrame;
    private static float elapsedSeconds;
    private static Object world;
    private static int clock, lastContext = -1200, lastHunger = -3600, lastRisk = -3600;
    private static final java.util.ArrayDeque<Integer> jumps = new java.util.ArrayDeque<>();
    private static boolean grounded, hungry, risky, plain;
    private static boolean lastVoice;
    private static final float ORB_SECONDS = .42f, PANEL_SECONDS = .24f;
    private static final float ENTER_SECONDS = ORB_SECONDS + PANEL_SECONDS, LEAVE_SECONDS = ENTER_SECONDS;
    private static java.util.List<net.minecraft.text.OrderedText> cachedLines = java.util.List.of();
    private static String cachedKey, cachedLanguage;
    private static int cachedLimit = -1, cachedWidth, cachedRevision = -1;
    private SenseWhisper() { }
    static void initialize() {
        WhisperRelic.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(SenseWhisper::tick);
        HudRenderCallback.EVENT.register((context, counter) -> render(context));
    }
    private static void tick(MinecraftClient client) {
        if (world != client.world) {
            world = client.world; key = null; previousFrame = 0; elapsedSeconds = 0; clock = 0; lastContext = -1200;
            lastHunger = lastRisk = -3600; jumps.clear(); hungry = risky = false; grounded = false;
        }
        if (lastVoice != SixthSenseConfig.instance.innerVoice) modeChanged();
        if (client.currentScreen != null || client.getOverlay() != null || client.isPaused() || client.options.hudHidden || !SixthSenseConfig.instance.hudEnabled) previousFrame = 0;
        if (client.player == null || client.isPaused() || !SixthSenseConfig.instance.innerVoice) return;
        WhisperRelic.prepare(client);
        clock++;
        var player = client.player;
        if (grounded && !player.isOnGround() && player.getVelocity().y > .1) jumps.addLast(clock);
        grounded = player.isOnGround();
        while (!jumps.isEmpty() && clock - jumps.peekFirst() > 200) jumps.removeFirst();
        if (player.getHungerManager().getFoodLevel() >= 7) hungry = false;
        if (player.getHealth() > 8) risky = false;
        if (clock % 10 != 0 || !SixthSenseClient.warningsAllowed(client) || client.currentScreen != null
                || client.options.hudHidden || player.isSleeping() || clock - lastContext < 900 || visible()) return;
        if (player.getHungerManager().getFoodLevel() < 5 && !hungry && clock - lastHunger >= 3600) {
            show("hungry"); hungry = true; lastHunger = lastContext = clock; return;
        }
        var biome = client.world.getBiome(player.getBlockPos());
        boolean rocky = biome.matchesKey(BiomeKeys.STONY_PEAKS) || biome.matchesKey(BiomeKeys.JAGGED_PEAKS)
                || biome.matchesKey(BiomeKeys.STONY_SHORE) || biome.matchesKey(BiomeKeys.WINDSWEPT_HILLS)
                || biome.matchesKey(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS);
        if (player.getHealth() <= 6 && rocky && jumps.size() >= 3 && !risky && clock - lastRisk >= 3600) {
            show("footing"); risky = true; lastRisk = lastContext = clock;
        }
    }
    private static float duration() {
        if (key == null) return 0;
        if (plain) return 4.8f;
        String text = SixthSenseLanguage.text("sense." + key).getString();
        // Full reading time comes after the entrance; it is not consumed while menus hide the HUD.
        return readingSeconds(text.codePointCount(0, text.length())) + ENTER_SECONDS + LEAVE_SECONDS;
    }
    static float readingSeconds(int characters) { return Math.clamp(6f + Math.max(0, characters-30)*.065f, 6f, 10f); }
    private static boolean visible() { return key != null && elapsedSeconds < duration(); }
    static boolean basicNotice(String next) {
        return switch(next) {
            case "started", "tired", "level", "targeted", "unavailable" -> true;
            default -> false;
        };
    }
    static void modeChanged() {
        lastVoice = SixthSenseConfig.instance.innerVoice;
        key = null; previousFrame = 0; elapsedSeconds = 0; jumps.clear(); grounded = false;
    }
    static void show(String next) {
        boolean voice = SixthSenseConfig.instance.innerVoice;
        if (!voice && !basicNotice(next)) return;
        if (lastVoice != voice) modeChanged();
        plain = !voice;
        if (next.equals(key) && visible()) return;
        // Replace text immediately without making the whole panel blink out and re-enter.
        float carriedEntrance = visible() ? carriedEntrance(elapsedSeconds, duration()) : 0;
        world = MinecraftClient.getInstance().world; key = next; previousFrame = 0; elapsedSeconds = carriedEntrance;
    }
    static float carriedEntrance(float elapsed, float duration) {
        // Preserve the exact opacity/position even if a new thought arrives halfway through the exit.
        return Math.clamp(Math.min(elapsed, (duration-elapsed)*ENTER_SECONDS/LEAVE_SECONDS), 0, ENTER_SECONDS);
    }
    static float orbProgress(float flowTime) {
        return SenseVisualMath.smooth(Math.clamp(flowTime/ORB_SECONDS,0,1));
    }
    static float panelProgress(float flowTime) {
        return SenseVisualMath.smooth(Math.clamp((flowTime-ORB_SECONDS)/PANEL_SECONDS,0,1));
    }
    static float frameSeconds(long previous, long now) {
        // Do not discard time on slow frames: that stretched fades on low-FPS clients.
        return previous == 0 ? 0 : Math.max(0, (now - previous) / 1e9f);
    }
    private static int color(float alpha, int rgb) { return Math.round(255 * alpha) << 24 | rgb; }
    private static void render(DrawContext context) {
        var client = MinecraftClient.getInstance();
        if (client.player == null
                || world != client.world || !client.player.isAlive() || client.player.isSleeping()
                || !SixthSenseConfig.instance.hudEnabled || client.options.hudHidden || client.currentScreen != null || client.getOverlay() != null || client.isPaused()) { previousFrame = 0; return; }
        if (SixthSenseConfig.instance.innerVoice && !visible()) {
            previousFrame = 0;
            WhisperRelic.drawHeart(context,client,0,1);
            return;
        }
        if (plain == SixthSenseConfig.instance.innerVoice || !visible()) { previousFrame = 0; return; }
        long now = System.nanoTime();
        elapsedSeconds += frameSeconds(previousFrame, now);
        previousFrame = now;
        float elapsed = elapsedSeconds, duration = duration();
        float enter = SenseVisualMath.smooth(Math.clamp(elapsed / (plain?.36f:ENTER_SECONDS), 0, 1));
        float leave = SenseVisualMath.smooth(Math.clamp((duration - elapsed) / (plain?.45f:LEAVE_SECONDS), 0, 1));
        float alpha = enter * leave;
        if (plain && alpha < .03f) return;
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        int x = Math.max(6, WhisperHeart.visible() ? Math.round(WhisperHeart.x()) : w / 2 - 91), limit = Math.max(1, Math.min(200, w - x - 36));
        int rows = (int)Math.ceil((client.player.getMaxHealth() + client.player.getAbsorptionAmount()) / 20f);
        int bottom = (WhisperHeart.visible() ? Math.round(WhisperHeart.baseY()) - 9 : h - 48) - (rows - 1) * Math.max(3, 10 - Math.max(0, rows - 2))
                - (client.player.getArmor() > 0 ? 10 : 0);
        if (plain) {
            // Share the inner voice's HUD anchor; use only vanilla-sized white, shadowed text.
            var lines = SixthSenseLanguage.wrap(client.textRenderer, SixthSenseLanguage.text("sense.notice." + key), Math.max(1, limit));
            int textY = Math.max(4, bottom - lines.size() * 10);
            for (var line : lines) {
                context.drawTextWithShadow(client.textRenderer, line, x, textY, color(alpha, 0xFFFFFF));
                textY += 10;
            }
            return;
        }
        float scale = .70f;
        limit = Math.min(160, limit);
        String language = client.getLanguageManager().getLanguage();
        if (!key.equals(cachedKey) || !language.equals(cachedLanguage) || limit != cachedLimit || cachedRevision != WhisperRelic.revision()) {
            cachedLines = SixthSenseLanguage.wrap(client.textRenderer, SixthSenseLanguage.text("sense." + key),
                    Math.max(1, (int)((limit - 8) / scale)));
            cachedWidth = cachedLines.stream().mapToInt(client.textRenderer::getWidth).max().orElse(0);
            cachedKey = key; cachedLanguage = language; cachedLimit = limit; cachedRevision = WhisperRelic.revision();
        }
        var lines = cachedLines;
        int panelWidth = Math.max(12, (int)Math.ceil(cachedWidth * scale) + 8);
        int panelHeight = Math.max(14, (int)Math.ceil(lines.size() * 10 * scale) + 6);
        int y = Math.max(4, bottom - panelHeight);
        int orbY = y + (panelHeight - 12) / 2;
        boolean reduced = SixthSenseConfig.instance.reduceMotion;
        float flowTime = carriedEntrance(elapsed,duration);
        float emergence = orbProgress(flowTime);
        float reveal = panelProgress(flowTime);
        WhisperRelic.drawHeart(context,client,emergence,1);
        float orbX = x;
        float movingOrbY = orbY;
        float orbAlpha = reduced ? emergence : 1;
        float panelAlpha = reduced ? reveal : 1;
        // The caption unfolds from inside the sphere to the right; reverse the same timeline on exit.
        if (reveal > 0) {
            int panelX = x+6+Math.round(10*reveal);
            int clipRight = x+6+(int)Math.ceil((panelWidth+10)*reveal);
            if (!reduced) context.enableScissor(x+6,y,clipRight,y+panelHeight);
            var matrices = context.getMatrices(); matrices.push();
            try {
                if (reduced) panelX = x+16;
                WhisperRelic.drawBacking(context,client,panelX,y,panelWidth,panelHeight,panelAlpha);
                matrices.translate(panelX+4,y+4,250); matrices.scale(scale,scale,1);
                if (panelAlpha>.03f) for (int i=0;i<lines.size();i++)
                    context.drawText(client.textRenderer,lines.get(i),0,i*10,color(panelAlpha,0xDEDDE7),false);
                context.draw();
            } finally { matrices.pop(); if (!reduced) context.disableScissor(); }
        }
        if (emergence > 0) {
            // Render last so the left edge of the growing strip is tucked behind the orb.
            context.getMatrices().push(); context.getMatrices().translate(0,0,300);
            try { WhisperRelic.draw(context,client,orbX,movingOrbY,orbAlpha,emergence); }
            finally { context.getMatrices().pop(); }
        }
    }
}
