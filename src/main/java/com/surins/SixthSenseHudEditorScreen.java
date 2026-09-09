package com.surins;

import com.surins.mixin.BossBarHudAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

import static com.surins.SixthSenseLanguage.string;

/**
 * A live, non-pausing editor for every HUD element provided by the mod.
 */
public class SixthSenseHudEditorScreen extends Screen {

    private enum HudElement {
        RAID,
        BOW,
        CROSSBOW,
        EXPLOSION,
        GHAST,
        SHULKER,
        WARDEN
    }

    private static final class PreviewBounds {
        final float x;
        final float y;
        final float width;
        final float height;
        final float scale;
        final float hitX;
        final float hitY;
        final float hitWidth;
        final float hitHeight;

        PreviewBounds(float x, float y, float width, float height, float scale) {
            this(x, y, width, height, scale, x, y, width, height);
        }

        PreviewBounds(float x, float y, float width, float height, float scale,
                      float hitX, float hitY, float hitWidth, float hitHeight) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitWidth = hitWidth;
            this.hitHeight = hitHeight;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= hitX && mouseX <= hitX + hitWidth
                    && mouseY >= hitY && mouseY <= hitY + hitHeight;
        }
    }

    private final class FooterLayout {
        final float x;
        final float y;
        final float scale;
        final int virtualWidth;

        FooterLayout(float x, float y, float scale, int virtualWidth) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.virtualWidth = virtualWidth;
        }

        int gridButtonX() {
            return virtualWidth - getDebugButtonWidth() - getGridButtonWidth() - 12;
        }

        int debugButtonX() {
            return virtualWidth - getDebugButtonWidth() - 4;
        }

        int buttonY() {
            return 4;
        }

        float virtualX(double mouseX) {
            return ((float) mouseX - x) / scale;
        }

        float virtualY(double mouseY) {
            return ((float) mouseY - y) / scale;
        }
    }

    private static final class ConfirmationLayout {
        final float x;
        final float y;
        final float scale;

        final int virtualWidth;
        final int virtualHeight;

        ConfirmationLayout(float x, float y, float scale, int virtualWidth, int virtualHeight) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.virtualWidth = virtualWidth;
            this.virtualHeight = virtualHeight;
        }

        int buttonWidth() { return (virtualWidth - 32) / 2; }
        int buttonY() { return virtualHeight - 22; }
        int cancelX() { return virtualWidth / 2 + 4; }

        float virtualX(double mouseX) {
            return ((float) mouseX - x) / scale;
        }

        float virtualY(double mouseY) {
            return ((float) mouseY - y) / scale;
        }
    }

    private static final Text TITLE = SixthSenseLanguage.text("editor.title");
    private static final String RAID_DISTANCE = "24m";
    private static final int ORIGINAL_FRAME_SIZE = 16;
    private static final int GRID_SIZE = 8;
    private static final int FOOTER_HEIGHT = 34;
    private static final int BUTTON_HEIGHT = 18;
    private static final int FOOTER_MARGIN = 8;

    private static final Identifier WARNING_BOW = Identifier.of("sixthsense", "textures/gui/warning_bow.png");
    private static final Identifier WARNING_CROSSBOW = Identifier.of("sixthsense", "textures/gui/warning_crossbow.png");
    private static final Identifier WARNING_BOOM = Identifier.of("sixthsense", "textures/gui/warning_boom.png");
    private static final Identifier WARNING_GHAST = Identifier.of("sixthsense", "textures/gui/warning_ghast.png");
    private static final Identifier WARNING_SHULKER = Identifier.of("sixthsense", "textures/gui/warning_spark.png");
    private static final Identifier WARNING_WARDEN = Identifier.of("sixthsense", "textures/gui/warning_warden.png");
    private static final Identifier WARNING_ARROW = Identifier.of("sixthsense", "textures/gui/warning_arrow.png");

    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_COLOR = 0xB0000000;
    private static final int GRID_COLOR = 0x223F8FBF;
    private static final int GRID_ANCHOR_COLOR = 0x40539BD1;
    private static final int BORDER_COLOR = 0x88888888;
    private static final int SELECTED_BORDER_COLOR = 0xFFFFFF55;

    private final Map<HudElement, PreviewBounds> previewBounds = new EnumMap<>(HudElement.class);
    private final Map<HudElement, PreviewBounds> debugBounds = new EnumMap<>(HudElement.class);
    private final HudAnimationMath.WardenClock previewClock = new HudAnimationMath.WardenClock();
    private final WardenHudMotion previewMotion = new WardenHudMotion();
    private boolean gridEnabled;
    private boolean debugMode;
    private boolean reloadingImages;
    private boolean imageReloadFailed;
    private String imageReloadStatus = "";
    private boolean confirmResetAll;
    private boolean cursorHidden;
    private long debugStartTime;
    private HudElement selectedElement;
    private HudElement draggingElement;
    private float dragOffsetX;
    private float dragOffsetY;

    public SixthSenseHudEditorScreen() {
        super(TITLE);
        this.gridEnabled = SixthSenseConfig.instance.editorGridEnabled;
        this.debugStartTime = System.currentTimeMillis();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        buildPreviewBounds();

        if (gridEnabled) {
            drawGrid(drawContext);
        }

        for (HudElement element : HudElement.values()) {
            drawElement(drawContext, element, previewBounds.get(element), false,
                    isElementEnabled(element) ? 1.0f : 0.35f);
        }

        drawEditorChrome(drawContext, mouseX, mouseY);

        if (debugMode) {
            renderDebugOverlay(drawContext, mouseX, mouseY);
        } else {
            setCursorHidden(false);
        }

        if (confirmResetAll) {
            setCursorHidden(false);
            drawResetConfirmation(drawContext);
        }
    }

    /**
     * The central cell is anchored on the automatic one-icon warning location:
     * centered between the health/hunger bars and above the experience level.
     */
    private void drawGrid(DrawContext drawContext) {
        float anchorX = getGridAnchorX();
        float anchorY = getGridAnchorY();
        int anchorLeft = Math.round(anchorX - GRID_SIZE / 2.0f);
        int anchorTop = Math.round(anchorY - GRID_SIZE / 2.0f);

        drawContext.fill(anchorLeft, anchorTop, anchorLeft + GRID_SIZE, anchorTop + GRID_SIZE, GRID_ANCHOR_COLOR);
        drawContext.drawBorder(anchorLeft, anchorTop, GRID_SIZE, GRID_SIZE, 0x9989C7FF);

        float firstVertical = anchorX - GRID_SIZE / 2.0f;
        while (firstVertical > 0.0f) {
            firstVertical -= GRID_SIZE;
        }
        while (firstVertical + GRID_SIZE <= 0.0f) {
            firstVertical += GRID_SIZE;
        }
        for (float x = firstVertical; x < width; x += GRID_SIZE) {
            int lineX = Math.round(x);
            drawContext.fill(lineX, 0, lineX + 1, height, GRID_COLOR);
        }

        float firstHorizontal = anchorY - GRID_SIZE / 2.0f;
        while (firstHorizontal > 0.0f) {
            firstHorizontal -= GRID_SIZE;
        }
        while (firstHorizontal + GRID_SIZE <= 0.0f) {
            firstHorizontal += GRID_SIZE;
        }
        for (float y = firstHorizontal; y < height; y += GRID_SIZE) {
            int lineY = Math.round(y);
            drawContext.fill(0, lineY, width, lineY + 1, GRID_COLOR);
        }
    }

    private float getGridAnchorX() {
        return width / 2.0f;
    }

    private float getGridAnchorY() {
        return height - 52.0f + (ORIGINAL_FRAME_SIZE * 0.70f * HudLayoutMath.scaleCompensation(client.getWindow().getScaleFactor())) / 2.0f;
    }

    private void drawEditorChrome(DrawContext drawContext, int mouseX, int mouseY) {
        HudElement hoveredElement = findElementAtName(previewBounds, mouseX, mouseY);
        if (hoveredElement != null && !debugMode) {
            drawHoverLabel(drawContext, getElementLabel(hoveredElement), previewBounds.get(hoveredElement));
        }

        FooterLayout footer = getFooterLayout();
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(footer.x, footer.y, 0);
        drawContext.getMatrices().scale(footer.scale, footer.scale, 1.0f);

        drawContext.fill(0, 0, footer.virtualWidth, FOOTER_HEIGHT, PANEL_COLOR);
        int controlStartX = footer.gridButtonX() - 8;
        drawContext.drawTextWithShadow(textRenderer, getFooterInstructions(controlStartX - 8), 8, 7, 0xFFDDDDDD);

        if (selectedElement != null) {
            SixthSenseConfig.AnimPos position = getPosition(selectedElement);
            String positionText = string("editor.position",
                    getElementLabel(selectedElement), Math.round(previewBounds.get(selectedElement).x), Math.round(previewBounds.get(selectedElement).y), String.format(java.util.Locale.ROOT, "%.2f", position.scale));
            if (textRenderer.getWidth(positionText) <= controlStartX - 8) {
                drawContext.drawTextWithShadow(textRenderer, positionText, 8, 21, 0xFFBBBBBB);
            }
        }

        drawButton(drawContext, footer.gridButtonX(), footer.buttonY(), getGridButtonWidth(),
                string("editor.grid", string(gridEnabled ? "state.on" : "state.off")), gridEnabled);
        drawButton(drawContext, footer.debugButtonX(), footer.buttonY(), getDebugButtonWidth(), string("editor.debug"), debugMode);
        drawContext.getMatrices().pop();
    }

    private String getFooterInstructions(int availableWidth) {
        String full = string("editor.help");
        if (textRenderer.getWidth(full) <= availableWidth) {
            return full;
        }
        String compact = string("editor.help_short");
        if (textRenderer.getWidth(compact) <= availableWidth) {
            return compact;
        }
        return "R / Shift+R / Esc";
    }

    private void drawHoverLabel(DrawContext drawContext, String label, PreviewBounds bounds) {
        float labelScale = 0.70f * getChromeScale();
        float labelX = Math.max(4.0f, Math.min(bounds.x, width - textRenderer.getWidth(label) * labelScale - 4.0f));
        float labelY = Math.max(3.0f, bounds.y - 10.0f * labelScale);
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(labelX, labelY, 0);
        drawContext.getMatrices().scale(labelScale, labelScale, 1.0f);
        drawContext.drawTextWithShadow(textRenderer, label, 0, 0, 0xFFFFD54F);
        drawContext.getMatrices().pop();
    }

    private void drawButton(DrawContext drawContext, int x, int y, int buttonWidth, String label, boolean active) {
        int fillColor = active ? 0xB05C7FA3 : 0xA0333333;
        drawContext.fill(x, y, x + buttonWidth, y + BUTTON_HEIGHT, fillColor);
        drawContext.drawBorder(x, y, buttonWidth, BUTTON_HEIGHT, active ? 0xFFFFFFFF : 0xFF777777);
        float labelScale = Math.min(1.0f, (buttonWidth - 8.0f) / Math.max(1, textRenderer.getWidth(label)));
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(x + buttonWidth / 2.0f, y + (BUTTON_HEIGHT - textRenderer.fontHeight * labelScale) / 2.0f, 0);
        drawContext.getMatrices().scale(labelScale, labelScale, 1.0f);
        drawContext.drawCenteredTextWithShadow(textRenderer, Text.literal(label), 0, 0, TEXT_COLOR);
        drawContext.getMatrices().pop();
    }

    private void buildPreviewBounds() {
        SixthSenseConfig.migrateLegacyPositions(width, height, getRaidContentWidth());
        previewBounds.clear();

        float[] widths = new float[6];
        for (HudElement element : HudElement.values()) {
            if (element != HudElement.RAID && getPosition(element).x == -1) {
                widths[element.ordinal() - 1] = getElementWidth(element);
            }
        }
        float[] row = HudLayoutMath.warningRow(width, widths, true,
                18 * getPosition(HudElement.WARDEN).displayScale());
        float dynamicY = height - 52.0f;

        for (HudElement element : HudElement.values()) {
            SixthSenseConfig.AnimPos position = getPosition(element);
            float elementWidth = getElementWidth(element);
            float elementHeight = getElementHeight(element);
            float x;
            float y;

            if (element == HudElement.RAID) {
                x = position.resolveX(width, elementWidth, (width - elementWidth) / 2.0f);
                y = position.resolveY(height, elementHeight, getAutomaticRaidY());
            } else {
                x = position.resolveX(width, elementWidth, row[element.ordinal() - 1]);
                y = position.resolveY(height, elementHeight, dynamicY);
            }

            previewBounds.put(element, new PreviewBounds(x, y, elementWidth, elementHeight, position.displayScale()));
        }
    }

    private float getAutomaticRaidY() {
        if (client == null || client.inGameHud == null || client.inGameHud.getBossBarHud() == null) {
            return 12.0f;
        }
        int activeBossBars = ((BossBarHudAccessor) client.inGameHud.getBossBarHud()).getBossBars().size();
        return 12.0f + activeBossBars * 19.0f;
    }

    private float getElementWidth(HudElement element) {
        SixthSenseConfig.AnimPos position = getPosition(element);
        if (element == HudElement.RAID) {
            return getRaidContentWidth() * position.displayScale();
        }
        return ORIGINAL_FRAME_SIZE * position.displayScale();
    }

    private float getElementHeight(HudElement element) {
        return ORIGINAL_FRAME_SIZE * getPosition(element).displayScale();
    }

    private int getRaidContentWidth() {
        return textRenderer.getWidth(string("editor.raid_example")) + 4 + ORIGINAL_FRAME_SIZE + 4 + textRenderer.getWidth(RAID_DISTANCE);
    }

    private void drawElement(DrawContext drawContext, HudElement element, PreviewBounds bounds,
                             boolean animated, float alpha) {
        switch (element) {
            case RAID -> drawRaidPreview(drawContext, bounds, alpha);
            case BOW -> drawWarningSprite(drawContext, WARNING_BOW, bounds, animated ? getAnimationFrame(8, 140) : 0,
                    32, 32, HudCharacterAnimation.WEAPON_TEXTURE_HEIGHT, alpha, "3");
            case CROSSBOW -> drawWarningSprite(drawContext, WARNING_CROSSBOW, bounds, animated ? getAnimationFrame(8, 140) : 0,
                    32, 32, HudCharacterAnimation.WEAPON_TEXTURE_HEIGHT, alpha, "2");
            case EXPLOSION -> drawWarningSprite(drawContext, WARNING_BOOM, bounds, animated ? getAnimationFrame(10, 85) : 0,
                    32, 160, 64, alpha, null);
            case GHAST -> drawGhastPreview(drawContext, bounds, animated, alpha);
            case SHULKER -> drawWarningSprite(drawContext, WARNING_SHULKER, bounds,
                    animated ? HudCharacterAnimation.frameAt(System.nanoTime()) : 0,
                    32, 32, HudCharacterAnimation.TEXTURE_HEIGHT, alpha, "3");
            case WARDEN -> drawWardenPreview(drawContext, bounds, animated, alpha);
        }
    }

    private void drawRaidPreview(DrawContext drawContext, PreviewBounds bounds, float alpha) {
        float scale = bounds.scale;
        int textColor = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        int textWidth = textRenderer.getWidth(string("editor.raid_example"));

        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(bounds.x, bounds.y, 0);
        drawContext.getMatrices().scale(scale, scale, 1.0f);
        drawContext.drawText(textRenderer, string("editor.raid_example"), 0, 4, textColor, true);
        drawContext.drawItem(new ItemStack(Items.COMPASS), textWidth + 4, 0);
        drawContext.drawText(textRenderer, RAID_DISTANCE, textWidth + 24, 4, textColor, true);
        drawContext.getMatrices().pop();
    }

    private void drawWarningSprite(DrawContext drawContext, Identifier texture, PreviewBounds bounds,
                                   int frame, int frameSize, int textureWidth, int textureHeight,
                                   float alpha, String countText) {
        float scale = bounds.scale;
        int columns = Math.max(1, textureWidth / frameSize);
        int uOffset = (frame % columns) * frameSize;
        int vOffset = (frame / columns) * frameSize;
        int actualHalfSize = frameSize / 2;

        drawContext.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(bounds.x + 8.0f * scale, bounds.y + 8.0f * scale, 0);
        drawContext.getMatrices().scale(scale, scale, 1.0f);
        drawContext.getMatrices().scale((float) ORIGINAL_FRAME_SIZE / frameSize,
                (float) ORIGINAL_FRAME_SIZE / frameSize, 1.0f);
        drawContext.drawTexture(texture, -actualHalfSize, -actualHalfSize, uOffset, vOffset,
                frameSize, frameSize, textureWidth, textureHeight);
        drawContext.getMatrices().pop();
        drawContext.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (countText != null) {
            drawPreviewCount(drawContext, countText, bounds.x, bounds.y, scale, alpha);
        }
    }

    private void drawGhastPreview(DrawContext drawContext, PreviewBounds bounds, boolean animated, float alpha) {
        drawWarningSprite(drawContext, WARNING_GHAST, bounds,
                animated ? GhastAnimationClock.previewFrame(System.currentTimeMillis() - debugStartTime) : 0,
                32, 32, HudCharacterAnimation.TEXTURE_HEIGHT, alpha, null);
        drawGhastDistance(drawContext, "48m", bounds.x, bounds.y, bounds.scale, alpha);
    }

    private void drawGhastDistance(DrawContext drawContext, String distanceText, float drawX, float drawY,
                                   float iconScale, float alpha) {
        float iconSize = ORIGINAL_FRAME_SIZE * iconScale;
        int nativeWidth = Math.max(1, textRenderer.getWidth(distanceText));
        int nativeHeight = Math.max(1, textRenderer.fontHeight);
        float textScale = Math.min(0.62f * iconScale,
                Math.min(iconSize * 0.72f / nativeWidth, iconSize * 0.50f / nativeHeight));
        float textX = drawX + iconSize - nativeWidth * textScale - 0.5f;
        float textY = drawY + iconSize - nativeHeight * textScale - 0.5f;
        int textColor = ((int) (alpha * 255) << 24) | 0xFFFFFF;

        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(textX, textY, 0);
        drawContext.getMatrices().scale(textScale, textScale, 1.0f);
        drawContext.drawText(textRenderer, distanceText, 0, 0, textColor, true);
        drawContext.getMatrices().pop();
    }

    private void drawWardenPreview(DrawContext drawContext, PreviewBounds bounds, boolean animated, float alpha) {
        float scale = bounds.scale;
        long now = System.currentTimeMillis();
        if (animated) previewClock.update(System.nanoTime(), 12.0, true);
        int frame = animated ? previewClock.frame() : 0;
        HudAnimationMath.Heartbeat heartbeat = previewClock.heartbeat(scale, animated);
        float centerX = bounds.x + 8.0f * scale;
        float centerY = bounds.y + 8.0f * scale;
        float arrowRotation = animated ? ((now - debugStartTime) * 0.08f) % 360.0f : 0.0f;
        long demoTime = Math.floorMod(now - debugStartTime, 4200);
        previewMotion.update(System.nanoTime(), arrowRotation, animated && demoTime >= 1000 && demoTime < 3500);

        drawContext.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(centerX, centerY, 0);
        drawContext.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(animated ? previewMotion.angle() : 0));
        drawContext.getMatrices().translate(0, -previewMotion.arrowRadius() * scale, 0);
        drawContext.getMatrices().scale(scale * previewMotion.arrowScale(), scale * previewMotion.arrowScale(), 1.0f);
        drawContext.drawTexture(WARNING_ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
        drawContext.getMatrices().pop();

        if (heartbeat.motionBlurAlpha() > 0.0f) {
            drawContext.setShaderColor(1.0f, 1.0f, 1.0f, alpha * heartbeat.motionBlurAlpha());
            drawWardenHeart(drawContext, centerX - heartbeat.jitterX * 0.6f,
                    centerY - heartbeat.jitterY * 0.6f, scale * heartbeat.scaleMultiplier * 0.985f, frame);
        }

        drawContext.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        drawWardenHeart(drawContext, centerX + heartbeat.jitterX, centerY + heartbeat.jitterY,
                scale * heartbeat.scaleMultiplier, frame);
        drawContext.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        if (animated) {
            float impact = WardenImpactMemory.pulseAt(Math.max(0, demoTime - 2300) / 1000.0);
            WardenReticleRenderer.draw(drawContext, centerX, centerY, scale,
                    alpha * previewMotion.reticleAlpha(), impact, demoTime < 2300 ? 1 : 0.3f);
        }
    }

    private void drawWardenHeart(DrawContext drawContext, float centerX, float centerY, float heartScale, int frame) {
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(centerX, centerY, 0);
        drawContext.getMatrices().scale(heartScale, heartScale, 1.0f);
        drawContext.drawTexture(WARNING_WARDEN, -8, -8, 0, frame * 16, 16, 16, 16, 160);
        drawContext.getMatrices().pop();
    }

    private void drawPreviewCount(DrawContext drawContext, String countText, float drawX, float drawY,
                                  float iconScale, float alpha) {
        float iconSize = ORIGINAL_FRAME_SIZE * iconScale;
        float maxTextWidth = iconSize * 0.86f;
        float maxTextHeight = iconSize * 0.62f;
        float textScale = Math.min(0.76f * iconScale, maxTextHeight / textRenderer.fontHeight);
        textScale = Math.min(textScale, maxTextWidth / textRenderer.getWidth(countText));
        float textWidth = textRenderer.getWidth(countText) * textScale;
        float textHeight = textRenderer.fontHeight * textScale;

        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(drawX + iconSize - textWidth - 0.5f,
                drawY + iconSize - textHeight - 0.5f, 0);
        drawContext.getMatrices().scale(textScale, textScale, 1.0f);
        drawContext.drawText(textRenderer, countText, 0, 0, ((int) (alpha * 255) << 24) | 0xFFFFFF, true);
        drawContext.getMatrices().pop();
    }

    private int getAnimationFrame(int frameCount, int frameDelay) {
        return (int) ((System.currentTimeMillis() - debugStartTime) / frameDelay) % frameCount;
    }

    private void renderDebugOverlay(DrawContext drawContext, int mouseX, int mouseY) {
        buildDebugBounds();
        HudElement hoveredElement = findElementAtName(debugBounds, mouseX, mouseY);

        // Draw the preview and its controls above the dimmed layout editor.
        drawContext.fill(0, 0, width, height, 0xB8000000);
        drawScaledCenteredText(drawContext, string("editor.debug_help"), width / 2.0f,
                getDebugTitleY(), 0.76f * getDebugUnit(), 0xFFE5F2FF);

        for (HudElement element : HudElement.values()) {
            PreviewBounds bounds = debugBounds.get(element);
            boolean animated = element == hoveredElement;
            int cardColor = animated ? 0xC0355067 : 0x8A1E272E;
            int borderColor = animated ? 0xFFB8E3FF : BORDER_COLOR;
            drawContext.fill(Math.round(bounds.hitX), Math.round(bounds.hitY),
                    Math.round(bounds.hitX + bounds.hitWidth), Math.round(bounds.hitY + bounds.hitHeight), cardColor);
            drawContext.drawBorder(Math.round(bounds.hitX), Math.round(bounds.hitY),
                    Math.round(bounds.hitWidth), Math.round(bounds.hitHeight), borderColor);
            drawElement(drawContext, element, bounds, animated, animated ? 1.0f : 0.50f);
            drawScaledCenteredText(drawContext, getElementLabel(element), bounds.hitX + bounds.hitWidth / 2.0f,
                    bounds.hitY + bounds.hitHeight - 10.0f * getDebugUnit(), 0.70f * getDebugUnit(),
                    animated ? 0xFFFFFFFF : 0xFFBBC6CE);
        }

        drawDebugButtonOnly(drawContext);
        drawImageReloadControl(drawContext, mouseX, mouseY);
        setCursorHidden(hoveredElement != null && !confirmResetAll);
    }

    private int getImageReloadWidth() {
        return Math.max(90, Math.max(textRenderer.getWidth(string("editor.reload_images")),
                textRenderer.getWidth(string("editor.reloading_images"))) + 16);
    }

    private float getImageReloadScale() {
        return Math.min(getChromeScale(), Math.max(0.1f, (width - 16.0f) / getImageReloadWidth()));
    }

    private boolean isOverImageReload(double mouseX, double mouseY) {
        float scale = getImageReloadScale();
        float x = width - 8 - getImageReloadWidth() * scale;
        return mouseX >= x && mouseX <= width - 8 && mouseY >= 8 && mouseY <= 8 + BUTTON_HEIGHT * scale;
    }

    private float getDebugHeaderHeight() { return 8 + 64 * getChromeScale(); }

    private boolean isOverInfo(double mouseX, double mouseY) {
        float scale = getImageReloadScale();
        float top = 12 + BUTTON_HEIGHT * scale;
        return mouseX >= width - 8 - BUTTON_HEIGHT * scale && mouseX <= width - 8
                && mouseY >= top && mouseY <= top + BUTTON_HEIGHT * scale;
    }

    private void drawImageReloadControl(DrawContext context, int mouseX, int mouseY) {
        float scale = getImageReloadScale();
        float x = width - 8 - getImageReloadWidth() * scale;
        context.getMatrices().push();
        context.getMatrices().translate(x, 8, 0);
        context.getMatrices().scale(scale, scale, 1);
        drawButton(context, 0, 0, getImageReloadWidth(),
                string(reloadingImages ? "editor.reloading_images" : "editor.reload_images"),
                !reloadingImages && isOverImageReload(mouseX, mouseY));
        context.getMatrices().pop();
        context.getMatrices().push();
        context.getMatrices().translate(width - 8 - BUTTON_HEIGHT * scale, 12 + BUTTON_HEIGHT * scale, 0);
        context.getMatrices().scale(scale, scale, 1);
        drawButton(context, 0, 0, BUTTON_HEIGHT, "i", isOverInfo(mouseX, mouseY));
        context.getMatrices().pop();
        if (!imageReloadStatus.isEmpty()) {
            float textScale = Math.min(0.72f * scale, (width - 16.0f) / Math.max(1, textRenderer.getWidth(imageReloadStatus)));
            context.getMatrices().push();
            context.getMatrices().translate(width - 8 - textRenderer.getWidth(imageReloadStatus) * textScale,
                    16 + 2 * BUTTON_HEIGHT * scale, 0);
            context.getMatrices().scale(textScale, textScale, 1);
            context.drawTextWithShadow(textRenderer, imageReloadStatus, 0, 0,
                    imageReloadFailed ? 0xFFFFA5A5 : 0xFFB6DFCF);
            context.getMatrices().pop();
        }
    }

    private void reloadHudImages() {
        if (reloadingImages || client == null) return;
        reloadingImages = true;
        imageReloadFailed = false;
        imageReloadStatus = "";
        var minecraft = client;
        HudTextureReloader.reload(minecraft).whenCompleteAsync((result, error) -> {
            reloadingImages = false;
            imageReloadFailed = error != null || !result.success();
            imageReloadStatus = imageReloadFailed
                    ? string("editor.reload_images_failed", error == null ? result.failedFile() : "PNG")
                    : string("editor.reload_images_done");
        }, minecraft);
    }

    private void buildDebugBounds() {
        debugBounds.clear();
        final int columns = 3;
        float unit = getDebugUnit();
        float cardWidth = 112.0f * unit;
        float cardHeight = 58.0f * unit;
        float gap = 12.0f * unit;
        float titleHeight = 20.0f * unit;
        float availableHeight = Math.max(1.0f, getFooterLayout().y - 8.0f - getDebugHeaderHeight());
        float galleryWidth = columns * cardWidth + (columns - 1) * gap;
        float galleryHeight = 3.0f * cardHeight + 2.0f * gap;
        float fit = Math.min(1.0f, Math.min((width - 16.0f * unit) / galleryWidth,
                (availableHeight - titleHeight) / galleryHeight));

        // Keep the same physical target size across GUI scales, then only shrink when the window truly needs it.
        unit *= Math.max(0.20f, fit);
        cardWidth = 112.0f * unit;
        cardHeight = 58.0f * unit;
        gap = 12.0f * unit;
        titleHeight = 20.0f * unit;
        galleryHeight = 3.0f * cardHeight + 2.0f * gap;
        float top = getDebugHeaderHeight() + Math.max(4.0f * unit, (availableHeight - titleHeight - galleryHeight) / 2.0f + titleHeight);

        HudElement[] elements = HudElement.values();
        for (int index = 0; index < elements.length; index++) {
            HudElement element = elements[index];
            int row = index / columns;
            int column = index % columns;
            int itemsInRow = Math.min(columns, elements.length - row * columns);
            float rowWidth = itemsInRow * cardWidth + (itemsInRow - 1) * gap;
            float cardX = (width - rowWidth) / 2.0f + column * (cardWidth + gap);
            float cardY = top + row * (cardHeight + gap);
            float elementScale = 2.0f * unit;
            if (element == HudElement.RAID) {
                elementScale = Math.min(elementScale, (cardWidth - 12.0f * unit) / getRaidContentWidth());
            }

            int visualWidth = Math.max(1, Math.round((element == HudElement.RAID ? getRaidContentWidth() : ORIGINAL_FRAME_SIZE) * elementScale));
            int visualHeight = Math.max(1, Math.round(ORIGINAL_FRAME_SIZE * elementScale));
            float visualX = cardX + (cardWidth - visualWidth) / 2.0f;
            float visualY = cardY + (cardHeight - visualHeight) / 2.0f - 4.0f * unit;
            debugBounds.put(element, new PreviewBounds(visualX, visualY, visualWidth, visualHeight, elementScale,
                    cardX, cardY, cardWidth, cardHeight));
        }
    }

    private float getDebugTitleY() {
        float unit = getDebugUnit();
        float availableHeight = Math.max(1.0f, getFooterLayout().y - 8.0f - getDebugHeaderHeight());
        float galleryHeight = 3.0f * 58.0f * unit + 2.0f * 12.0f * unit;
        float titleHeight = 20.0f * unit;
        float fit = Math.min(1.0f, Math.min((width - 16.0f * unit) / (3.0f * 112.0f * unit + 2.0f * 12.0f * unit),
                (availableHeight - titleHeight) / galleryHeight));
        unit *= Math.max(0.20f, fit);
        float finalGalleryHeight = 3.0f * 58.0f * unit + 2.0f * 12.0f * unit;
        return getDebugHeaderHeight() + Math.max(4.0f * unit, (availableHeight - 20.0f * unit - finalGalleryHeight) / 2.0f + 5.0f * unit);
    }

    private void drawDebugButtonOnly(DrawContext drawContext) {
        FooterLayout footer = getFooterLayout();
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(footer.x, footer.y, 0);
        drawContext.getMatrices().scale(footer.scale, footer.scale, 1.0f);
        drawButton(drawContext, footer.debugButtonX(), footer.buttonY(), getDebugButtonWidth(), string("editor.debug"), true);
        drawContext.getMatrices().pop();
    }

    private void drawScaledCenteredText(DrawContext drawContext, String text, float centerX, float y,
                                        float scale, int color) {
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(centerX, y, 0);
        scale = Math.min(scale, (width - 16.0f) / Math.max(1, textRenderer.getWidth(text)));
        drawContext.getMatrices().scale(scale, scale, 1.0f);
        drawContext.drawCenteredTextWithShadow(textRenderer, Text.literal(text), 0, 0, color);
        drawContext.getMatrices().pop();
    }

    private HudElement findElementAtName(Map<HudElement, PreviewBounds> boundsMap, double mouseX, double mouseY) {
        HudElement[] elements = HudElement.values();
        for (int index = elements.length - 1; index >= 0; index--) {
            PreviewBounds bounds = boundsMap.get(elements[index]);
            if (bounds != null && bounds.contains(mouseX, mouseY)) {
                return elements[index];
            }
        }
        return null;
    }

    private String getElementLabel(HudElement element) {
        return switch (element) {
            case RAID -> string("element.raid");
            case BOW -> string("element.bow");
            case CROSSBOW -> string("element.crossbow");
            case EXPLOSION -> string("element.explosion");
            case GHAST -> string("element.ghast");
            case SHULKER -> string("element.shulker");
            case WARDEN -> string("element.warden");
        };
    }

    private boolean isElementEnabled(HudElement element) {
        return switch (element) {
            case RAID -> SixthSenseConfig.instance.enableRaidWarning;
            case BOW -> SixthSenseConfig.instance.enableBowWarning;
            case CROSSBOW -> SixthSenseConfig.instance.enableCrossbowWarning;
            case EXPLOSION -> SixthSenseConfig.instance.enableExplosionWarning;
            case GHAST -> SixthSenseConfig.instance.enableGhastWarning;
            case SHULKER -> SixthSenseConfig.instance.enableShulkerWarning;
            case WARDEN -> SixthSenseConfig.instance.enableWardenWarning;
        };
    }

    private SixthSenseConfig.AnimPos getPosition(HudElement element) {
        return switch (element) {
            case RAID -> SixthSenseConfig.instance.raidPos;
            case BOW -> SixthSenseConfig.instance.bowPos;
            case CROSSBOW -> SixthSenseConfig.instance.crossbowPos;
            case EXPLOSION -> SixthSenseConfig.instance.explosionPos;
            case GHAST -> SixthSenseConfig.instance.ghastPos;
            case SHULKER -> SixthSenseConfig.instance.shulkerPos;
            case WARDEN -> SixthSenseConfig.instance.wardenPos;
        };
    }

    private int getGridButtonWidth() {
        return Math.max(54, Math.max(
                textRenderer.getWidth(string("editor.grid", string("state.on"))),
                textRenderer.getWidth(string("editor.grid", string("state.off")))) + 12);
    }

    private int getDebugButtonWidth() {
        return Math.max(58, textRenderer.getWidth(string("editor.debug")) + 12);
    }

    private FooterLayout getFooterLayout() {
        float scale = getChromeScale();
        int virtualWidth = Math.max(1, Math.round((width - FOOTER_MARGIN * 2.0f) / scale));
        float y = Math.max(0.0f, height - FOOTER_MARGIN - FOOTER_HEIGHT * scale);
        return new FooterLayout(FOOTER_MARGIN, y, scale, virtualWidth);
    }

    private float getChromeScale() {
        if (client == null) {
            return 1.0f;
        }
        float guiScale = Math.max(1.0f, (float) client.getWindow().getScaleFactor());
        float preferred = Math.min(1.0f, 3.0f / guiScale);
        float minimumFooterWidth = getGridButtonWidth() + getDebugButtonWidth() + 140.0f;
        return Math.min(preferred, Math.max(0.1f, (width - FOOTER_MARGIN * 2.0f) / minimumFooterWidth));
    }

    private float getDebugUnit() {
        if (client == null) {
            return 1.0f;
        }
        return 3.0f / Math.max(1.0f, (float) client.getWindow().getScaleFactor());
    }

    private int getContentBottom() {
        return Math.max(0, (int) Math.floor(getFooterLayout().y - 6.0f));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmResetAll) {
            return handleConfirmationClick(mouseX, mouseY, button);
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (debugMode && isOverInfo(mouseX, mouseY) && client != null) {
                setCursorHidden(false);
                client.setScreen(new HudFeatureInfoScreen(this));
                return true;
            }
            if (debugMode && isOverImageReload(mouseX, mouseY)) {
                reloadHudImages();
                return true;
            }
            FooterLayout footer = getFooterLayout();
            float virtualX = footer.virtualX(mouseX);
            float virtualY = footer.virtualY(mouseY);
            if (isInsideVirtualButton(virtualX, virtualY, footer.debugButtonX(), footer.buttonY(), getDebugButtonWidth())) {
                toggleDebugMode();
                return true;
            }
            if (!debugMode && isInsideVirtualButton(virtualX, virtualY, footer.gridButtonX(), footer.buttonY(), getGridButtonWidth())) {
                gridEnabled = !gridEnabled;
                SixthSenseConfig.instance.editorGridEnabled = gridEnabled;
                return true;
            }
        }

        if (debugMode) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            HudElement clickedElement = findElementAtName(previewBounds, mouseX, mouseY);
            if (clickedElement != null) {
                PreviewBounds bounds = previewBounds.get(clickedElement);
                dragOffsetX = (float) mouseX - bounds.x;
                dragOffsetY = (float) mouseY - bounds.y;
                selectedElement = clickedElement;
                draggingElement = clickedElement;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInsideVirtualButton(float mouseX, float mouseY, int x, int y, int buttonWidth) {
        return mouseX >= x && mouseX <= x + buttonWidth && mouseY >= y && mouseY <= y + BUTTON_HEIGHT;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (debugMode || confirmResetAll) {
            return true;
        }
        if (draggingElement != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            SixthSenseConfig.AnimPos position = getPosition(draggingElement);
            PreviewBounds bounds = previewBounds.get(draggingElement);
            float x = clampPosition((float) mouseX - dragOffsetX, bounds.width, width);
            float y = clampPosition((float) mouseY - dragOffsetY, bounds.height, getContentBottom());
            x = snapPosition(draggingElement, x, bounds.width, width, true);
            y = snapPosition(draggingElement, y, bounds.height, getContentBottom(), false);
            position.place(x, y, bounds.width, bounds.height, width, height);
            buildPreviewBounds();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingElement != null) {
            draggingElement = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (debugMode || confirmResetAll) {
            return true;
        }
        HudElement hovered = findElementAtName(previewBounds, mouseX, mouseY);
        if (hovered != null && verticalAmount != 0.0) {
            selectedElement = hovered;
            SixthSenseConfig.AnimPos position = getPosition(hovered);
            float scaleStep = verticalAmount > 0.0 ? 0.05f : -0.05f;
            position.scale = MathHelper.clamp(position.scale + scaleStep, 0.1f, 5.0f);

            if (position.x != -1 && position.y != -1) {
                float elementWidth = getElementWidth(hovered);
                float elementHeight = getElementHeight(hovered);
                float x = position.resolveX(width, elementWidth, 0);
                float y = position.resolveY(height, elementHeight, 0);
                if (gridEnabled && hovered != HudElement.RAID) {
                    x = snapPosition(hovered, x, elementWidth, width, true);
                    y = snapPosition(hovered, y, elementHeight, getContentBottom(), false);
                }
                position.place(x, y, elementWidth, elementHeight, width, height);
            }
            buildPreviewBounds();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private float snapPosition(HudElement element, float position, float elementSize, int screenSize, boolean horizontal) {
        if (!gridEnabled || element == HudElement.RAID) {
            return position;
        }
        float anchor = horizontal ? getGridAnchorX() : getGridAnchorY();
        float center = position + elementSize / 2.0f;
        float snappedCenter = anchor + Math.round((center - anchor) / GRID_SIZE) * GRID_SIZE;
        return clampPosition(snappedCenter - elementSize / 2.0f, elementSize, screenSize);
    }

    private static float clampPosition(float position, float elementSize, int screenSize) {
        return MathHelper.clamp(position, 0.0f, Math.max(0.0f, screenSize - elementSize));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmResetAll) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_Y) {
                resetAllPositions();
                confirmResetAll = false;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_N) {
                confirmResetAll = false;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_R) {
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                confirmResetAll = true;
                setCursorHidden(false);
            } else if (selectedElement != null) {
                resetPosition(selectedElement);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_G && !debugMode) {
            gridEnabled = !gridEnabled;
            SixthSenseConfig.instance.editorGridEnabled = gridEnabled;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_D) {
            toggleDebugMode();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void toggleDebugMode() {
        debugMode = !debugMode;
        debugStartTime = System.currentTimeMillis();
        if (!debugMode) {
            setCursorHidden(false);
        }
    }

    private void drawResetConfirmation(DrawContext drawContext) {
        drawContext.fill(0, 0, width, height, 0xA8000000);
        ConfirmationLayout layout = getConfirmationLayout();
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(layout.x, layout.y, 0);
        drawContext.getMatrices().scale(layout.scale, layout.scale, 1.0f);
        drawContext.fill(0, 0, layout.virtualWidth, layout.virtualHeight, 0xF01B2228);
        drawContext.drawBorder(0, 0, layout.virtualWidth, layout.virtualHeight, 0xFFE5B95A);
        int textY = 8;
        for (var line : textRenderer.wrapLines(SixthSenseLanguage.text("editor.reset_title"), layout.virtualWidth - 24)) {
            drawContext.drawTextWithShadow(textRenderer, line, 12, textY, 0xFFFFFFFF);
            textY += textRenderer.fontHeight + 2;
        }
        textY += 4;
        for (var line : textRenderer.wrapLines(SixthSenseLanguage.text("editor.reset_body"), layout.virtualWidth - 24)) {
            drawContext.drawTextWithShadow(textRenderer, line, 12, textY, 0xFFDDDDDD);
            textY += textRenderer.fontHeight + 2;
        }
        drawButton(drawContext, 12, layout.buttonY(), layout.buttonWidth(), string("editor.reset_all"), true);
        drawButton(drawContext, layout.cancelX(), layout.buttonY(), layout.buttonWidth(), string("editor.cancel"), false);
        drawContext.getMatrices().pop();
    }

    private ConfirmationLayout getConfirmationLayout() {
        float scale = getChromeScale();
        int virtualWidth = Math.max(80, Math.min(320, (int) ((width - 16) / scale)));
        int lines = textRenderer.wrapLines(SixthSenseLanguage.text("editor.reset_title"), virtualWidth - 24).size()
                + textRenderer.wrapLines(SixthSenseLanguage.text("editor.reset_body"), virtualWidth - 24).size();
        int virtualHeight = 40 + lines * (textRenderer.fontHeight + 2);
        scale = Math.min(scale, Math.max(0.1f, (height - 16.0f) / virtualHeight));
        float x = (width - virtualWidth * scale) / 2.0f;
        float y = (height - virtualHeight * scale) / 2.0f;
        return new ConfirmationLayout(x, y, scale, virtualWidth, virtualHeight);
    }

    private boolean handleConfirmationClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        ConfirmationLayout layout = getConfirmationLayout();
        float virtualX = layout.virtualX(mouseX);
        float virtualY = layout.virtualY(mouseY);
        if (isInsideVirtualButton(virtualX, virtualY, 12, layout.buttonY(), layout.buttonWidth())) {
            resetAllPositions();
            confirmResetAll = false;
        } else if (isInsideVirtualButton(virtualX, virtualY, layout.cancelX(), layout.buttonY(), layout.buttonWidth())) {
            confirmResetAll = false;
        }
        return true;
    }

    private void resetAllPositions() {
        for (HudElement element : HudElement.values()) {
            resetPosition(element);
        }
        selectedElement = null;
        draggingElement = null;
    }

    private void resetPosition(HudElement element) {
        SixthSenseConfig.AnimPos position = getPosition(element);
        position.reset(element == HudElement.RAID ? 0.6f : 0.7f);
    }

    private void setCursorHidden(boolean hidden) {
        if (cursorHidden == hidden || client == null) {
            return;
        }
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR,
                hidden ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
        cursorHidden = hidden;
    }

    @Override
    public void close() {
        setCursorHidden(false);
        SixthSenseConfig.instance.editorGridEnabled = gridEnabled;
        SixthSenseConfig.save();
        if (client != null) {
            client.setScreen(null);
        }
    }
}
