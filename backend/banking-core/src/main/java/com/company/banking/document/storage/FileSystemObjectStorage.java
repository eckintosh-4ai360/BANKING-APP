package com.company.banking.document.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Stores objects as files under a base directory. Writes go to a temporary file first and are moved into place
 * atomically, so a crash never leaves a half-written object behind.
 */
public class FileSystemObjectStorage implements ObjectStorage {

    private static final Pattern SAFE_KEY = Pattern.compile("^[a-zA-Z0-9/_-]{1,300}$");

    private final Path baseDirectory;

    public FileSystemObjectStorage(Path baseDirectory) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDirectory);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create document storage directory", ex);
        }
    }

    @Override
    public void put(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store object", ex);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read object", ex);
        }
    }

    private Path resolve(String key) {
        if (!SAFE_KEY.matcher(key).matches() || key.contains("..")) {
            throw new IllegalArgumentException("Illegal storage key");
        }
        Path path = baseDirectory.resolve(key).normalize();
        if (!path.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("Illegal storage key");
        }
        return path;
    }
}
