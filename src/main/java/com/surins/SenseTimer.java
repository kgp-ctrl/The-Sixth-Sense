package com.surins;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Arm;

/** A quiet 3px time column, outside the hotbar and offhand slot. */
final class SenseTimer {
    static void initialize(){HudRenderCallback.EVENT.register((c,t)->{
        var mc=MinecraftClient.getInstance();var state=SenseAbility.snapshot(mc);
        if(state==null || state.active()<=0 || mc.options.hudHidden || mc.currentScreen!=null || !SixthSenseConfig.instance.hudEnabled)return;
        int x=c.getScaledWindowWidth()/2-99,y=c.getScaledWindowHeight()-20;
        if(mc.player.getMainArm()==Arm.RIGHT && !mc.player.getOffHandStack().isEmpty())x-=29;
        x=Math.max(2,x);int filled=Math.clamp((int)Math.ceil(18*state.active()/(float)SenseRules.DURATION),1,18);
        c.fill(x-1,y-1,x+4,y+19,0xA0101116);c.fill(x,y,x+3,y+18,0x80464B58);
        c.fill(x,y+18-filled,x+3,y+18,0xCCB0AEC7);
    });}
    private SenseTimer(){}
}
