package com.videocloud.video;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Removes local temporary files for upload sessions that have expired.
 * Only UUID-named directories under the configured resumable root are touched.
 */
@Component
public class ExpiredUploadCleanup {
    private final JdbcTemplate jdbc;
    private final Path root;

    public ExpiredUploadCleanup(JdbcTemplate jdbc,
                                @Value("${videocloud.media-root:./var/media}") String mediaRoot) {
        this.jdbc = jdbc;
        this.root = Path.of(mediaRoot).toAbsolutePath().normalize();
    }

    @Scheduled(fixedDelayString = "${videocloud.upload-cleanup-interval-ms:300000}")
    public void cleanupExpiredUploads() {
        List<UUID> expired = jdbc.query(
            "SELECT id FROM upload_sessions WHERE status IN ('CREATED','UPLOADING') AND expires_at <= now()",
            (rs, row) -> rs.getObject(1, UUID.class));

        for (UUID id : expired) {
            int updated = jdbc.update(
                "UPDATE upload_sessions SET status='EXPIRED',updated_at=now() " +
                "WHERE id=? AND status IN ('CREATED','UPLOADING') AND expires_at <= now()", id);
            if (updated == 0) continue;

            // The associated placeholder video should no longer consume a video slot.
            jdbc.update(
                "UPDATE videos SET status='DELETED',deleted_at=COALESCE(deleted_at,now()),updated_at=now() " +
                "WHERE id=(SELECT video_id FROM upload_sessions WHERE id=?) " +
                "AND status='UPLOADING' AND deleted_at IS NULL", id);
            deleteTree(root.resolve("resumable").resolve(id.toString()).normalize());
        }
    }

    private void deleteTree(Path directory) {
        Path resumableRoot = root.resolve("resumable").normalize();
        if (!directory.startsWith(resumableRoot) || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
            // A later scheduled run can retry cleanup; do not fail application work.
        }
    }
}
