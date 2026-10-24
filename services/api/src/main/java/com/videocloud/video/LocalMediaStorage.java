package com.videocloud.video;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Local-disk implementation of the media storage contract. */
@Component
public class LocalMediaStorage implements MediaStorage {
    private final Path root;

    public LocalMediaStorage(@Value("${videocloud.media-root:./var/media}") String mediaRoot) {
        this.root = Path.of(mediaRoot).toAbsolutePath().normalize();
    }

    @Override public Path root() { return root; }

    @Override
    public Path resolveKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("Object key is required");
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root))
            throw new IllegalArgumentException("Object key escapes storage root");
        return resolved;
    }

    @Override
    public String keyFor(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root))
            throw new IllegalArgumentException("File is outside storage root");
        return root.relativize(normalized).toString();
    }

    @Override public boolean isRegularFile(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        return normalized.startsWith(root) && Files.isRegularFile(normalized);
    }

    @Override public long size(Path file) throws IOException {
        if (!isRegularFile(file)) throw new IOException("Storage object is missing or outside storage root");
        return Files.size(file);
    }

    @Override public void createDirectories(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new IOException("Directory is outside storage root");
        Files.createDirectories(normalized);
    }

    /**
     * Copies without deleting the source. A temporary destination and SHA-256
     * verification make the operation safe to retry during legacy migration.
     */
    @Override
    public void copyVerified(Path source, Path destination) throws IOException {
        Path src = source.toAbsolutePath().normalize();
        Path dst = destination.toAbsolutePath().normalize();
        if (!src.startsWith(root) || !dst.startsWith(root) || !Files.isRegularFile(src))
            throw new IOException("Source or destination is outside storage root, or source is missing");
        if (src.equals(dst)) return;
        Files.createDirectories(dst.getParent());
        Path temp = dst.resolveSibling(dst.getFileName() + ".migration-tmp");
        Files.deleteIfExists(temp);
        try {
            Files.copy(src, temp, StandardCopyOption.REPLACE_EXISTING);
            if (!sha256(src).equals(sha256(temp))) throw new IOException("SHA-256 verification failed for copied media");
            try {
                Files.move(temp, dst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
