package com.videocloud.api;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workspace-scoped playback analytics for the local MVP.
 * Events are emitted by authenticated creator playback. Public audience tracking
 * can be added after share-token ingestion and abuse controls are hardened.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class PlaybackAnalyticsController {
    private final JdbcTemplate jdbc;
    private final CurrentWorkspace workspace;

    public PlaybackAnalyticsController(JdbcTemplate jdbc, CurrentWorkspace workspace) {
        this.jdbc = jdbc;
        this.workspace = workspace;
    }

    public record PlaybackEventRequest(String eventType, Integer watchedSeconds, String viewerKey) {}

    @PostMapping("/videos/{videoId}/events")
    public Map<String, Object> recordEvent(@PathVariable UUID videoId,
                                           @RequestBody PlaybackEventRequest request,
                                           Authentication authentication) {
        UUID workspaceId = workspace.id(authentication);
        UUID userId = workspace.userId(authentication);
        Integer owned = jdbc.query(
            "SELECT 1 FROM videos WHERE id=? AND workspace_id=? AND deleted_at IS NULL",
            rs -> rs.next() ? 1 : 0, videoId, workspaceId);
        if (owned == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");

        String eventType = request.eventType() == null ? "" : request.eventType().trim().toUpperCase();
        if (!List.of("PLAY", "PAUSE", "COMPLETE", "ERROR").contains(eventType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported playback event");
        }
        int watchedSeconds = request.watchedSeconds() == null ? 0 : request.watchedSeconds();
        if (watchedSeconds < 0 || watchedSeconds > 24 * 60 * 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid watchedSeconds");
        }
        String viewerKey = request.viewerKey() == null ? "" : request.viewerKey().trim();
        if (viewerKey.length() < 16 || viewerKey.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid viewer key");
        }

        jdbc.update(
            "INSERT INTO playback_events(video_id,workspace_id,event_type,watched_seconds,viewer_fingerprint_hash) VALUES (?,?,?,?,?)",
            videoId, workspaceId, eventType, watchedSeconds, sha256(workspaceId + ":" + viewerKey));
        return Map.of("accepted", true, "videoId", videoId.toString(), "eventType", eventType);
    }

    @GetMapping
    public Map<String, Object> analytics(Authentication authentication) {
        UUID workspaceId = workspace.id(authentication);
        Map<String, Object> summary = jdbc.queryForMap(
            "SELECT count(*) FILTER (WHERE event_type='PLAY') AS plays, " +
            "count(*) FILTER (WHERE event_type='COMPLETE') AS completions, " +
            "count(DISTINCT viewer_fingerprint_hash) FILTER (WHERE event_type='PLAY') AS unique_viewers, " +
            "COALESCE(sum(watched_seconds),0) AS watch_seconds " +
            "FROM playback_events WHERE workspace_id=? AND occurred_at >= now() - interval '30 days'",
            workspaceId);

        List<Map<String, Object>> daily = jdbc.queryForList(
            "SELECT date_trunc('day', occurred_at)::date AS day, " +
            "count(*) FILTER (WHERE event_type='PLAY') AS plays, " +
            "count(DISTINCT viewer_fingerprint_hash) FILTER (WHERE event_type='PLAY') AS unique_viewers, " +
            "COALESCE(sum(watched_seconds),0) AS watch_seconds " +
            "FROM playback_events WHERE workspace_id=? AND occurred_at >= now() - interval '30 days' " +
            "GROUP BY date_trunc('day', occurred_at)::date ORDER BY day",
            workspaceId);

        List<Map<String, Object>> perVideo = jdbc.queryForList(
            "SELECT v.id, v.title, count(*) FILTER (WHERE e.event_type='PLAY') AS plays, " +
            "count(DISTINCT e.viewer_fingerprint_hash) FILTER (WHERE e.event_type='PLAY') AS unique_viewers, " +
            "COALESCE(sum(e.watched_seconds),0) AS watch_seconds " +
            "FROM videos v LEFT JOIN playback_events e ON e.video_id=v.id AND e.workspace_id=v.workspace_id " +
            "AND e.occurred_at >= now() - interval '30 days' " +
            "WHERE v.workspace_id=? AND v.deleted_at IS NULL " +
            "GROUP BY v.id,v.title ORDER BY plays DESC,v.title",
            workspaceId);

        return Map.of("periodDays", 30, "summary", summary, "daily", daily, "videos", perVideo,
            "note", "Analytics include authenticated creator playback events recorded by this local MVP; shared/public audience analytics are not yet collected.");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
