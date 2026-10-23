CREATE TABLE videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    title VARCHAR(250) NOT NULL,
    description TEXT,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE'
      CHECK (visibility IN ('PRIVATE','UNLISTED','PUBLIC')),
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED'
      CHECK (status IN ('CREATED','UPLOADING','UPLOADED','PROCESSING','READY','FAILED','DELETING','DELETED')),
    duration_seconds INTEGER CHECK (duration_seconds >= 0),
    source_object_key TEXT,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX videos_workspace_created_idx ON videos(workspace_id, created_at DESC);
CREATE INDEX videos_workspace_status_idx ON videos(workspace_id, status);

CREATE TABLE video_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    asset_type VARCHAR(30) NOT NULL CHECK (asset_type IN ('ORIGINAL','HLS_MANIFEST','HLS_SEGMENT','THUMBNAIL','CAPTION','PREVIEW')),
    object_key TEXT NOT NULL,
    mime_type VARCHAR(150),
    size_bytes BIGINT CHECK (size_bytes >= 0),
    width INTEGER CHECK (width > 0),
    height INTEGER CHECK (height > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (video_id, asset_type, object_key)
);

CREATE TABLE upload_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    object_key TEXT NOT NULL UNIQUE,
    expected_size_bytes BIGINT NOT NULL CHECK (expected_size_bytes > 0),
    content_type VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED'
      CHECK (status IN ('CREATED','UPLOADING','UPLOADED','EXPIRED','REJECTED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transcoding_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL DEFAULT 'GCP_TRANSCODER',
    provider_job_id VARCHAR(200) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
      CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error_code VARCHAR(120),
    last_error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);
CREATE INDEX transcoding_jobs_status_created_idx ON transcoding_jobs(status, created_at);

CREATE TABLE video_captions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    language_code VARCHAR(20) NOT NULL,
    object_key TEXT NOT NULL,
    source VARCHAR(20) NOT NULL CHECK (source IN ('MANUAL','AUTO','AI_TRANSLATED')),
    status VARCHAR(20) NOT NULL DEFAULT 'READY' CHECK (status IN ('PROCESSING','READY','FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (video_id, language_code, source)
);

CREATE TABLE usage_daily (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    stored_bytes_estimate BIGINT NOT NULL DEFAULT 0 CHECK (stored_bytes_estimate >= 0),
    delivered_bytes BIGINT NOT NULL DEFAULT 0 CHECK (delivered_bytes >= 0),
    play_count BIGINT NOT NULL DEFAULT 0 CHECK (play_count >= 0),
    transcode_seconds BIGINT NOT NULL DEFAULT 0 CHECK (transcode_seconds >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, usage_date)
);

CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    key_hash TEXT NOT NULL,
    scopes TEXT[] NOT NULL DEFAULT '{}',
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX api_keys_workspace_idx ON api_keys(workspace_id);

CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    secret_reference TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    event_id VARCHAR(200) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
      CHECK (status IN ('PENDING','DELIVERED','FAILED')),
    next_attempt_at TIMESTAMPTZ,
    last_http_status INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (endpoint_id, event_id)
);
