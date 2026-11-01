package com.surins;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** A short oak sign slides down once when joining a server or opening the local world to LAN. */
final class MultiplayerNotice {
    private static long shown;
    private static boolean remote;
    private MultiplayerNotice() { }
    static void initialize() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { remote = false; shown = 0; });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { remote = false; shown = 0; });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean nowRemote = client.world != null && client.player != null
                    && !SenseAbility.available(client) && client.player.age > 40;
            if (nowRemote && !remote) shown = System.nanoTime();
            remote = nowRemote;
        });
        HudRenderCallback.EVENT.register((context, counter) -> render(context));
    }
    private static void render(DrawContext context) {
        if (shown == 0 || !remote) return;
        float elapsed = (System.nanoTime() - shown) / 1e9f;
        if (elapsed > 8) { shown = 0; return; }
        var client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;
        int width = Math.min(context.getScaledWindowWidth() - 16, 340);
        var lines = SixthSenseLanguage.wrap(client.textRenderer, SixthSenseLanguage.text("sense.multiplayer"), width - 22);
        int height = 23 + lines.size() * 10;
        float t = Math.min(1, Math.min(elapsed / .4f, (8 - elapsed) / .4f));
        t = t * t * (3 - 2 * t);
        int x = (context.getScaledWindowWidth() - width) / 2, y = Math.round(-height + (height + 8) * t);
        context.fill(x, y, x + width, y + height, 0xFF292B3A);
        context.drawBorder(x, y, width, height, 0xFF595970);
        context.fill(x + 4, y + 3, x + width - 4, y + 4, 0xFF9D97BD);
        context.drawCenteredTextWithShadow(client.textRenderer, "The Sixth Sense", x + width / 2, y + 8, 0xFFDEDCE9);
        for (int i = 0; i < lines.size(); i++) context.drawCenteredTextWithShadow(client.textRenderer,
                lines.get(i), x + width / 2, y + 20 + i * 10, 0xFFAAA9BF);
    }
}
