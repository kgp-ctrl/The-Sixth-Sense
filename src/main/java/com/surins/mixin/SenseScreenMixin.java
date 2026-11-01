package com.surins.mixin;
import com.surins.SenseScreenEffects;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
abstract class SenseScreenMixin {
    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void sixthsense$screen(RenderTickCounter counter, CallbackInfo ci) { SenseScreenEffects.render(); }
}
