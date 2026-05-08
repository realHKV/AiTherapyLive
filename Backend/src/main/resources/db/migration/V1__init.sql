-- V1__init.sql
-- Initial schema for AI Therapy Platform

-- 1. users
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    display_name    VARCHAR(100),
    oauth_provider  VARCHAR(50),          -- 'google' | null
    oauth_subject   VARCHAR(255),         -- provider's sub claim
    password_hash   VARCHAR(255),         -- null if OAuth-only user
    is_verified     BOOLEAN NOT NULL DEFAULT false,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_oauth ON users(oauth_provider, oauth_subject);

-- 2. therapy_profiles (1:1 with users)
CREATE TABLE therapy_profiles (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    preferred_name       TEXT,                    -- ENCRYPTED
    age_range            VARCHAR(20),             -- '25-34', optional
    communication_style  VARCHAR(50) DEFAULT 'gentle',  -- 'direct'|'gentle'|'reflective'
    ai_persona           VARCHAR(50) DEFAULT 'calm',    -- 'calm'|'encouraging'|'analytical'
    topics_of_concern    TEXT,                    -- ENCRYPTED JSON array e.g. '["anxiety","work"]'
    total_sessions       INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. personality_traits
CREATE TABLE personality_traits (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trait_key   VARCHAR(100) NOT NULL,       -- e.g. 'perfectionist', 'introvert'
    trait_value TEXT,                        -- ENCRYPTED — AI-inferred description
    confidence  FLOAT NOT NULL DEFAULT 0.5,  -- 0.0 to 1.0
    source      VARCHAR(50) NOT NULL,        -- 'user_stated' | 'ai_inferred'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, trait_key)
);

CREATE INDEX idx_traits_user ON personality_traits(user_id, confidence DESC);

-- 4. conversations (sessions)
CREATE TABLE conversations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status           VARCHAR(20) NOT NULL DEFAULT 'active',  -- 'active'|'completed'|'abandoned'
    session_summary  TEXT,                   -- ENCRYPTED — AI-generated after session ends
    mood_start       VARCHAR(30),            -- 'anxious'|'sad'|'neutral'|'hopeful'
    mood_end         VARCHAR(30),
    token_count      INT NOT NULL DEFAULT 0, -- total tokens used this session (cost tracking)
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at         TIMESTAMPTZ,
    summarized_at    TIMESTAMPTZ             -- set when async summarization job completes
);

CREATE INDEX idx_conv_user ON conversations(user_id, started_at DESC);
CREATE INDEX idx_conv_status ON conversations(status) WHERE status = 'active';

-- 5. messages
CREATE TABLE messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL,  -- 'user' | 'assistant'
    content          TEXT NOT NULL,         -- ENCRYPTED
    token_count      INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conv ON messages(conversation_id, created_at ASC);

-- 6. long_term_memories
CREATE TABLE long_term_memories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_conv_id  UUID REFERENCES conversations(id) ON DELETE SET NULL,
    memory_type     VARCHAR(50) NOT NULL,    -- 'life_event'|'goal'|'relationship'|'preference'
    title           VARCHAR(500) NOT NULL,   -- ENCRYPTED — short label e.g. "Sister's wedding"
    detail          TEXT,                    -- ENCRYPTED — full context
    importance      INT NOT NULL DEFAULT 5,  -- 1-10, used to rank context injection
    occurred_at     DATE,                    -- when the event is/was
    follow_up_at    DATE,                    -- when AI should next ask about this
    is_resolved     BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_memory_user ON long_term_memories(user_id, importance DESC);
-- Partial index is mostly useful if querying follow-ups directly
CREATE INDEX idx_memory_followup ON long_term_memories(follow_up_at) WHERE is_resolved = false;

-- 7. refresh_tokens
CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) UNIQUE NOT NULL,  -- SHA-256 hash of raw token
    device_info  VARCHAR(255),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked      BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);
