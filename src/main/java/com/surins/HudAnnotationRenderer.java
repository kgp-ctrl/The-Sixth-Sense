package com.surins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** The live HUD, layout editor and debug previews share the same small number sizing. */
final class HudAnnotationRenderer {
    private HudAnnotationRenderer() { }
    static void draw(DrawContext context,String text,float x,float y,float iconScale,float alpha,boolean distance) {
        var client=MinecraftClient.getInstance();var font=client.textRenderer;
        int width=font.getWidth(text),height=font.fontHeight;
        float scale=HudLayoutMath.annotationScale(iconScale,client.getWindow().getScaleFactor(),width,height,distance);
        float size=16*iconScale;
        context.getMatrices().push();
        context.getMatrices().translate(x+size-width*scale-.5f,y+size-height*scale-.5f,0);
        context.getMatrices().scale(scale,scale,1);
        context.drawText(font,text,0,0,((int)(alpha*255)<<24)|0xFFFFFF,true);
        context.getMatrices().pop();
    }
}
