package com.videocloud.video;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Requeues videos left in PROCESSING by an API restart. */
@Component
public class ProcessingRecovery {
    private final JdbcTemplate jdbc;
    private final VideoProcessingService processor;
    private final Path root;

    public ProcessingRecovery(JdbcTemplate jdbc, VideoProcessingService processor,
                              @Value("${videocloud.media-root:./var/media}") String mediaRoot) {
        this.jdbc=jdbc; this.processor=processor; this.root=Path.of(mediaRoot).toAbsolutePath().normalize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        List<Map<String,Object>> rows=jdbc.queryForList(
            "SELECT id,source_object_key FROM videos WHERE status='PROCESSING' AND deleted_at IS NULL");
        for(Map<String,Object> row:rows) {
            UUID id=(UUID)row.get("id");
            Path source=root.resolve((String)row.get("source_object_key")).normalize();
            if(!source.startsWith(root)||!Files.isRegularFile(source)) {
                jdbc.update("UPDATE videos SET status='FAILED',updated_at=now() WHERE id=? AND status='PROCESSING'",id);
                continue;
            }
            processor.process(id,source,source.getParent());
        }
    }
}
