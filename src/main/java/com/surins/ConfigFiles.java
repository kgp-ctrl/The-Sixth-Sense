package com.surins;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Predicate;

/** Replace only after serialization/validation succeeded; retain the last valid copy. */
final class ConfigFiles {
    private ConfigFiles() { }
    static Path backup(Path path) { return path.resolveSibling(path.getFileName() + ".bak"); }
    static void write(Path path, String json, Predicate<String> valid) throws IOException {
        if (!valid.test(json)) throw new IOException("Invalid settings; existing file retained");
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path staged = Files.createTempFile(path.toAbsolutePath().getParent(), "sixthsense-", ".tmp");
        try {
            Files.writeString(staged, json, StandardCharsets.UTF_8);
            if (Files.isRegularFile(path) && valid.test(Files.readString(path, StandardCharsets.UTF_8)))
                Files.copy(path, backup(path), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(staged, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(staged); }
    }
}
