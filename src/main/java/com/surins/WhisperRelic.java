package com.surins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/** Cached native 16px orb and compact Minecraft-style pixel frame. Skin sampling and texture generation never run per frame. */
final class WhisperRelic {
    private static final Identifier ID = Identifier.of("sixthsense", "dynamic/whisper_orb");
    private static final Identifier PANEL = Identifier.of("sixthsense", "dynamic/whisper_panel");
    private static final Identifier HEART = Identifier.of("sixthsense", "dynamic/whisper_heart");
    private static NativeImageBackedTexture heart;
    private static NativeImageBackedTexture panel;
    private static volatile int generation;
    private static int sampledGeneration = -1, skinGlId = -1;
    private static Identifier skin;
    private static NativeImageBackedTexture texture;
    private static final int[] orbPixels=new int[16*16];

    static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() { return ID; }
            public void reload(ResourceManager manager) { generation++; }
        });
    }

    static int revision() { return generation; }

    static void prepare(MinecraftClient client) {
        if (client.player == null) return;
        Identifier current = client.player.getSkinTextures().texture();
        var skinTexture = client.getTextureManager().getTexture(current);
        int glId = skinTexture.getGlId();
        if (!current.equals(skin) || glId != skinGlId || sampledGeneration != generation) {
            int accent = readEyeColor(glId);
            NativeImage image = new NativeImage(WhisperRelicMath.SIZE, WhisperRelicMath.SIZE, false);
            for (int py=0;py<WhisperRelicMath.SIZE;py++) for (int px=0;px<WhisperRelicMath.SIZE;px++)
                { int color=WhisperRelicMath.pixel(px,py,accent);orbPixels[py*16+px]=color;
                  image.setColor(px,py,WhisperRelicMath.swapRedBlue(color)); }
            if (texture == null) {
                texture = new NativeImageBackedTexture(image);
                client.getTextureManager().registerTexture(ID, texture);
            } else { texture.setImage(image); texture.upload(); }
            texture.setFilter(false, false);
            NativeImage heartImage = new NativeImage(9,9,false);
            for (int y=0;y<9;y++) for (int x=0;x<9;x++)
                heartImage.setColor(x,y,WhisperRelicMath.swapRedBlue(WhisperRelicMath.heartPixel(x,y,accent)));
            if (heart == null) {
                heart = new NativeImageBackedTexture(heartImage); client.getTextureManager().registerTexture(HEART,heart);
            } else { heart.setImage(heartImage); heart.upload(); }
            heart.setFilter(false,false);
            skin = current; skinGlId = glId; sampledGeneration = generation;
        }
    }

    static void draw(DrawContext context, MinecraftClient client, float x, float y, float alpha, float emergence) {
        if(texture==null)return;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        if(!SixthSenseConfig.instance.reduceMotion && emergence<1 && WhisperHeart.visible()){
            // Opaque pixels travel up a narrow throat, then fan out into their final sphere positions.
            // No whole-sphere translation or fading: reversing emergence reverses every particle path.
            float sourceX=WhisperHeart.x()+WhisperHeart.width()/2;
            float sourceY=WhisperHeart.y()+WhisperHeart.height()*.25f;
            var matrices=context.getMatrices();
            for(int py=0;py<16;py++)for(int px=0;px<16;px++){
                int color=orbPixels[py*16+px];if(color==0)continue;
                float start=(15-py)/15f*.50f;
                float travel=Math.clamp((emergence-start)/.50f,0,1);if(travel<=0)continue;
                float t=SenseVisualMath.smooth(travel), spread=t*t;
                float originX=sourceX+(px-7.5f)*.08f;
                float destX=x+px*.75f,destY=y+py*.75f;
                float drawX=originX+(destX-originX)*spread,drawY=sourceY+(destY-sourceY)*t;
                float size=.3f+.45f*t;
                matrices.push();matrices.translate(drawX,drawY,0);matrices.scale(size,size,1);
                context.fill(0,0,1,1,color);matrices.pop();
            }
            context.draw();return;
        }
        var matrices=context.getMatrices();matrices.push();matrices.translate(x,y,0);
        context.setShaderColor(1,1,1,alpha);
        try{context.drawTexture(ID,0,0,12,12,0,0,16,16,16,16);}
        finally{context.setShaderColor(1,1,1,1);matrices.pop();}
    }

    static void drawBacking(DrawContext context, MinecraftClient client, int x, int y, int width, int height, float alpha) {
        if (panel == null) {
            NativeImage image=new NativeImage(16,16,false);
            for (int py=0;py<16;py++) for (int px=0;px<16;px++)
                image.setColor(px,py,WhisperRelicMath.swapRedBlue(WhisperRelicMath.panelPixel(px,py)));
            panel=new NativeImageBackedTexture(image);
            client.getTextureManager().registerTexture(PANEL,panel);
            panel.setFilter(false,false);
        }
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        context.setShaderColor(1,1,1,alpha);
        try {
            // Nine slices keep all corner pixels square even when translated text wraps.
            for (int row=0;row<3;row++) for (int col=0;col<3;col++) {
                int dx=col==0?0:col==1?3:width-3, dy=row==0?0:row==1?3:height-3;
                int dw=col==1?width-6:3, dh=row==1?height-6:3;
                int u=col==0?0:col==1?3:13, v=row==0?0:row==1?3:13;
                context.drawTexture(PANEL,x+dx,y+dy,dw,dh,u,v,col==1?10:3,row==1?10:3,16,16);
            }
        } finally { context.setShaderColor(1,1,1,1); }
    }

    static void drawHeart(DrawContext context, MinecraftClient client, float extraction, float alpha) {
        if (!WhisperHeart.visible() || heart == null || extraction >= 1) return;
        var matrices = context.getMatrices(); matrices.push();
        matrices.translate(WhisperHeart.x(),WhisperHeart.y(),200);
        matrices.scale(WhisperHeart.width()/9,WhisperHeart.height()/9,1);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        boolean reduced = SixthSenseConfig.instance.reduceMotion;
        context.setShaderColor(1,1,1,reduced ? alpha*(1-extraction) : alpha);
        float right = WhisperHeart.x()+WhisperHeart.width()*(WhisperHeart.half()?4/9f:1);
        float bottom = WhisperHeart.y()+WhisperHeart.height()*(reduced?1:1-extraction);
        context.enableScissor((int)Math.floor(WhisperHeart.x()),(int)Math.floor(WhisperHeart.y()),
                (int)Math.ceil(right),(int)Math.ceil(bottom));
        try { context.drawTexture(HEART,0,0,0,0,9,9,9,9); }
        finally { context.disableScissor(); context.setShaderColor(1,1,1,1); matrices.pop(); }
    }

    private static int readEyeColor(int glId) {
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            RenderSystem.bindTexture(glId);
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width < 64 || width > 1024 || height < width / 2 || height > 1024) return WhisperRelicMath.NEUTRAL;
            try (NativeImage image = new NativeImage(width, height, false)) {
                image.loadFromTextureImage(0, false);
                return WhisperRelicMath.eyeColor(width, height, (x, y) -> WhisperRelicMath.swapRedBlue(image.getColor(x, y)));
            }
        } catch (RuntimeException ignored) {
            return WhisperRelicMath.NEUTRAL;
        } finally { RenderSystem.bindTexture(bound); }
    }
    private WhisperRelic() { }
}
