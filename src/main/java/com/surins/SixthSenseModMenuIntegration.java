package com.surins;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.MinecraftClient;

public class SixthSenseModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            MinecraftClient client = MinecraftClient.getInstance();
            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();
            float raidWidth = client.textRenderer.getWidth(SixthSenseLanguage.text("editor.raid_example"))
                    + 24 + client.textRenderer.getWidth("24m");
            SixthSenseConfig.migrateLegacyPositions(width, height, raidWidth);
            SixthSenseConfig.instance.raidPos.refreshCoordinates(width, height, raidWidth);
            for (SixthSenseConfig.AnimPos pos : new SixthSenseConfig.AnimPos[]{
                    SixthSenseConfig.instance.bowPos, SixthSenseConfig.instance.crossbowPos,
                    SixthSenseConfig.instance.explosionPos, SixthSenseConfig.instance.ghastPos,
                    SixthSenseConfig.instance.shulkerPos, SixthSenseConfig.instance.wardenPos}) {
                pos.refreshCoordinates(width, height, 16);
            }
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(SixthSenseLanguage.text("config.title"));

            builder.setSavingRunnable(() -> {
                SixthSenseConfig.save();
            });

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // Main category to hold all sub-categories, effectively creating a vertical list
            ConfigCategory mainCategory = builder.getOrCreateCategory(SixthSenseLanguage.text("config.settings"));

            mainCategory.addEntry(entryBuilder.startTextDescription(SixthSenseLanguage.text("config.editor_help"))
                    .build());

            // Raid Settings Sub-Category
            SubCategoryBuilder raidSubCategory = entryBuilder.startSubCategory(SixthSenseLanguage.text("config.raid"));
            
            raidSubCategory.add(entryBuilder.startBooleanToggle(SixthSenseLanguage.text("config.raid_warning"), SixthSenseConfig.instance.enableRaidWarning)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> SixthSenseConfig.instance.enableRaidWarning = newValue)
                    .build());

            raidSubCategory.add(entryBuilder.startBooleanToggle(SixthSenseLanguage.text("config.raid_glow"), SixthSenseConfig.instance.enableRaidGlow)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> SixthSenseConfig.instance.enableRaidGlow = newValue)
                    .build());

            raidSubCategory.add(entryBuilder.startIntField(SixthSenseLanguage.text("config.x"), SixthSenseConfig.instance.raidPos.x)
                    .setDefaultValue(-1)
                    .setSaveConsumer(newValue -> SixthSenseConfig.instance.raidPos.setX(newValue))
                    .build());

            raidSubCategory.add(entryBuilder.startIntField(SixthSenseLanguage.text("config.y"), SixthSenseConfig.instance.raidPos.y)
                    .setDefaultValue(-1)
                    .setSaveConsumer(newValue -> SixthSenseConfig.instance.raidPos.setY(newValue))
                    .build());

            raidSubCategory.add(entryBuilder.startFloatField(SixthSenseLanguage.text("config.scale"), SixthSenseConfig.instance.raidPos.scale)
                    .setDefaultValue(0.6f)
                    .setMin(0.1f)
                    .setMax(5.0f)
                    .setSaveConsumer(newValue -> SixthSenseConfig.instance.raidPos.scale = newValue)
                    .build());
                    
            mainCategory.addEntry(raidSubCategory.build());

            // Bow Warning Sub-Category
            mainCategory.addEntry(buildWarningSubCategory(entryBuilder, "config.bow", 
                    SixthSenseConfig.instance.enableBowWarning, newValue -> SixthSenseConfig.instance.enableBowWarning = newValue,
                    SixthSenseConfig.instance.bowPos).build());

            // Crossbow Warning Sub-Category
            mainCategory.addEntry(buildWarningSubCategory(entryBuilder, "config.crossbow", 
                    SixthSenseConfig.instance.enableCrossbowWarning, newValue -> SixthSenseConfig.instance.enableCrossbowWarning = newValue,
                    SixthSenseConfig.instance.crossbowPos).build());

            // Explosion Warning Sub-Category
            mainCategory.addEntry(buildWarningSubCategory(entryBuilder, "config.explosion", 
                    SixthSenseConfig.instance.enableExplosionWarning, newValue -> SixthSenseConfig.instance.enableExplosionWarning = newValue,
                    SixthSenseConfig.instance.explosionPos).build());

            // Ghast Warning Sub-Category
            SubCategoryBuilder ghastSubCategory = buildWarningSubCategory(entryBuilder, "config.ghast", 
                    SixthSenseConfig.instance.enableGhastWarning, newValue -> SixthSenseConfig.instance.enableGhastWarning = newValue,
                    SixthSenseConfig.instance.ghastPos);
            ghastSubCategory.add(entryBuilder.startBooleanToggle(SixthSenseLanguage.text("config.ghast_distance"), SixthSenseConfig.instance.showGhastDistance)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> SixthSenseConfig.instance.showGhastDistance = newValue)
                    .build());
            mainCategory.addEntry(ghastSubCategory.build());

            // Shulker Warning Sub-Category
            mainCategory.addEntry(buildWarningSubCategory(entryBuilder, "config.shulker", 
                    SixthSenseConfig.instance.enableShulkerWarning, newValue -> SixthSenseConfig.instance.enableShulkerWarning = newValue,
                    SixthSenseConfig.instance.shulkerPos).build());

            // Warden Warning Sub-Category
            SubCategoryBuilder wardenSubCategory = buildWarningSubCategory(entryBuilder, "config.warden", 
                    SixthSenseConfig.instance.enableWardenWarning, newValue -> SixthSenseConfig.instance.enableWardenWarning = newValue,
                    SixthSenseConfig.instance.wardenPos);
            wardenSubCategory.add(entryBuilder.startBooleanToggle(SixthSenseLanguage.text("config.warden_distance"), SixthSenseConfig.instance.showWardenDistance)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> SixthSenseConfig.instance.showWardenDistance = newValue)
                    .build());
            mainCategory.addEntry(wardenSubCategory.build());

            return builder.build();
        };
    }

    private SubCategoryBuilder buildWarningSubCategory(ConfigEntryBuilder entryBuilder, String name, 
                                                boolean enableCurrent, java.util.function.Consumer<Boolean> enableSaver,
                                                SixthSenseConfig.AnimPos pos) {
        SubCategoryBuilder subCategory = entryBuilder.startSubCategory(SixthSenseLanguage.text(name));

        subCategory.add(entryBuilder.startBooleanToggle(SixthSenseLanguage.text("config.enabled"), enableCurrent)
                .setDefaultValue(true)
                .setSaveConsumer(enableSaver)
                .build());

        subCategory.add(entryBuilder.startIntField(SixthSenseLanguage.text("config.x"), pos.x)
                .setDefaultValue(-1)
                .setSaveConsumer(newValue -> pos.setX(newValue))
                .build());

        subCategory.add(entryBuilder.startIntField(SixthSenseLanguage.text("config.y"), pos.y)
                .setDefaultValue(-1)
                .setSaveConsumer(newValue -> pos.setY(newValue))
                .build());

        subCategory.add(entryBuilder.startFloatField(SixthSenseLanguage.text("config.scale"), pos.scale)
                .setDefaultValue(0.7f)
                .setMin(0.1f)
                .setMax(5.0f)
                .setSaveConsumer(newValue -> pos.scale = newValue)
                .build());

        return subCategory;
    }
}
