package com.hkv.AiTherapy.service.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls durable extraction jobs. PostgreSQL remains the queue so process restarts lose no work. */
@Component
@ConditionalOnProperty(name = {"ai.routing.enabled", "ai.extraction.worker-enabled"}, havingValue = "true")
public class SummarizationJob {
    private static final Logger log = LoggerFactory.getLogger(SummarizationJob.class);
    private final DurableExtractionProcessor processor;

    public SummarizationJob(DurableExtractionProcessor processor) { this.processor = processor; }

    @Scheduled(fixedDelayString = "${ai.extraction.poll-interval-ms:5000}")
    public void processNextDueJob() {
        try { processor.processOne(); }
        catch (RuntimeException error) { log.warn("Durable extraction poll failed ({})", error.getClass().getSimpleName()); }
    }
}
