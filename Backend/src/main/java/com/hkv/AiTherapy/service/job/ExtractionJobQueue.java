package com.hkv.AiTherapy.service.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/** PostgreSQL-backed queue: committed jobs survive process loss and can be claimed by one worker. */
@Service
public class ExtractionJobQueue {
    private final JdbcTemplate jdbc;
    private final String policyVersion;
    private final int maxAttempts;

    public ExtractionJobQueue(JdbcTemplate jdbc,
                              @Value("${ai.extraction.policy-version:session-extraction-v1}") String policyVersion,
                              @Value("${ai.extraction.max-attempts:5}") int maxAttempts) {
        if (policyVersion == null || policyVersion.isBlank() || policyVersion.length() > 100)
            throw new IllegalArgumentException("A valid extraction policy version is required");
        if (maxAttempts < 1 || maxAttempts > 10) throw new IllegalArgumentException("Extraction attempts must be 1..10");
        this.jdbc = jdbc;
        this.policyVersion = policyVersion;
        this.maxAttempts = maxAttempts;
    }

    /** Must join the session-end transaction so completion cannot commit without durable work. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(UUID userId, UUID conversationId, long sourceRevision) {
        UUID jobId = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO extraction_jobs
                    (id,user_id,conversation_id,purpose,source_revision,policy_version,status,max_attempts)
                VALUES (?,?,?,'SESSION_SUMMARY',?,?,'QUEUED',?)
                ON CONFLICT DO NOTHING
                """, jobId, userId, conversationId, sourceRevision, policyVersion, maxAttempts);
        if (inserted == 1) return jobId;
        return jdbc.queryForObject("""
                SELECT id FROM extraction_jobs
                 WHERE conversation_id=? AND purpose='SESSION_SUMMARY' AND source_revision=?
                """, UUID.class, conversationId, sourceRevision);
    }

    /** One durable refresh per message snapshot; repeated clicks reuse the same job. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueueRefresh(UUID userId, UUID conversationId, long sourceRevision, UUID sourceMessageId) {
        UUID jobId = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO extraction_jobs
                    (id,user_id,conversation_id,purpose,source_revision,source_message_id,
                     policy_version,status,max_attempts)
                VALUES (?,?,?,'PROFILE_REFRESH',?, ?,?,'QUEUED',?)
                ON CONFLICT DO NOTHING
                """, jobId, userId, conversationId, sourceRevision, sourceMessageId, policyVersion, maxAttempts);
        if (inserted == 1) return jobId;
        return jdbc.queryForObject("""
                SELECT id FROM extraction_jobs
                 WHERE conversation_id=? AND purpose='PROFILE_REFRESH'
                   AND source_revision=? AND source_message_id=?
                """, UUID.class, conversationId, sourceRevision, sourceMessageId);
    }

    @Transactional
    public UUID claimOne(UUID workerId, Duration leaseDuration) {
        jdbc.update("""
                UPDATE extraction_jobs
                   SET status=CASE WHEN attempt_count >= max_attempts THEN 'FAILED' ELSE 'RETRY_WAIT' END,
                       next_attempt_at=NOW(), lease_expires_at=NULL, claimed_by=NULL,
                       last_error_code='WORKER_LEASE_EXPIRED', updated_at=NOW(),
                       completed_at=CASE WHEN attempt_count >= max_attempts THEN NOW() ELSE NULL END
                 WHERE status='RUNNING' AND lease_expires_at < NOW()
                """);
        return jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM extraction_jobs
                     WHERE status IN ('QUEUED','RETRY_WAIT') AND next_attempt_at <= NOW()
                       AND attempt_count < max_attempts
                     ORDER BY next_attempt_at, created_at, id
                     FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE extraction_jobs job
                   SET status='RUNNING', attempt_count=attempt_count+1,
                       claimed_by=?, lease_expires_at=NOW() + (? * INTERVAL '1 millisecond'), updated_at=NOW()
                  FROM candidate WHERE job.id=candidate.id
                RETURNING job.id
                """, statement -> {
                    statement.setObject(1, workerId);
                    statement.setLong(2, leaseDuration.toMillis());
                }, result -> result.next() ? result.getObject(1, UUID.class) : null);
    }

    /** Explicit operator action for a terminal failure. Completed/superseded jobs cannot be replayed. */
    @Transactional
    public boolean replayFailed(UUID jobId) {
        return jdbc.update("""
                UPDATE extraction_jobs
                   SET status='QUEUED', attempt_count=0, replay_count=replay_count+1, next_attempt_at=NOW(),
                       lease_expires_at=NULL, claimed_by=NULL, last_error_code=NULL,
                       completed_at=NULL, updated_at=NOW()
                 WHERE id=? AND status='FAILED'
                """, jobId) == 1;
    }
}
