package com.surins;

import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;

/** Server-thread authority, isolated per player on dedicated, LAN and integrated servers. */
public final class SenseServer {
    private static final Map<UUID,Session> sessions=new HashMap<>();
    private static final Set<UUID> sleepStarts=new HashSet<>();
    private static final class Session {
        Object dimension; long requestTick=-1000000; int sentActive=-1,sentNights=-1;
        final Set<UUID> noticed=new HashSet<>(); List<SensePackets.Presence> presences=List.of();
    }
    private SenseServer(){}
    static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(SensePackets.Request.ID,(packet,context)->context.server().execute(()->request(context.player())));
        ServerTickEvents.END_SERVER_TICK.register(SenseServer::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{sessions.clear();sleepStarts.clear();});
        EntitySleepEvents.START_SLEEPING.register((entity,pos)->{if(entity instanceof ServerPlayerEntity p)sleepStarts.add(p.getUuid());});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;var saved=state(server);var e=saved.player(p.getUuid());
            if(e.active>0){e.active=0;saved.markDirty();}SenseGameplay.update(p,false);
            sessions.remove(p.getUuid());sleepStarts.remove(p.getUuid());
        });
    }
    static SenseState state(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(SenseState.TYPE,"sixthsense_sensing");}
    static boolean supported(ServerPlayerEntity p){return ServerPlayNetworking.canSend(p,SensePackets.State.ID);}
    private static void request(ServerPlayerEntity p){
        if(!supported(p) || !p.isAlive() || p.isSpectator() || p.isSleeping())return;
        Session session=sessions.computeIfAbsent(p.getUuid(),id->new Session());
        long now=p.getServerWorld().getTime();if(now-session.requestTick<10)return;session.requestTick=now;
        var saved=state(p.getServer());var entry=saved.player(p.getUuid());
        String reason=SenseRules.rejection(true,p.experienceLevel,entry.active,entry.nights,false);
        if(reason==null && targeted(p))reason="targeted";
        if(reason!=null){if(!reason.equals("active"))ServerPlayNetworking.send(p,new SensePackets.Notice(reason));return;}
        p.addExperience(-SenseRules.XP_COST);
        entry.start();saved.markDirty();session.noticed.clear();session.presences=List.of();session.dimension=p.getWorld().getRegistryKey();
        SenseGameplay.update(p,true);send(p,session,entry);
        ServerPlayNetworking.send(p,new SensePackets.Notice("started"));
    }
    private static boolean targeted(ServerPlayerEntity p){
        for(var e:p.getServerWorld().iterateEntities())if(e instanceof MobEntity m && m.isAlive() && m.getTarget()==p)return true;
        return false;
    }
    private static void tick(MinecraftServer server){
        var saved=state(server);
        for(var p:server.getPlayerManager().getPlayerList()){
            if(!supported(p))continue;
            Session s=sessions.computeIfAbsent(p.getUuid(),id->new Session());var e=saved.player(p.getUuid());
            Object dimension=p.getWorld().getRegistryKey();
            if(!p.isAlive() || p.isSpectator() || p.isSleeping() || s.dimension!=null && s.dimension!=dimension){
                if(e.active>0){e.active=0;saved.markDirty();}
            }
            if(s.dimension!=dimension)s.sentActive=-1;
            s.dimension=dimension;
            if(e.active>0){
                e.active--;saved.markDirty();
                if(e.active%4==0){
                    List<SensePackets.Presence> found=new ArrayList<>();
                    for(var mob:p.getServerWorld().getEntitiesByClass(MobEntity.class,p.getBoundingBox().expand(SenseRules.RADIUS),
                            m->m.isAlive() && (m instanceof Monster || m.getTarget()==p) && SenseGameplay.inRange(p,m))){
                        if(found.size()>=SensePackets.MAX_PRESENCES)break;
                        if(mob.getTarget()==p)s.noticed.add(mob.getUuid());
                        found.add(new SensePackets.Presence(mob.getUuid(),mob.getId(),s.noticed.contains(mob.getUuid())));
                    }
                    s.presences=List.copyOf(found);
                }
            }else{s.presences=List.of();s.noticed.clear();}
            SenseGameplay.update(p,e.active>0);
            if(s.sentNights!=e.nights || e.active==0 && (s.sentActive!=0 || server.getTicks()%20==0) || e.active>0 && e.active%4==0)send(p,s,e);
        }
    }
    private static void send(ServerPlayerEntity p,Session s,SenseState.Entry e){
        ServerPlayNetworking.send(p,new SensePackets.State(p.getWorld().getRegistryKey().getValue(),e.active,e.nights,s.presences));
        s.sentActive=e.active;s.sentNights=e.nights;
    }
    public static void completedNight(ServerWorld world){
        var saved=state(world.getServer());
        for(var p:world.getPlayers())if(sleepStarts.remove(p.getUuid()) && p.isSleeping() && saved.player(p.getUuid()).rest(p.getSleepTimer())){
            saved.markDirty();SenseGameplay.update(p,false);
            if(supported(p)){Session s=sessions.computeIfAbsent(p.getUuid(),id->new Session());s.presences=List.of();s.noticed.clear();send(p,s,saved.player(p.getUuid()));}
        }
    }
}
