ALTER TABLE upload_sessions
    ADD COLUMN IF NOT EXISTS expected_sha256 CHAR(64),
    ADD COLUMN IF NOT EXISTS completed_sha256 CHAR(64),
    ADD COLUMN IF NOT EXISTS chunk_size_bytes BIGINT NOT NULL DEFAULT 8388608,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE upload_chunks (
    upload_session_id UUID NOT NULL REFERENCES upload_sessions(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 CHAR(64) NOT NULL,
    object_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(upload_session_id, chunk_index)
);
CREATE INDEX upload_sessions_expiry_idx ON upload_sessions(status, expires_at);
