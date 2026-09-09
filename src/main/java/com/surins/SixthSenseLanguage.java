package com.surins;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** Uses Minecraft's selected language and its built-in en_us fallback/resource reload. */
public final class SixthSenseLanguage {
    private SixthSenseLanguage() {}

    public static MutableText text(String key, Object... arguments) {
        return Text.translatable("sixthsense." + key, arguments);
    }

    public static String string(String key, Object... arguments) {
        return text(key, arguments).getString();
    }
}
