package com.videocloud.video;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Storage boundary for media objects. Object keys are relative to the configured
 * storage root; callers must never resolve user-supplied paths directly.
 */
public interface MediaStorage {
    Path root();
    Path resolveKey(String objectKey);
    String keyFor(Path file);
    boolean isRegularFile(Path file);
    long size(Path file) throws IOException;
    void createDirectories(Path directory) throws IOException;
    void copyVerified(Path source, Path destination) throws IOException;
}
