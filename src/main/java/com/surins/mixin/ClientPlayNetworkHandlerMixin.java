package com.surins.mixin;

import com.surins.WardenThreatTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    // TAIL executes after forceMainThread, so packet/entity access stays on the client thread.
    @Inject(method = "onEntityDamage", at = @At("TAIL"))
    private void sixthsense$onEntityDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        WardenThreatTracker.onDamage(MinecraftClient.getInstance(), packet);
    }
}
