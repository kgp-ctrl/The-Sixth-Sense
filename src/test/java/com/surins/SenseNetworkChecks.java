package com.surins;

import java.util.List;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;

/** Wire bounds and player isolation must remain independent of rendering and networking callbacks. */
final class SenseNetworkChecks {
    static void run() {
        var a=UUID.randomUUID();var b=UUID.randomUUID();
        var state=new SenseState();state.player(a).start();
        check(state.player(b).active==0 && state.player(b).nights==0,"Starting one player changed another");
        state.player(b).start();state.player(a).rest(100);
        check(state.player(b).active==1200 && state.player(b).nights==1,"Sleep cleared another player's state");
        var restored=SenseState.read(state.writeNbt(new net.minecraft.nbt.NbtCompound(),null),null);
        check(restored.player(a).nights==0 && restored.player(b).nights==1 && restored.player(b).active==0,"Save mixed player states");
        var buf=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
        try {
            var packet=new SensePackets.State(Identifier.ofVanilla("overworld"),1200,1,List.of(new SensePackets.Presence(a,42,true)));
            SensePackets.State.CODEC.encode(buf,packet);
            check(packet.equals(SensePackets.State.CODEC.decode(buf)) && !buf.isReadable(),"State packet round trip failed");
            for(int[] values:new int[][]{{-1,0,0},{1201,0,0},{0,-1,0},{0,2,0},{0,0,-1},{0,0,513}}) {
                buf.clear();buf.writeIdentifier(Identifier.ofVanilla("overworld"));
                for(int v:values)buf.writeVarInt(v);
                boolean rejected=false;
                try{SensePackets.State.CODEC.decode(buf);}catch(IllegalArgumentException expected){rejected=true;}
                check(rejected,"Out-of-range packet accepted");
            }
        }finally{buf.release();}
        System.out.println("Network checks passed: bounded payloads, round trip, isolated recovery and save state.");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
