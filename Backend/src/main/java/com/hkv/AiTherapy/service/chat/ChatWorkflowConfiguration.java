package com.hkv.AiTherapy.service.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@ConditionalOnProperty(name = {"ai.chat.v2.enabled", "ai.routing.enabled"}, havingValue = "true")
public class ChatWorkflowConfiguration {
    @Bean(name = "chatGenerationExecutor")
    public ThreadPoolTaskExecutor chatGenerationExecutor(
            @Value("${ai.chat.v2.workers:8}") int workers,
            @Value("${ai.chat.v2.queue-capacity:64}") int queueCapacity) {
        if (workers < 1 || workers > 64 || queueCapacity < 1 || queueCapacity > 2048)
            throw new IllegalStateException("Chat worker and queue limits are invalid");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("chat-generation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "chatDeadlineScheduler", destroyMethod = "shutdownNow")
    public ScheduledThreadPoolExecutor chatDeadlineScheduler() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "chat-deadline-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2, factory);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
