package com.videocloud.video;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class VideoProcessingService {
    private static final Duration THUMBNAIL_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration HLS_TIMEOUT = Duration.ofHours(2);
    private final JdbcTemplate jdbc;
    private final String ffmpeg;
    private final MediaStorage storage;
    private final MediaProcessRunner processRunner;

    public VideoProcessingService(JdbcTemplate jdbc,
                                  @Value("${videocloud.ffmpeg-path:ffmpeg}") String ffmpeg,
                                  MediaStorage storage) {
        this.jdbc = jdbc;
        this.ffmpeg = ffmpeg;
        this.storage = storage;
        this.processRunner = new MediaProcessRunner();
    }

    @Async
    public void process(UUID id, Path source, Path dir) {
        try {
            Path normalizedRoot = storage.root();
            Path normalizedDir = dir.toAbsolutePath().normalize();
            Path normalizedSource = source.toAbsolutePath().normalize();
            if (!normalizedDir.startsWith(normalizedRoot) || !normalizedSource.startsWith(normalizedDir)
                    || !Files.isRegularFile(normalizedSource)) {
                throw new IOException("Source or output directory is outside the configured media root");
            }
            Files.createDirectories(normalizedDir);
            removeGeneratedOutputs(normalizedDir);

            int thumbnailExit = processRunner.run(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-ss", "00:00:01", "-i", normalizedSource.toString(), "-frames:v", "1",
                normalizedDir.resolve("thumbnail.jpg").toString()), THUMBNAIL_TIMEOUT);
            if (thumbnailExit != 0) throw new IOException("Thumbnail generation failed");

            int hlsExit = processRunner.run(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-i", normalizedSource.toString(), "-vf", "scale=w='min(1280,iw)':h=-2",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "25",
                "-c:a", "aac", "-b:a", "128k", "-hls_time", "6", "-hls_playlist_type", "vod",
                "-hls_segment_filename", normalizedDir.resolve("segment-%05d.ts").toString(),
                normalizedDir.resolve("index.m3u8").toString()), HLS_TIMEOUT);
            if (hlsExit != 0) throw new IOException("HLS transcoding failed");

            validatePlaybackOutputs(normalizedDir);
            registerGeneratedAssets(id, normalizedSource, normalizedDir);
            jdbc.update("UPDATE videos SET status='READY',updated_at=now() WHERE id=? AND deleted_at IS NULL", id);
        } catch (Exception e) {
            try {
                removeGeneratedOutputs(dir.toAbsolutePath().normalize());
                jdbc.update("DELETE FROM video_assets WHERE video_id=? AND asset_type IN ('THUMBNAIL','HLS_MANIFEST','HLS_SEGMENT')", id);
            } catch (Exception ignored) {
                // Preserve FAILED status even if cleanup itself fails; the next retry can clean leftovers.
            }
            jdbc.update("UPDATE videos SET status='FAILED',updated_at=now() WHERE id=? AND deleted_at IS NULL", id);
        }
    }

    private void validatePlaybackOutputs(Path dir) throws IOException {
        Path thumbnail = dir.resolve("thumbnail.jpg");
        Path manifest = dir.resolve("index.m3u8");
        if (!Files.isRegularFile(thumbnail) || Files.size(thumbnail) == 0) {
            throw new IOException("Thumbnail output is missing or empty");
        }
        if (!Files.isRegularFile(manifest) || Files.size(manifest) == 0) {
            throw new IOException("HLS manifest is missing or empty");
        }
        String playlist = Files.readString(manifest);
        if (!playlist.startsWith("#EXTM3U") || !playlist.contains("#EXTINF:")) {
            throw new IOException("HLS manifest is not a valid VOD playlist");
        }
        boolean hasSegment = false;
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                if (file.getFileName().toString().matches("segment-[0-9]{5}\\.ts") && Files.size(file) > 0) {
                    hasSegment = true;
                    break;
                }
            }
        }
        if (!hasSegment) throw new IOException("HLS output contains no non-empty media segments");
    }

    private void removeGeneratedOutputs(Path dir) throws IOException {
        if (!dir.toAbsolutePath().normalize().startsWith(storage.root())) {
            throw new IOException("Refusing to clean files outside the configured media root");
        }
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path file : paths.toList()) {
                String name = file.getFileName().toString();
                if (name.equals("thumbnail.jpg") || name.equals("index.m3u8")
                        || name.matches("segment-[0-9]{5}\\.ts")) {
                    Files.deleteIfExists(file);
                }
            }
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
