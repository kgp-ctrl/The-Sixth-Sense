package com.surins.mixin;
import com.surins.SenseGameplay;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntity.class)
abstract class SenseStealthMixin {
    @Inject(method = "getAttackDistanceScalingFactor", at = @At("RETURN"), cancellable = true)
    private void sixthsense$visibility(Entity observer, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(SenseGameplay.visibility((LivingEntity)(Object)this, observer, cir.getReturnValue()));
    }
}
