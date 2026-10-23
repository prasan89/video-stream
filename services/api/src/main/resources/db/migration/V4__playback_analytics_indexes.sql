CREATE INDEX IF NOT EXISTS playback_events_video_date_idx ON playback_events(video_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS playback_events_viewer_idx ON playback_events(workspace_id, viewer_fingerprint_hash, occurred_at DESC);
