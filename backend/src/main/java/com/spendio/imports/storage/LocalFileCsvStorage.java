package com.spendio.imports.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local-disk {@link CsvStorage}: files live under {@code spendio.imports.storage-dir}. */
@Component
public class LocalFileCsvStorage implements CsvStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileCsvStorage.class);

    private final Path root;

    public LocalFileCsvStorage(
            @Value("${spendio.imports.storage-dir:${java.io.tmpdir}/spendio-imports}") String rootDir) {
        this.root = Path.of(rootDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create import storage dir " + this.root, e);
        }
    }

    @Override
    public StoredFile store(byte[] content) {
        String key = UUID.randomUUID() + ".csv";
        Path target = safePath(key);
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot store import file " + key, e);
        }
        return new StoredFile(key, content.length);
    }

    @Override
    public byte[] read(String key) {
        try {
            return Files.readAllBytes(safePath(key));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read import file " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(safePath(key));
        } catch (IOException e) {
            log.warn("Cannot delete import file {}: {}", key, e.getMessage());
        }
    }

    private Path safePath(String key) {
        Path candidate = root.resolve(key).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Unsafe storage key: " + key);
        }
        return candidate;
    }
}