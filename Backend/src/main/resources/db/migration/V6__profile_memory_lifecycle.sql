-- Keep all applied migrations immutable. Ciphertext can exceed the clear title length.
ALTER TABLE long_term_memories ALTER COLUMN title TYPE TEXT;
ALTER TABLE therapy_profiles ALTER COLUMN age_range TYPE VARCHAR(50);

ALTER TABLE users
    ADD COLUMN context_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN context_reset_at TIMESTAMPTZ;

ALTER TABLE therapy_profiles ADD COLUMN additional_note TEXT;

ALTER TABLE conversations
    ADD COLUMN context_eligible BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN summary_revision BIGINT;

UPDATE conversations SET summary_revision = 0 WHERE session_summary IS NOT NULL;

ALTER TABLE long_term_memories
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'pending',
    ADD COLUMN source_kind VARCHAR(30) NOT NULL DEFAULT 'ai_inferred',
    ADD COLUMN confirmed_at TIMESTAMPTZ,
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN source_message_id UUID REFERENCES messages(id) ON DELETE SET NULL,
    ADD COLUMN source_job_id UUID,
    ADD COLUMN source_output_id UUID UNIQUE,
    ADD COLUMN source_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN supersedes_id UUID REFERENCES long_term_memories(id) ON DELETE SET NULL;

-- Legacy extracted observations are candidates, not automatically confirmed facts.
UPDATE long_term_memories
SET status = CASE WHEN source_conv_id IS NULL
                  THEN CASE WHEN is_resolved THEN 'resolved' ELSE 'active' END
                  ELSE 'pending' END,
    source_kind = CASE WHEN source_conv_id IS NULL THEN 'user_saved' ELSE 'legacy_extracted' END,
    confirmed_at = CASE WHEN source_conv_id IS NULL THEN created_at ELSE NULL END;

ALTER TABLE long_term_memories
    ADD CONSTRAINT memory_status_valid CHECK (status IN ('pending','active','resolved','superseded','expired')),
    ADD CONSTRAINT memory_source_kind_valid CHECK (source_kind IN ('user_saved','user_stated','ai_inferred','legacy_extracted'));

CREATE INDEX idx_memory_owner_status ON long_term_memories(user_id, status, importance DESC);
CREATE INDEX idx_memory_source_conversation ON long_term_memories(source_conv_id);
