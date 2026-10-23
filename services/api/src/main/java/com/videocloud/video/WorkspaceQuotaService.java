package com.videocloud.video;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/** Enforces the plan attached to the workspace. Unsubscribed workspaces use FREE. */
@Service
public class WorkspaceQuotaService {
    private final JdbcTemplate jdbc;
    public WorkspaceQuotaService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Map<String, Object> assertCanStartUpload(UUID workspaceId, long incomingBytes) {
        if (incomingBytes <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload size must be positive");
        // Serializes quota checks for this workspace so simultaneous uploads cannot both pass on stale usage.
        Integer locked = jdbc.query("SELECT 1 FROM workspaces WHERE id=? FOR UPDATE",
            rs -> rs.next() ? 1 : null, workspaceId);
        if (locked == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");

        Map<String, Object> plan = jdbc.queryForMap(
            "SELECT p.code,p.storage_limit_bytes,p.video_limit FROM plans p " +
            "WHERE p.active=true AND p.id=COALESCE((SELECT s.plan_id FROM subscriptions s " +
            "WHERE s.workspace_id=? AND s.status IN ('ACTIVE','TRIALING') " +
            "AND (s.current_period_end IS NULL OR s.current_period_end>now()) " +
            "ORDER BY s.created_at DESC LIMIT 1),(SELECT id FROM plans WHERE code='FREE' AND active=true))",
            workspaceId);
        long storageLimit = ((Number) plan.get("storage_limit_bytes")).longValue();
        int videoLimit = ((Number) plan.get("video_limit")).intValue();
        Long stored = jdbc.queryForObject(
            "SELECT COALESCE(sum(a.size_bytes),0) FROM video_assets a JOIN videos v ON v.id=a.video_id " +
            "WHERE v.workspace_id=? AND v.deleted_at IS NULL AND a.asset_type='ORIGINAL'", Long.class, workspaceId);
        Long reserved = jdbc.queryForObject(
            "SELECT COALESCE(sum(us.expected_size_bytes),0) FROM upload_sessions us JOIN videos v ON v.id=us.video_id " +
            "WHERE v.workspace_id=? AND v.deleted_at IS NULL AND us.status IN ('CREATED','UPLOADING') AND us.expires_at>now()",
            Long.class, workspaceId);
        Integer videoCount = jdbc.queryForObject(
            "SELECT count(*) FROM videos WHERE workspace_id=? AND deleted_at IS NULL AND status<>'DELETED'",
            Integer.class, workspaceId);
        long used = (stored == null ? 0 : stored) + (reserved == null ? 0 : reserved);
        if (videoCount != null && videoCount >= videoLimit) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                "Video limit reached for the " + plan.get("code") + " plan (" + videoLimit + " videos).");
        }
        if (incomingBytes > storageLimit || used > storageLimit - incomingBytes) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                "Storage quota exceeded for the " + plan.get("code") + " plan. Used " + used +
                " bytes of " + storageLimit + " bytes.");
        }
        return Map.of("planCode", plan.get("code"), "storageLimitBytes", storageLimit,
            "storedBytes", stored == null ? 0L : stored, "reservedBytes", reserved == null ? 0L : reserved,
            "videoLimit", videoLimit, "videoCount", videoCount == null ? 0 : videoCount);
    }
}
