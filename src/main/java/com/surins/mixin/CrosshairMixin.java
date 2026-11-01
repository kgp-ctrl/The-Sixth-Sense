package com.surins.mixin;

import com.surins.BowAimAssist;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class CrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("TAIL"))
    private void sixthsense$aimCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        BowAimAssist.render(context, MinecraftClient.getInstance());
    }
}
