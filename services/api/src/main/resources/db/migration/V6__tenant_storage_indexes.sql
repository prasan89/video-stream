-- Tenant-scoped access paths and query support.
CREATE INDEX IF NOT EXISTS videos_workspace_deleted_created_idx
    ON videos(workspace_id, deleted_at, created_at DESC);
CREATE INDEX IF NOT EXISTS videos_workspace_source_idx
    ON videos(workspace_id, source_object_key)
    WHERE source_object_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS subscriptions_workspace_status_period_idx
    ON subscriptions(workspace_id, status, current_period_end);
CREATE INDEX IF NOT EXISTS upload_sessions_video_status_expiry_idx
    ON upload_sessions(video_id, status, expires_at);
