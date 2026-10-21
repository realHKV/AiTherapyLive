-- Converge duplicate active sessions before enforcing the single-active-session invariant.
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY started_at DESC, id DESC) AS position
    FROM conversations
    WHERE status = 'active'
)
UPDATE conversations c
SET status = 'abandoned', ended_at = COALESCE(c.ended_at, NOW())
FROM ranked r
WHERE c.id = r.id AND r.position > 1;

CREATE UNIQUE INDEX uq_conversations_one_active_per_user
    ON conversations(user_id) WHERE status = 'active';

CREATE TABLE chat_turns (
    request_id UUID PRIMARY KEY REFERENCES ai_requests(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('GREETING', 'CHAT')),
    user_message_id UUID REFERENCES messages(id) ON DELETE SET NULL,
    assistant_message_id UUID REFERENCES messages(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED')),
    failure_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_chat_turns_conversation_created ON chat_turns(conversation_id, created_at DESC);

CREATE TABLE chat_session_starts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    request_id UUID REFERENCES ai_requests(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, idempotency_key)
);
