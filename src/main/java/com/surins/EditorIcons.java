package com.surins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Toolbar PNGs, fitted proportionally without runtime cropping or distortion. */
enum EditorIcons {
    SETTINGS("settings",124,124), GRID("grid",63,63), DEBUG("debug",67,66);
    private final Identifier texture;
    private final int width, height;
    EditorIcons(String name, int width, int height) {
        this.texture = Identifier.of("sixthsense", "textures/settings/" + name + ".png");
        this.width = width; this.height = height;
    }
    void draw(DrawContext context, float x, float y, float boxSize) {
        float scale = boxSize / Math.max(width,height);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        context.getMatrices().push();
        context.getMatrices().translate(x + (boxSize - width * scale) / 2, y + (boxSize - height * scale) / 2, 0);
        context.getMatrices().scale(scale,scale,1);
        context.drawTexture(texture,0,0,0,0,width,height,width,height);
        context.getMatrices().pop();
        RenderSystem.disableBlend();
    }
}
