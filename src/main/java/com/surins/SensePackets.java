package com.surins;

import java.util.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Versioned, bounded messages. Clients request activation; only the server supplies state. */
public final class SensePackets {
    static final int MAX_PRESENCES=512;
    record Presence(UUID id,int entityId,boolean noticed) { }
    public record Request() implements CustomPayload {
        public static final Id<Request> ID=new Id<>(Identifier.of("sixthsense","request_v1"));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.unit(new Request());
        public Id<Request> getId(){return ID;}
    }
    public record State(Identifier dimension,int active,int nights,List<Presence> presences) implements CustomPayload {
        public static final Id<State> ID=new Id<>(Identifier.of("sixthsense","state_v1"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=PacketCodec.ofStatic(SensePackets::writeState,SensePackets::readState);
        public Id<State> getId(){return ID;}
    }
    public record Notice(String reason) implements CustomPayload {
        public static final Id<Notice> ID=new Id<>(Identifier.of("sixthsense","notice_v1"));
        public static final PacketCodec<RegistryByteBuf,Notice> CODEC=PacketCodec.ofStatic((buf,p)->buf.writeString(p.reason,32),buf->new Notice(buf.readString(32)));
        public Id<Notice> getId(){return ID;}
    }
    private static void writeState(RegistryByteBuf b,State p){
        b.writeIdentifier(p.dimension);b.writeVarInt(p.active);b.writeVarInt(p.nights);b.writeVarInt(p.presences.size());
        for(var e:p.presences){b.writeUuid(e.id);b.writeVarInt(e.entityId);b.writeBoolean(e.noticed);}
    }
    private static State readState(RegistryByteBuf b){
        var dimension=b.readIdentifier();int active=b.readVarInt(),nights=b.readVarInt(),size=b.readVarInt();
        if(active<0 || active>SenseRules.DURATION || nights<0 || nights>1 || size<0 || size>MAX_PRESENCES)
            throw new IllegalArgumentException("Invalid Sixth Sense state");
        var list=new ArrayList<Presence>(size);
        for(int i=0;i<size;i++)list.add(new Presence(b.readUuid(),b.readVarInt(),b.readBoolean()));
        return new State(dimension,active,nights,List.copyOf(list));
    }
    static void register(){
        PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);
        PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);
        PayloadTypeRegistry.playS2C().register(Notice.ID,Notice.CODEC);
    }
    private SensePackets(){}
}
