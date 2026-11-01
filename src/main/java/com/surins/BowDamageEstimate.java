package com.surins;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.enchantment.Enchantment;

/** Conservative direct-hit estimate from synchronized data; never mutates health or creates arrows. */
final class BowDamageEstimate {
    private BowDamageEstimate() { }
    static float damage(PlayerEntity shooter, LivingEntity target, double impactSpeed) {
        int power = level(shooter, shooter.getActiveItem(), Enchantments.POWER);
        int protection = 0;
        for (ItemStack armor : target.getArmorItems()) {
            protection += level(target, armor, Enchantments.PROTECTION)
                    + 2 * level(target, armor, Enchantments.PROJECTILE_PROTECTION);
        }
        var resistance = target.getStatusEffect(StatusEffects.RESISTANCE);
        // 1.21.1 Power adds 0.5 damage per level before the impact-speed multiplier.
        return damage(impactSpeed, power, target.getArmor(),
                target.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS), protection,
                resistance == null ? 0 : resistance.getAmplifier() + 1);
    }
    private static int level(LivingEntity entity, ItemStack stack, RegistryKey<Enchantment> key) {
        return entity.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(key)
                .map(entry -> EnchantmentHelper.getLevel(entry, stack)).orElse(0);
    }
    static float damage(double speed, int power, float armor, double toughness, int protection, int resistance) {
        double raw = Math.ceil(Math.max(0, speed) * (2 + .5 * Math.max(0,power)));
        double armorReduction = Math.min(20, Math.max(armor * .2, armor - raw / (2 + toughness / 4))) / 25;
        return (float)(raw * (1 - armorReduction) * (1 - Math.min(20, Math.max(0,protection)) / 25.0)
                * Math.max(0, 1 - .2 * resistance));
    }
    static int color(float health, float damage) {
        // One conservative hit = red; two hits = red-orange, four or more = orange.
        double shots = damage <= 0 ? 4 : Math.max(1, Math.ceil(health / damage));
        int green = (int)Math.round(40 + 105 * Math.min(1, (shots - 1) / 3));
        return 0xFF0000 | green << 8 | 0x18;
    }
}
