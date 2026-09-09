package com.surins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** A compact, scrollable checklist for testing the HUD in a running world. */
final class HudFeatureInfoScreen extends Screen {
    private static final String[] SECTIONS = {
            "bow", "crossbow", "explosion", "ghast", "shulker", "warden", "thorns",
            "raid", "layout", "editor", "debug", "reload", "settings", "language", "general"
    };
    private final Screen parent;
    private int scroll;
    private int contentHeight;

    HudFeatureInfoScreen(Screen parent) {
        super(SixthSenseLanguage.text("info.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> close())
                .dimensions(width / 2 - 50, height - 27, 100, 20).build());
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // This screen paints its own panel before the text. Screen.render calls
        // this again before drawing widgets; vanilla blur would blur the checklist.
    }

    private int top() { return 38; }
    private int bottom() { return Math.max(top() + 1, height - 36); }
    private void scrollBy(int amount) {
        scroll = Math.max(0, Math.min(Math.max(0, contentHeight - (bottom() - top())), scroll + amount));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xF0181D22);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFE6D5AD);
        context.drawCenteredTextWithShadow(textRenderer, SixthSenseLanguage.text("info.help"), width / 2, 23, 0xFFB7C0C4);
        int left = Math.max(12, (width - 650) / 2);
        int textWidth = Math.max(1, width - left * 2 - 10);
        // Recompute before clamping so a GUI-scale/window change preserves a valid viewport.
        contentHeight = 0;
        for (String section : SECTIONS) {
            contentHeight += textRenderer.wrapLines(SixthSenseLanguage.text("info." + section), textWidth).size() * 12 + 9;
        }
        scrollBy(0);
        context.enableScissor(left, top(), width - left, bottom());
        int y = top() - scroll;
        for (String section : SECTIONS) {
            boolean heading = true;
            for (var line : textRenderer.wrapLines(SixthSenseLanguage.text("info." + section), textWidth)) {
                context.drawTextWithShadow(textRenderer, line, left, y, heading ? 0xFFE6D5AD : 0xFFD0D8DC);
                heading = false;
                y += 12;
            }
            y += 9;
        }
        context.disableScissor();
        int viewport = bottom() - top();
        if (contentHeight > viewport) {
            int thumbHeight = Math.max(8, viewport * viewport / contentHeight);
            int thumbY = top() + (viewport - thumbHeight) * scroll / (contentHeight - viewport);
            context.fill(width - left - 3, top(), width - left, bottom(), 0xFF303A40);
            context.fill(width - left - 3, thumbY, width - left, thumbY + thumbHeight, 0xFFC4B387);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scrollBy((int) Math.round(-vertical * 30));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scrollBy(-12);
            case GLFW.GLFW_KEY_DOWN -> scrollBy(12);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-(bottom() - top()));
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(bottom() - top());
            case GLFW.GLFW_KEY_HOME -> scrollBy(-contentHeight);
            case GLFW.GLFW_KEY_END -> scrollBy(contentHeight);
            default -> { return super.keyPressed(key, scanCode, modifiers); }
        }
        return true;
    }
}
