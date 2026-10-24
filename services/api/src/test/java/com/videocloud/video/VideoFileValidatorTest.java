package com.videocloud.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VideoFileValidatorTest {
    @TempDir Path temp;

    @Test void acceptsMp4ContainerWithMatchingMimeAndExtension() throws IOException {
        Path file = temp.resolve("sample.mp4");
        Files.write(file, new byte[] {0,0,0,16,'f','t','y','p','i','s','o','m',0,0,0,0});
        assertDoesNotThrow(() -> VideoFileValidator.validate(file, "mp4", "video/mp4"));
    }

    @Test void acceptsWebmEbmlSignature() throws IOException {
        Path file = temp.resolve("sample.webm");
        Files.write(file, new byte[] {(byte)0x1a,0x45,(byte)0xdf,(byte)0xa3,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
        assertDoesNotThrow(() -> VideoFileValidator.validate(file, "webm", "video/webm"));
    }

    @Test void rejectsExtensionAndMimeMismatch() throws IOException {
        Path file = temp.resolve("sample.mp4");
        Files.write(file, new byte[] {0,0,0,16,'f','t','y','p','i','s','o','m',0,0,0,0});
        assertThrows(ResponseStatusException.class,
            () -> VideoFileValidator.validate(file, "mp4", "image/png"));
    }

    @Test void rejectsSpoofedContainerSignature() throws IOException {
        Path file = temp.resolve("fake.mp4");
        Files.write(file, new byte[] {0,0,0,16,'n','o','t','v','i','d','e','o',0,0,0,0});
        assertThrows(ResponseStatusException.class,
            () -> VideoFileValidator.validate(file, "mp4", "video/mp4"));
    }

    @Test void rejectsTooSmallFiles() throws IOException {
        Path file = temp.resolve("tiny.mp4");
        Files.write(file, new byte[] {0,0,0,16,'f','t','y','p'});
        assertThrows(ResponseStatusException.class,
            () -> VideoFileValidator.validate(file, "mp4", "video/mp4"));
    }
}
