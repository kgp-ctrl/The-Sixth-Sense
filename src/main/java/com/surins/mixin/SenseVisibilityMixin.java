package com.surins.mixin;

import com.surins.SenseCloudRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Invisible threats keep their presence, without changing the entity or the normal render pass. */
@Mixin(LivingEntityRenderer.class)
abstract class SenseVisibilityMixin {
    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void sixthsense$presence(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SenseCloudRenderer.renderingPresence(entity)) cir.setReturnValue(true);
    }
}
