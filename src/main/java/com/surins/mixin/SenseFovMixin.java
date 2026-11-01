package com.surins.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(net.minecraft.client.render.GameRenderer.class)
abstract class SenseFovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void sixthsense$view(net.minecraft.client.render.Camera camera, float delta, boolean changing, CallbackInfoReturnable<Double> cir) { if (changing) cir.setReturnValue(com.surins.SenseView.fov(cir.getReturnValue())); }
}
