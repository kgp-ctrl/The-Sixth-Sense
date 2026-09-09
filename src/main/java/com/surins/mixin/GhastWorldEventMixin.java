package com.surins.mixin;

import com.surins.GhastThreatTracker;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class GhastWorldEventMixin {
    @Inject(method = "syncWorldEvent", at = @At("HEAD"))
    private void sixthsense$trackGhastShot(PlayerEntity player, int eventId, BlockPos pos, int data, CallbackInfo ci) {
        GhastThreatTracker.onWorldEvent(eventId, pos);
    }
}
