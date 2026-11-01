package com.surins;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;

/** World-owned state, accessed exclusively on the server thread. */
final class SenseState extends PersistentState {
    static final Type<SenseState> TYPE = new Type<>(SenseState::new, SenseState::read, null);
    static final class Entry {
        int active, nights;
        int lastSneakGroundTick = Integer.MIN_VALUE;
        void start() { lastSneakGroundTick = Integer.MIN_VALUE; active = SenseRules.DURATION; nights = SenseRules.REST_NIGHTS; }
        boolean rest(int sleepTicks) {
            if (nights == 0 || !SenseRules.rested(sleepTicks)) return false;
            active = 0; nights = 0; return true;
        }

    }
    private final Map<UUID, Entry> players = new HashMap<>();
    Entry player(UUID uuid) { return players.computeIfAbsent(uuid, ignored -> new Entry()); }
    static SenseState read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        SenseState state = new SenseState();
        for (String key : nbt.getKeys()) {
            UUID uuid;
            try { uuid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            NbtCompound data = nbt.getCompound(key);
            Entry entry = state.player(uuid);
            entry.active = 0; // An interrupted session never resumes on world load.
            entry.nights = Math.clamp(data.getInt("nights"), 0, SenseRules.REST_NIGHTS);
            if (data.getInt("active") > 0) entry.nights = SenseRules.REST_NIGHTS;
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        players.forEach((uuid, entry) -> {
            NbtCompound data = new NbtCompound();
            data.putInt("active", entry.active); data.putInt("nights", entry.nights);
            nbt.put(uuid.toString(), data);
        });
        return nbt;
    }
}
