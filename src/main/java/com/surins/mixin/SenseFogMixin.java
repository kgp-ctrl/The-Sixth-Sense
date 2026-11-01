package com.surins.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(net.minecraft.client.render.BackgroundRenderer.class)
abstract class SenseFogMixin {
    @Inject(method = "applyFog", at = @At("RETURN"))
    private static void sixthsense$view(net.minecraft.client.render.Camera camera, net.minecraft.client.render.BackgroundRenderer.FogType type, float distance, boolean thick, float delta, CallbackInfo cir) { if (type == net.minecraft.client.render.BackgroundRenderer.FogType.FOG_TERRAIN) com.surins.SenseView.fog(camera); }
}
