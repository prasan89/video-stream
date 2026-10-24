package com.videocloud.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MediaProcessRunnerTest {
    private final MediaProcessRunner runner = new MediaProcessRunner();

    @Test
    void returnsExitCodeForCompletedProcess() throws Exception {
        assertEquals(0, runner.run(List.of("sh", "-c", "exit 0"), Duration.ofSeconds(2)));
        assertEquals(7, runner.run(List.of("sh", "-c", "exit 7"), Duration.ofSeconds(2)));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void terminatesProcessWhenTimeoutExpires() {
        IOException error = assertThrows(IOException.class,
            () -> runner.run(List.of("sh", "-c", "sleep 10"), Duration.ofMillis(100)));
        assertTrue(error.getMessage().contains("timed out"));
    }

    @Test
    void rejectsInvalidTimeoutBeforeStartingProcess() {
        assertThrows(IllegalArgumentException.class,
            () -> runner.run(List.of("sh", "-c", "exit 0"), Duration.ZERO));
    }
}
