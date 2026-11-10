CREATE TABLE ai_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('GREETING', 'CHAT', 'SUMMARY', 'PROFILE_REFRESH')),
    tier VARCHAR(10) NOT NULL CHECK (tier IN ('FREE', 'PRO')),
    policy_id VARCHAR(100) NOT NULL,
    model_id VARCHAR(255) NOT NULL,
    region VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED')),
    estimated_input_tokens INTEGER NOT NULL CHECK (estimated_input_tokens >= 0),
    estimated_output_tokens INTEGER NOT NULL CHECK (estimated_output_tokens >= 0),
    reserved_tokens INTEGER NOT NULL CHECK (reserved_tokens >= 0),
    actual_input_tokens INTEGER,
    actual_output_tokens INTEGER,
    settled_tokens INTEGER,
    provider_attempts INTEGER NOT NULL DEFAULT 0 CHECK (provider_attempts >= 0),
    lease_expires_at TIMESTAMPTZ,
    failure_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_ai_requests_user_created ON ai_requests(user_id, created_at DESC);
CREATE INDEX idx_ai_requests_status_lease ON ai_requests(status, lease_expires_at) WHERE status IN ('RESERVED', 'RUNNING');

CREATE TABLE ai_attempts (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES ai_requests(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    actual_input_tokens INTEGER CHECK (actual_input_tokens >= 0),
    actual_output_tokens INTEGER CHECK (actual_output_tokens >= 0),
    failure_code VARCHAR(50),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    UNIQUE (request_id, attempt_number)
);

CREATE INDEX idx_ai_attempts_request ON ai_attempts(request_id, attempt_number);

CREATE TABLE ai_usage_buckets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope VARCHAR(30) NOT NULL CHECK (scope IN ('USER_DAILY', 'ACCOUNT_DAILY', 'MODEL_REGION_DAILY')),
    scope_key VARCHAR(400) NOT NULL,
    window_start DATE NOT NULL,
    used_requests INTEGER NOT NULL DEFAULT 0 CHECK (used_requests >= 0),
    reserved_requests INTEGER NOT NULL DEFAULT 0 CHECK (reserved_requests >= 0),
    used_tokens INTEGER NOT NULL DEFAULT 0 CHECK (used_tokens >= 0),
    reserved_tokens INTEGER NOT NULL DEFAULT 0 CHECK (reserved_tokens >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (scope, scope_key, window_start)
);

CREATE INDEX idx_ai_usage_buckets_window ON ai_usage_buckets(window_start, scope);
