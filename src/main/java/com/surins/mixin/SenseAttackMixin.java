package com.surins.mixin;
import com.surins.SenseGameplay;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerEntity.class)
abstract class SenseAttackMixin {
    @Unique private boolean sixthsense$ambush;
    @Inject(method = "attack", at = @At("HEAD"))
    private void sixthsense$prepare(Entity target, CallbackInfo ci) {
        sixthsense$ambush = SenseGameplay.assassination((PlayerEntity)(Object)this, target);
    }
    @ModifyArg(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"), index = 1)
    private float sixthsense$damage(float amount) { return sixthsense$ambush ? amount * 2 : amount; }
    @Inject(method = "attack", at = @At("RETURN"))
    private void sixthsense$clear(Entity target, CallbackInfo ci) { sixthsense$ambush = false; }
}
