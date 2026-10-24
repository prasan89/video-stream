package com.videocloud.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalMediaStorageTest {
    @TempDir Path temp;

    @Test
    void resolvesOnlyKeysInsideStorageRoot() throws Exception {
        LocalMediaStorage storage = new LocalMediaStorage(temp.resolve("media").toString());
        Path object = storage.resolveKey("workspaces/a/videos/b/source.mp4");
        assertTrue(object.startsWith(storage.root()));
        assertEquals("workspaces/a/videos/b/source.mp4", storage.keyFor(object));
        assertThrows(IllegalArgumentException.class, () -> storage.resolveKey("../../outside.txt"));
        assertThrows(IllegalArgumentException.class, () -> storage.keyFor(temp.resolve("outside.txt")));
    }

    @Test
    void copyVerifiedCopiesBytesAndCanBeRetried() throws Exception {
        LocalMediaStorage storage = new LocalMediaStorage(temp.resolve("media").toString());
        Path source = storage.resolveKey("legacy/source.mp4");
        Path destination = storage.resolveKey("workspaces/ws/videos/video/source.mp4");
        storage.createDirectories(source.getParent());
        Files.write(source, new byte[] {1, 2, 3, 4, 5});

        storage.copyVerified(source, destination);
        storage.copyVerified(source, destination);

        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(destination));
        assertEquals(5, storage.size(destination));
        assertTrue(storage.isRegularFile(destination));
        assertTrue(Files.exists(source), "Migration must retain the source until an operator verifies the result");
    }
}
