ALTER TABLE extraction_jobs
    ADD COLUMN source_message_id UUID REFERENCES messages(id) ON DELETE CASCADE;

ALTER TABLE extraction_jobs DROP CONSTRAINT extraction_jobs_purpose_check;
ALTER TABLE extraction_jobs ADD CONSTRAINT extraction_jobs_purpose_check
    CHECK (purpose IN ('SESSION_SUMMARY', 'PROFILE_REFRESH'));

ALTER TABLE extraction_jobs
    DROP CONSTRAINT extraction_jobs_conversation_id_purpose_source_revision_key;

ALTER TABLE extraction_jobs ADD CONSTRAINT extraction_jobs_source_message_shape_check
    CHECK ((purpose = 'SESSION_SUMMARY' AND source_message_id IS NULL)
        OR (purpose = 'PROFILE_REFRESH' AND source_message_id IS NOT NULL));

CREATE UNIQUE INDEX uq_extraction_session_summary
    ON extraction_jobs(conversation_id, source_revision)
    WHERE purpose = 'SESSION_SUMMARY';

CREATE UNIQUE INDEX uq_extraction_profile_refresh
    ON extraction_jobs(conversation_id, source_revision, source_message_id)
    WHERE purpose = 'PROFILE_REFRESH';
