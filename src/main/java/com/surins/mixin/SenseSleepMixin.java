package com.surins.mixin;

import com.surins.SenseServer;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
abstract class SenseSleepMixin {
    @Inject(method = "wakeSleepingPlayers", at = @At("HEAD"))
    private void sixthsense$rest(CallbackInfo ci) { SenseServer.completedNight((ServerWorld)(Object)this); }
}
