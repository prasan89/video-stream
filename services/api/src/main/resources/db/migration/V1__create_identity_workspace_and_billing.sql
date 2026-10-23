CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash TEXT,
    display_name VARCHAR(160) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    identity_provider VARCHAR(40) NOT NULL DEFAULT 'LOCAL',
    identity_subject VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_provider_subject_unique UNIQUE (identity_provider, identity_subject)
);

CREATE TABLE workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workspace_members (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER','ADMIN','EDITOR','VIEWER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX workspace_members_user_idx ON workspace_members(user_id);

CREATE TABLE plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
    monthly_price_paise BIGINT NOT NULL CHECK (monthly_price_paise >= 0),
    annual_price_paise BIGINT CHECK (annual_price_paise >= 0),
    storage_limit_bytes BIGINT NOT NULL CHECK (storage_limit_bytes >= 0),
    monthly_bandwidth_limit_bytes BIGINT NOT NULL CHECK (monthly_bandwidth_limit_bytes >= 0),
    video_limit INTEGER NOT NULL CHECK (video_limit >= 0),
    seat_limit INTEGER NOT NULL CHECK (seat_limit >= 1),
    features JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    plan_id UUID NOT NULL REFERENCES plans(id),
    provider VARCHAR(40) NOT NULL DEFAULT 'RAZORPAY',
    provider_customer_id VARCHAR(160),
    provider_subscription_id VARCHAR(160) UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('INCOMPLETE','TRIALING','ACTIVE','PAST_DUE','CANCELLED','EXPIRED')),
    currency CHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
    current_period_start TIMESTAMPTZ,
    current_period_end TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX subscriptions_workspace_status_idx ON subscriptions(workspace_id, status);

CREATE TABLE billing_provider_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
      CHECK (processing_status IN ('RECEIVED','PROCESSING','PROCESSED','FAILED')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    UNIQUE (provider, provider_event_id)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE SET NULL,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(120) NOT NULL,
    resource_type VARCHAR(80),
    resource_id VARCHAR(160),
    request_id VARCHAR(120),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_logs_workspace_created_idx ON audit_logs(workspace_id, created_at DESC);
