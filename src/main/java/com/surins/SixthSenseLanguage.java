package com.surins;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** Uses Minecraft's selected language and its built-in en_us fallback/resource reload. */
public final class SixthSenseLanguage {
    private SixthSenseLanguage() {}

    public static MutableText text(String key, Object... arguments) {
        return Text.translatable("sixthsense." + key, arguments);
    }

    /** Arabic shaping can change glyph advances after vanilla's initial line split. */
    static java.util.List<net.minecraft.text.OrderedText> wrap(net.minecraft.client.font.TextRenderer renderer,
            Text text, int maxWidth) {
        int width = Math.max(1, maxWidth);
        var lines = renderer.wrapLines(text, width);
        if (!net.minecraft.util.Language.getInstance().isRightToLeft()) return lines;
        for (int attempt = 0; attempt < 4; attempt++) {
            int measured = lines.stream().mapToInt(renderer::getWidth).max().orElse(0);
            if (measured <= maxWidth || width == 1) break;
            width = Math.max(1, width - (measured - maxWidth) - 1);
            lines = renderer.wrapLines(text, width);
        }
        return lines;
    }

    public static String string(String key, Object... arguments) {
        return text(key, arguments).getString();
    }
}
