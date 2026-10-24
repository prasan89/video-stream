package com.videocloud.video;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Rejects obvious MIME/extension spoofing before an uploaded file is queued for
 * transcoding. This is a lightweight container-signature check, not a malware
 * scanner or a substitute for FFmpeg's full media parser.
 */
public final class VideoFileValidator {
    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
        "mp4", "video/mp4",
        "mov", "video/quicktime",
        "webm", "video/webm",
        "mkv", "video/x-matroska"
    );
    private VideoFileValidator() {}

    public static void validate(Path file, String extension, String contentType) throws IOException {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String expectedMime = MIME_BY_EXTENSION.get(ext);
        if (expectedMime == null || !expectedMime.equalsIgnoreCase(contentType == null ? "" : contentType.trim())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "File extension and content type do not match a supported video format");
        }
        if (!Files.isRegularFile(file) || Files.size(file) < 12) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Uploaded file is too small or is not a regular file");
        }
        byte[] header = new byte[12];
        try (InputStream input = Files.newInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int read = input.read(header, offset, header.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset < header.length || !hasExpectedSignature(ext, header)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "File contents do not match the declared video format");
            }
        }
    }

    private static boolean hasExpectedSignature(String extension, byte[] header) {
        if (extension.equals("mp4") || extension.equals("mov")) {
            return header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
        }
        // WebM and Matroska both use the EBML container header. FFmpeg performs
        // deeper parsing during processing; extension/MIME still distinguish the declared format.
        return (extension.equals("webm") || extension.equals("mkv"))
            && (header[0] & 0xff) == 0x1a
            && (header[1] & 0xff) == 0x45
            && (header[2] & 0xff) == 0xdf
            && (header[3] & 0xff) == 0xa3;
    }
}
