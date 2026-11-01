package com.surins.mixin;

import com.surins.WhisperHeart;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class WhisperHeartMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void sixthsense$beginHeartFrame(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        WhisperHeart.beginFrame();
    }
    @Inject(method = "renderHealthBar", at = @At("HEAD"))
    private void sixthsense$beginBar(DrawContext context, net.minecraft.entity.player.PlayerEntity player,
            int x, int y, int lines, int regeneratingHeartIndex, float maxHealth,
            int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        WhisperHeart.beginBar(context, x, y);
    }
    @Inject(method = "renderHealthBar", at = @At("TAIL"))
    private void sixthsense$endBar(DrawContext context, net.minecraft.entity.player.PlayerEntity player,
            int x, int y, int lines, int regeneratingHeartIndex, float maxHealth,
            int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        WhisperHeart.endBar();
    }
    @Inject(method = "drawHeart", at = @At("TAIL"))
    private void sixthsense$captureHeart(DrawContext context, @Coerce Object type, int x, int y,
                                       boolean hardcore, boolean blinking, boolean half, CallbackInfo ci) {
        WhisperHeart.capture(context, x, y, half);
    }
}
