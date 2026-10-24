package com.videocloud.video;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class VideoProcessingService {
    private final JdbcTemplate jdbc;
    private final String ffmpeg;
    private final MediaStorage storage;

    public VideoProcessingService(JdbcTemplate jdbc,
                                  @Value("${videocloud.ffmpeg-path:ffmpeg}") String ffmpeg,
                                  MediaStorage storage) {
        this.jdbc = jdbc;
        this.ffmpeg = ffmpeg;
        this.storage = storage;
    }

    @Async
    public void process(UUID id, Path source, Path dir) {
        try {
            Files.createDirectories(dir);
            Process thumb = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-ss", "00:00:01", "-i", source.toString(), "-frames:v", "1",
                dir.resolve("thumbnail.jpg").toString()).redirectErrorStream(true).start();
            if (!thumb.waitFor(3, TimeUnit.MINUTES) || thumb.exitValue() != 0)
                throw new IllegalStateException("thumbnail failed");

            Process hls = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-i", source.toString(), "-vf", "scale=w='min(1280,iw)':h=-2",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "25",
                "-c:a", "aac", "-b:a", "128k", "-hls_time", "6", "-hls_playlist_type", "vod",
                "-hls_segment_filename", dir.resolve("segment-%05d.ts").toString(),
                dir.resolve("index.m3u8").toString()).redirectErrorStream(true).start();
            if (!hls.waitFor(2, TimeUnit.HOURS) || hls.exitValue() != 0)
                throw new IllegalStateException("HLS transcode failed");

            registerGeneratedAssets(id, source, dir);
            jdbc.update("UPDATE videos SET status='READY',updated_at=now() WHERE id=? AND deleted_at IS NULL", id);
        } catch (Exception e) {
            jdbc.update("UPDATE videos SET status='FAILED',updated_at=now() WHERE id=? AND deleted_at IS NULL", id);
        }
    }

    /**
     * Register generated files after successful processing so storage quota calculations
     * include thumbnails and every HLS output, not just the original upload.
     */
    private void registerGeneratedAssets(UUID videoId, Path source, Path dir) throws IOException {
        Path normalizedDir = dir.toAbsolutePath().normalize();
        if (!normalizedDir.startsWith(storage.root()) || !source.toAbsolutePath().normalize().startsWith(normalizedDir))
            throw new IllegalStateException("Generated assets are outside the configured media root");

        try (Stream<Path> paths = Files.walk(normalizedDir)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                if (file.equals(source.toAbsolutePath().normalize())) continue;
                String name = file.getFileName().toString();
                String type;
                String mime;
                if ("thumbnail.jpg".equals(name)) {
                    type = "THUMBNAIL";
                    mime = "image/jpeg";
                } else if ("index.m3u8".equals(name)) {
                    type = "HLS_MANIFEST";
                    mime = "application/vnd.apple.mpegurl";
                } else if (name.matches("segment-[0-9]{5}\\.ts")) {
                    type = "HLS_SEGMENT";
                    mime = "video/mp2t";
                } else {
                    continue;
                }
                String key = storage.keyFor(file);
                jdbc.update("INSERT INTO video_assets(video_id,asset_type,object_key,mime_type,size_bytes) " +
                    "VALUES (?,?,?,?,?) ON CONFLICT (video_id,asset_type,object_key) DO UPDATE " +
                    "SET mime_type=EXCLUDED.mime_type,size_bytes=EXCLUDED.size_bytes",
                    videoId, type, key, mime, storage.size(file));
            }
        }
    }
}
