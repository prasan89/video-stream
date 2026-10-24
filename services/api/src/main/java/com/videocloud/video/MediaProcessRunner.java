package com.videocloud.video;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs external media tools without allowing unread output pipes to stall processing. */
final class MediaProcessRunner {
    int run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("Command must not be empty");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        Process process = new ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            throw new IOException("Media process timed out");
        }
        return process.exitValue();
    }
}
