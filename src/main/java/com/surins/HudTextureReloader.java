package com.surins;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Decode in the background, stage on the render thread, then replace only HUD textures. */
final class HudTextureReloader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static CompletableFuture<Result> pending;

    record Result(boolean success, String failedFile) {}
    private record Prepared(Map<Identifier, NativeImage> images, String failedFile) {}

    static CompletableFuture<Result> reload(MinecraftClient client) {
        if (pending != null && !pending.isDone()) return pending;
        FabricLoader loader = FabricLoader.getInstance();
        Path source = null;
        if (loader.isDevelopmentEnvironment()) {
            var roots = new ArrayList<Path>();
            loader.getModContainer("sixthsense").ifPresent(mod -> roots.addAll(mod.getRootPaths()));
            roots.add(loader.getGameDir());
            source = HudTextureFiles.findSourceRoot(roots);
        }
        Path sourceRoot = source;
        Path overrides = loader.getConfigDir().resolve("sixthsense/visuals");
        ResourceManager resources = client.getResourceManager();
        pending = CompletableFuture.supplyAsync(() -> prepare(resources, sourceRoot, overrides))
                .thenApplyAsync(prepared -> install(client, resources, prepared), client);
        return pending;
    }

    private static Prepared prepare(ResourceManager resources, Path source, Path overrides) {
        Map<Identifier, NativeImage> images = new LinkedHashMap<>();
        String file = "";
        try {
            for (var sprite : HudTextureFiles.SPRITES) {
                file = sprite.name() + ".png";
                Identifier id = Identifier.of("sixthsense", sprite.resourcePath());
                byte[] bytes = HudTextureFiles.read(sprite, source, overrides,
                        path -> resources.getResourceOrThrow(Identifier.of("sixthsense", path)).getInputStream());
                images.put(id, NativeImage.read(new ByteArrayInputStream(bytes)));
            }
            return new Prepared(images, "");
        } catch (Exception error) {
            images.values().forEach(NativeImage::close);
            LOGGER.warn("HUD image reload rejected {}. Existing textures retained.", file, error);
            return new Prepared(Map.of(), file);
        }
    }

    private static Result install(MinecraftClient client, ResourceManager resources, Prepared prepared) {
        if (!prepared.failedFile().isEmpty()) return new Result(false, prepared.failedFile());
        Map<Identifier, PreparedTexture> textures = new LinkedHashMap<>();
        boolean installed = false;
        String file = "";
        try {
            for (var entry : prepared.images().entrySet()) {
                file = entry.getKey().getPath();
                var texture = new PreparedTexture(entry.getKey(), entry.getValue());
                textures.put(entry.getKey(), texture);
                texture.stage(resources);
            }
            // All images are decoded and uploaded before any visible texture is replaced.
            textures.forEach(client.getTextureManager()::registerTexture);
            installed = true;
            return new Result(true, "");
        } catch (Exception error) {
            LOGGER.warn("Could not install HUD images: {}", file, error);
            return new Result(false, file);
        } finally {
            prepared.images().values().forEach(NativeImage::close);
            if (!installed) textures.values().forEach(texture -> { texture.close(); texture.clearGlId(); });
        }
    }

    /** Later ordinary resource-pack reloads retain vanilla ResourceTexture behavior. */
    private static final class PreparedTexture extends ResourceTexture {
        private NativeImage image;
        private boolean staged;

        PreparedTexture(Identifier id, NativeImage image) { super(id); this.image = image; }

        void stage(ResourceManager resources) throws java.io.IOException {
            super.load(resources);
            staged = true;
        }

        @Override
        public void load(ResourceManager resources) throws java.io.IOException {
            if (staged) { staged = false; return; }
            super.load(resources);
        }

        @Override
        protected TextureData loadTextureData(ResourceManager resources) {
            if (image == null) return super.loadTextureData(resources);
            NativeImage ready = image;
            image = null;
            return new TextureData(null, ready);
        }
    }
}
