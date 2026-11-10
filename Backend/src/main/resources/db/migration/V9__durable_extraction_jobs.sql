CREATE TABLE extraction_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('SESSION_SUMMARY')),
    source_revision BIGINT NOT NULL CHECK (source_revision >= 0),
    policy_version VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED','RUNNING','RETRY_WAIT','COMPLETED','FAILED','SUPERSEDED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    replay_count INTEGER NOT NULL DEFAULT 0 CHECK (replay_count >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 5 CHECK (max_attempts BETWEEN 1 AND 10),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lease_expires_at TIMESTAMPTZ,
    claimed_by UUID,
    last_request_id UUID REFERENCES ai_requests(id) ON DELETE SET NULL,
    last_error_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    UNIQUE (conversation_id, purpose, source_revision)
);

CREATE INDEX idx_extraction_jobs_due ON extraction_jobs(status, next_attempt_at, created_at)
    WHERE status IN ('QUEUED','RETRY_WAIT');
CREATE INDEX idx_extraction_jobs_expired_lease ON extraction_jobs(lease_expires_at)
    WHERE status = 'RUNNING';

CREATE TABLE profile_candidates (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES extraction_jobs(id) ON DELETE CASCADE,
    source_message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    source_revision BIGINT NOT NULL CHECK (source_revision >= 0),
    source_output_id UUID NOT NULL UNIQUE,
    field_key VARCHAR(120) NOT NULL,
    proposed_value TEXT NOT NULL,
    evidence TEXT NOT NULL,
    confidence DOUBLE PRECISION CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (job_id, field_key, source_output_id)
);

CREATE INDEX idx_profile_candidates_owner_status ON profile_candidates(user_id, status, created_at DESC);
