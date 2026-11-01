package com.surins;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/** Client view of server-authorized state. No gameplay mutation or client-supplied targets. */
public final class SenseAbility {
    record Presence(UUID id,int entityId,boolean noticed){}
    record Snapshot(Object dimension,int active,int nights,List<Presence> presences){}
    private static Snapshot snapshot;
    private static SensePackets.State pending;
    private static Object world;
    private SenseAbility(){}
    static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(SensePackets.State.ID,(packet,context)->context.client().execute(()->pending=packet));
        ClientPlayNetworking.registerGlobalReceiver(SensePackets.Notice.ID,(packet,context)->context.client().execute(()->SenseWhisper.show(packet.reason())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{snapshot=null;pending=null;world=null;SenseView.reset();SenseCloudRenderer.reset();});
        SenseView.initialize();SenseCloudRenderer.initialize();SenseScreenEffects.initialize();SenseWhisper.initialize();SenseTimer.initialize();
    }
    public static boolean available(MinecraftClient client){
        return client.player!=null && client.world!=null && ClientPlayNetworking.canSend(SensePackets.Request.ID);
    }
    static void tick(MinecraftClient client){
        if(world!=client.world){world=client.world;snapshot=null;}
        if(!available(client)){snapshot=null;pending=null;}
        else if(pending!=null){
            if(pending.dimension().equals(client.world.getRegistryKey().getValue())){
                snapshot=new Snapshot(client.world.getRegistryKey(),pending.active(),pending.nights(),pending.presences().stream()
                        .map(p->new Presence(p.id(),p.entityId(),p.noticed())).toList());pending=null;
            }
        }else if(snapshot!=null && snapshot.active()>0 && !client.isPaused())
            snapshot=new Snapshot(snapshot.dimension(),snapshot.active()-1,snapshot.nights(),snapshot.presences());
        SenseView.tick(client);
    }
    static Snapshot snapshot(MinecraftClient client){return available(client) && snapshot!=null && snapshot.dimension()==client.world.getRegistryKey()?snapshot:null;}
    static void request(MinecraftClient client){
        if(!available(client)){if(client.player!=null)SenseWhisper.show("unavailable");return;}
        ClientPlayNetworking.send(new SensePackets.Request());
    }
}
