package com.hkv.AiTherapy.service.operations;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** Low-cardinality operational metrics. No account IDs, prompts, content, or secrets are labels. */
@Component
public class AiOperationalMetrics {
    private final MeterRegistry registry;
    private final JdbcTemplate jdbc;
    private final AtomicInteger activeStreams = new AtomicInteger();

    public AiOperationalMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        this.registry = registry;
        this.jdbc = jdbc;
        Gauge.builder("ai.streams.active", activeStreams, AtomicInteger::get).register(registry);
        Gauge.builder("ai.extraction.queue.depth", this, metrics -> metrics.queueDepth()).register(registry);
    }

    public void streamStarted() { activeStreams.incrementAndGet(); }
    public void streamFinished(String result) {
        activeStreams.updateAndGet(value -> Math.max(0, value - 1));
        Counter.builder("ai.streams.completed").tag("result", safeResult(result)).register(registry).increment();
    }
    public void extractionTransition(String result) {
        Counter.builder("ai.extraction.jobs").tag("result", safeResult(result)).register(registry).increment();
    }

    private double queueDepth() {
        try {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM extraction_jobs WHERE status IN ('QUEUED','RETRY_WAIT')", Long.class);
            return count == null ? 0 : count;
        } catch (RuntimeException unavailable) {
            return Double.NaN;
        }
    }

    private static String safeResult(String result) {
        return switch (result) {
            case "completed", "failed", "retry", "superseded", "cancelled" -> result;
            default -> "other";
        };
    }
}
