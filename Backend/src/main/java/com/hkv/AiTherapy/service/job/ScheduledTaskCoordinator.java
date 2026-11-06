package com.hkv.AiTherapy.service.job;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates non-idempotent scheduled maintenance across application replicas with PostgreSQL advisory locks. */
@Service
public class ScheduledTaskCoordinator {
    private final JdbcTemplate jdbc;

    public ScheduledTaskCoordinator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public boolean runIfOwner(String taskName, Runnable task) {
        if (taskName == null || !taskName.matches("[a-z0-9-]{1,64}") || task == null)
            throw new IllegalArgumentException("A valid scheduled task name and action are required");
        Boolean acquired = jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtextextended(?, 0))", Boolean.class, "aitherapy:" + taskName);
        if (!Boolean.TRUE.equals(acquired)) return false;
        task.run();
        return true;
    }
}
