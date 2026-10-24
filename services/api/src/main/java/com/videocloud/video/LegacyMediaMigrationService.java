package com.videocloud.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-time, copy-and-verify migration for legacy video objects. Dry-run mode
 * never writes files or database rows. The source objects are retained even
 * after a successful migration so operators can verify before cleanup.
 */
@Service
public class LegacyMediaMigrationService {
    private static final Logger log = LoggerFactory.getLogger(LegacyMediaMigrationService.class);
    private final JdbcTemplate jdbc;
    private final MediaStorage storage;

    public LegacyMediaMigrationService(JdbcTemplate jdbc, MediaStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    public record MigrationReport(boolean dryRun, int videosScanned, int videosAlreadyTenantScoped,
                                  int objectsPlanned, int objectsCopied, int failures, List<String> details) {}

    @Transactional
    public MigrationReport migrate(boolean dryRun) {
        List<Map<String, Object>> videos = jdbc.queryForList(
            "SELECT v.id,v.workspace_id,v.source_object_key FROM videos v " +
            "WHERE v.deleted_at IS NULL AND v.source_object_key IS NOT NULL ORDER BY v.created_at,v.id");
        int alreadyScoped = 0, planned = 0, copied = 0, failures = 0;
        List<String> details = new ArrayList<>();

        for (Map<String, Object> video : videos) {
            UUID videoId = (UUID) video.get("id");
            UUID workspaceId = (UUID) video.get("workspace_id");
            String sourceKey = (String) video.get("source_object_key");
            List<Map<String, Object>> activeUploads = jdbc.queryForList(
                "SELECT 1 FROM upload_sessions WHERE video_id=? AND status IN ('CREATED','UPLOADING') AND expires_at>now()",
                videoId);
            if (!activeUploads.isEmpty()) {
                details.add("SKIP active upload session video=" + videoId);
                continue;
            }

            String prefix = "workspaces/" + workspaceId + "/videos/" + videoId + "/";
            if (sourceKey.replace('\\', '/').startsWith(prefix)) {
                alreadyScoped++;
                continue;
            }

            List<ObjectRef> objects = new ArrayList<>();
            objects.add(new ObjectRef("video", sourceKey, null));
            List<Map<String, Object>> assets = jdbc.queryForList(
                "SELECT id,object_key FROM video_assets WHERE video_id=? ORDER BY id", videoId);
            for (Map<String, Object> asset : assets) {
                objects.add(new ObjectRef("asset", (String) asset.get("object_key"), (UUID) asset.get("id")));
            }

            Path sourceDirectory;
            try {
                sourceDirectory = storage.resolveKey(sourceKey).getParent();
            } catch (RuntimeException e) {
                failures++;
                details.add("FAIL invalid source key video=" + videoId);
                continue;
            }

            boolean videoFailed = false;
            List<PlannedObject> plan = new ArrayList<>();
            for (ObjectRef object : objects) {
                try {
                    Path source = storage.resolveKey(object.key());
                    if (!storage.isRegularFile(source)) {
                        details.add("FAIL missing " + object.kind() + " video=" + videoId + " key=" + object.key());
                        videoFailed = true;
                        break;
                    }
                    String filename = source.getFileName().toString();
                    Path destination = storage.root().resolve(prefix).resolve(filename).normalize();
                    if (!destination.startsWith(storage.root().resolve(prefix).normalize())) {
                        details.add("FAIL unsafe destination video=" + videoId);
                        videoFailed = true;
                        break;
                    }
                    plan.add(new PlannedObject(object, source, destination));
                } catch (RuntimeException e) {
                    details.add("FAIL invalid object key video=" + videoId);
                    videoFailed = true;
                    break;
                }
            }
            if (videoFailed) {
                failures++;
                continue;
            }

            planned += plan.size();
            if (dryRun) {
                details.add("DRY-RUN video=" + videoId + " objects=" + plan.size());
                continue;
            }

            try {
                for (PlannedObject object : plan) {
                    storage.copyVerified(object.source(), object.destination());
                    if (!Files.isRegularFile(object.destination())) throw new IOException("Copied object is missing");
                    if (object.ref().kind().equals("video")) {
                        jdbc.update("UPDATE videos SET source_object_key=?,updated_at=now() WHERE id=?",
                            storage.keyFor(object.destination()), videoId);
                    } else {
                        jdbc.update("UPDATE video_assets SET object_key=? WHERE id=? AND video_id=?",
                            storage.keyFor(object.destination()), object.ref().assetId(), videoId);
                    }
                    copied++;
                }
                details.add("MIGRATED video=" + videoId + " objects=" + plan.size());
            } catch (Exception e) {
                failures++;
                details.add("FAIL copy/update video=" + videoId + " reason=" + e.getClass().getSimpleName());
                // Any already-copied objects are safe duplicates; source files remain intact.
                throw new IllegalStateException("Legacy media migration failed; source files were retained for recovery", e);
            }
        }

        MigrationReport report = new MigrationReport(dryRun, videos.size(), alreadyScoped, planned, copied, failures,
            List.copyOf(details));
        log.info("Legacy media migration report: {}", report);
        return report;
    }

    private record ObjectRef(String kind, String key, UUID assetId) {}
    private record PlannedObject(ObjectRef ref, Path source, Path destination) {}
}
