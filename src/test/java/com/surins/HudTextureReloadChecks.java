package com.surins;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;

/** Source edits must be reread, overrides must be explicit, and invalid PNGs must be rejected. */
final class HudTextureReloadChecks {
    static void run() throws Exception {
        for (var sprite : HudTextureFiles.SPRITES) {
            require(sprite.resourcePath().endsWith(".png"), "Reload includes a non-image resource");
            try (var input = HudTextureReloadChecks.class.getResourceAsStream("/assets/sixthsense/" + sprite.resourcePath())) {
                require(input != null, "Reload manifest points to a missing image");
                HudTextureFiles.validate(sprite, input.readAllBytes());
            }
        }
        Path temporary = Files.createTempDirectory("sixthsense-image-reload-").toAbsolutePath().normalize();
        try {
            Path source = temporary.resolve("src/main/resources/assets/sixthsense");
            Path overrides = temporary.resolve("overrides");
            var sprite = HudTextureFiles.SPRITES.getLast();
            Path png = source.resolve(sprite.resourcePath());
            Files.createDirectories(png.getParent());
            Files.writeString(temporary.resolve("gradle.properties"), "mod_version=test");
            Files.writeString(temporary.resolve("src/main/resources/fabric.mod.json"), "{}");
            require(source.equals(HudTextureFiles.findSourceRoot(List.of(temporary.resolve("build/classes/java/main")))),
                    "Development source lookup failed from compiled classes");
            require(source.equals(HudTextureFiles.findSourceRoot(List.of(temporary.resolve("run")))),
                    "Development source lookup failed from the run folder");
            byte[] first = image(64, 128, 0xFF222222);
            byte[] second = image(64, 128, 0xFF772222);
            byte[] packed = image(64, 128, 0xFF112233);
            HudTextureFiles.ResourceOpener fallback = path -> new ByteArrayInputStream(packed);
            Files.write(png, first);
            require(Arrays.equals(first, HudTextureFiles.read(sprite, source, overrides, fallback)), "Source PNG was ignored");
            Files.write(png, second);
            require(Arrays.equals(second, HudTextureFiles.read(sprite, source, overrides, fallback)), "Saved source edit was cached instead of reread");
            Path override = overrides.resolve(sprite.resourcePath());
            Files.createDirectories(override.getParent());
            Files.write(override, first);
            require(Arrays.equals(first, HudTextureFiles.read(sprite, source, overrides, fallback)), "Explicit override was ignored");
            Files.write(override, image(32, 32, 0xFF000000));
            try {
                HudTextureFiles.read(sprite, source, overrides, fallback);
                throw new AssertionError("Wrong-size override was accepted or silently skipped");
            } catch (IOException expected) { }
            require(Arrays.equals(second, Files.readAllBytes(png)), "Reload changed an authored source PNG");
            Files.write(override, new byte[]{1, 2, 3});
            try {
                HudTextureFiles.read(sprite, source, overrides, fallback);
                throw new AssertionError("Incomplete PNG was accepted");
            } catch (IOException expected) { }
            Files.delete(override);
            Files.delete(png);
            require(Arrays.equals(packed, HudTextureFiles.read(sprite, source, overrides, fallback)), "Resource-pack fallback failed");
            require(Arrays.equals(packed, HudTextureFiles.read(sprite, null, overrides, fallback)), "Packaged-mod reload requires source files");
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(temporary)) throw new IOException("Cleanup escaped its test folder");
                    Files.delete(path);
                }
            }
        }
        System.out.println("Image reload checks passed: live source edits, overrides, PNG dimensions, partial writes and resource fallback.");
    }

    private static byte[] image(int width, int height, int color) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return output.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
