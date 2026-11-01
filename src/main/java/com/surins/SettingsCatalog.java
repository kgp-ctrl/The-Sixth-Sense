package com.surins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.village.raid.Raid;
import java.util.ArrayList;
import java.util.List;
import java.util.function.*;

/** One settings model and one UI, used from both H and Mod Menu. */
final class SettingsCatalog {
    sealed interface Entry permits Toggle, NumberValue { String key(); String help(); void reset(); }
    record Toggle(String key, String help, BooleanSupplier read, Consumer<Boolean> write, boolean defaultValue) implements Entry {
        public void reset() { write.accept(defaultValue); }
    }
    record NumberValue(String key, String help, DoubleSupplier read, DoubleConsumer write,
                       double defaultValue, double min, double max, boolean integer) implements Entry {
        public void reset() { write.accept(defaultValue); }
    }
    enum Group {
        MOBS("config.group.mobs"), WEAPONS("config.group.weapons"), EVENTS("config.group.events");
        final String key;
        Group(String key) { this.key=key; }
    }
    record Category(String key, Item item, List<Entry> entries) {
        ItemStack icon(MinecraftClient client) {
            return key.equals("config.raid") ? RaidIcon.STACK.copy() : new ItemStack(item);
        }
        Group group() {
            return switch(key) {
                case "config.raid", "config.explosion" -> Group.EVENTS;
                case "config.bow", "config.crossbow", "config.aim" -> Group.WEAPONS;
                default -> Group.MOBS;
            };
        }
        Toggle enabled() { return (Toggle) entries.getFirst(); }
        List<Entry> options() { return entries.subList(1,entries.size()); }
    }
    // Vanilla's own pattern stack, also available before joining a world. Lazy and built once.
    private static final class RaidIcon {
        static final ItemStack STACK=Raid.getOminousBanner(
                BuiltinRegistries.createWrapperLookup().getWrapperOrThrow(RegistryKeys.BANNER_PATTERN));
    }

    static List<Category> categories() {
        var c = SixthSenseConfig.instance;
        List<Category> result = new ArrayList<>();
        result.add(new Category("config.general", Items.REPEATER, List.of(
                new Toggle("technical.enabled", "technical.enabled_help", () -> c.hudEnabled, v -> c.hudEnabled = v, true),
                new Toggle("technical.creative", "technical.creative_help", () -> c.showInCreative, v -> c.showInCreative = v, false))));
        result.add(warning("config.raid", Items.WHITE_BANNER, () -> c.enableRaidWarning, v -> c.enableRaidWarning = v, c.raidPos, .6,
                new Toggle("config.raid_glow", "", () -> c.enableRaidGlow, v -> c.enableRaidGlow = v, true)));
        result.add(warning("config.bow", Items.BOW, () -> c.enableBowWarning, v -> c.enableBowWarning = v, c.bowPos, .7));
        result.add(warning("config.crossbow", Items.CROSSBOW, () -> c.enableCrossbowWarning, v -> c.enableCrossbowWarning = v, c.crossbowPos, .7));
        result.add(warning("config.explosion", Items.TNT, () -> c.enableExplosionWarning, v -> c.enableExplosionWarning = v, c.explosionPos, .7));
        result.add(warning("config.ghast", Items.GHAST_TEAR, () -> c.enableGhastWarning, v -> c.enableGhastWarning = v, c.ghastPos, .7,
                new Toggle("config.ghast_distance", "", () -> c.showGhastDistance, v -> c.showGhastDistance = v, true)));
        result.add(warning("config.shulker", Items.SHULKER_SHELL, () -> c.enableShulkerWarning, v -> c.enableShulkerWarning = v, c.shulkerPos, .7));
        result.add(warning("config.warden", Items.ECHO_SHARD, () -> c.enableWardenWarning, v -> c.enableWardenWarning = v, c.wardenPos, .7,
                new Toggle("config.warden_distance", "", () -> c.showWardenDistance, v -> c.showWardenDistance = v, false)));
        result.add(new Category("config.phantom", Items.PHANTOM_MEMBRANE, List.of(
                new Toggle("config.enabled", "", () -> c.enablePhantomWarning, v -> c.enablePhantomWarning = v, true),
                new NumberValue("config.scale", "technical.phantom_help", () -> c.phantomScale,
                        v -> c.phantomScale = (float) v, .65, .1, 5, false))));
        result.add(new Category("config.aim", Items.ARROW, List.of(
                new Toggle("config.enabled", "config.aim_help", () -> c.enableAimAssist, v -> c.enableAimAssist = v, true))));
        return result;
    }

    static Category special() {
        var c = SixthSenseConfig.instance;
        return new Category("config.special", Items.ENDER_EYE, List.of(
                new Toggle("config.inner_voice", "config.inner_voice_help", () -> c.innerVoice,
                        v -> { c.innerVoice = v; SenseWhisper.modeChanged(); }, false)));
    }

    private static Category warning(String key, Item item, BooleanSupplier read, Consumer<Boolean> write,
                                    SixthSenseConfig.AnimPos pos, double scale, Entry... extra) {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Toggle("config.enabled", "", read, write, true));
        entries.addAll(List.of(extra));
        entries.add(new NumberValue("config.scale", "", () -> pos.scale, v -> pos.scale = (float) v, scale, .1, 5, false));
        entries.add(new NumberValue("config.x", "config.position_help", () -> pos.x, v -> pos.setX((int) v), -1, -1, 32767, true));
        entries.add(new NumberValue("config.y", "config.position_help", () -> pos.y, v -> pos.setY((int) v), -1, -1, 32767, true));
        return new Category(key, item, entries);
    }

    static void refreshPositions(MinecraftClient client) {
        int width = client.getWindow().getScaledWidth(), height = client.getWindow().getScaledHeight();
        float raidWidth = client.textRenderer.getWidth(SixthSenseLanguage.text("editor.raid_example"))
                + 24 + client.textRenderer.getWidth("24m");
        SixthSenseConfig.migrateLegacyPositions(width, height, raidWidth);
        var c = SixthSenseConfig.instance;
        c.raidPos.refreshCoordinates(width, height, raidWidth);
        for (var pos : new SixthSenseConfig.AnimPos[]{c.bowPos,c.crossbowPos,c.explosionPos,c.ghastPos,c.shulkerPos,c.wardenPos})
            pos.refreshCoordinates(width, height, 16);
    }
}
