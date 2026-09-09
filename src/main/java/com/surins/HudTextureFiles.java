package com.surins;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The reload button can read only these PNGs, never classes or other resources. */
final class HudTextureFiles {
    record Sprite(String name, int width, int height) {
        String resourcePath() { return "textures/gui/" + name + ".png"; }
    }

    static final List<Sprite> SPRITES = List.of(
            new Sprite("warning_bow", 32, 256), new Sprite("warning_crossbow", 32, 256),
            new Sprite("warning_boom", 160, 64), new Sprite("warning_ghast", 32, 512),
            new Sprite("warning_spark", 32, 512), new Sprite("warning_warden", 16, 160),
            new Sprite("warning_arrow", 16, 16), new Sprite("warning_warden_thorns", 64, 128));
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    @FunctionalInterface
    interface ResourceOpener { InputStream open(String path) throws IOException; }

    private HudTextureFiles() {}

    static Path findSourceRoot(Iterable<Path> startingPaths) {
        for (Path start : startingPaths) {
            Path folder = start.toAbsolutePath().normalize();
            for (int depth = 0; folder != null && depth < 7; depth++, folder = folder.getParent()) {
                Path resources = folder.resolve("src/main/resources");
                if (Files.isRegularFile(folder.resolve("gradle.properties"))
                        && Files.isRegularFile(resources.resolve("fabric.mod.json"))
                        && Files.isDirectory(resources.resolve("assets/sixthsense/textures/gui"))) {
                    return resources.resolve("assets/sixthsense");
                }
            }
        }
        return null;
    }

    static byte[] read(Sprite sprite, Path sourceRoot, Path overrides, ResourceOpener fallback) throws IOException {
        Path override = overrides.resolve(sprite.resourcePath());
        Path source = sourceRoot == null ? null : sourceRoot.resolve(sprite.resourcePath());
        try (InputStream input = Files.exists(override) ? Files.newInputStream(override)
                : source != null && Files.exists(source) ? Files.newInputStream(source)
                : fallback.open(sprite.resourcePath())) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            validate(sprite, bytes);
            return bytes;
        }
    }

    static void validate(Sprite sprite, byte[] bytes) throws IOException {
        if (bytes.length < 33 || bytes.length > MAX_BYTES) throw new IOException("Incomplete or oversized PNG");
        ByteBuffer header = ByteBuffer.wrap(bytes);
        if (header.getLong(0) != 0x89504e470d0a1a0aL || header.getInt(8) != 13 || header.getInt(12) != 0x49484452) {
            throw new IOException("Not a PNG image");
        }
        if (header.getInt(16) != sprite.width() || header.getInt(20) != sprite.height()) {
            throw new IOException("Expected " + sprite.width() + " x " + sprite.height() + " pixels");
        }
    }
}
