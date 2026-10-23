package com.videocloud.video;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Removes expired upload files only within their owning workspace directory. */
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
        List<Map<String,Object>> expired = jdbc.queryForList(
            "SELECT us.id, v.workspace_id FROM upload_sessions us JOIN videos v ON v.id=us.video_id " +
            "WHERE us.status IN ('CREATED','UPLOADING') AND us.expires_at <= now()");

        for (Map<String,Object> row : expired) {
            UUID id = (UUID) row.get("id");
            UUID workspaceId = (UUID) row.get("workspace_id");
            int updated = jdbc.update(
                "UPDATE upload_sessions SET status='EXPIRED',updated_at=now() " +
                "WHERE id=? AND status IN ('CREATED','UPLOADING') AND expires_at <= now()", id);
            if (updated == 0) continue;

            jdbc.update(
                "UPDATE videos SET status='DELETED',deleted_at=COALESCE(deleted_at,now()),updated_at=now() " +
                "WHERE id=(SELECT video_id FROM upload_sessions WHERE id=?) " +
                "AND status='UPLOADING' AND deleted_at IS NULL", id);
            deleteTree(root.resolve("workspaces").resolve(workspaceId.toString())
                .resolve("uploads").resolve(id.toString()).normalize());
        }
    }

    private void deleteTree(Path directory) {
        Path workspacesRoot = root.resolve("workspaces").normalize();
        if (!directory.startsWith(workspacesRoot) || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
            // A later scheduled run retries cleanup.
        }
    }
}
