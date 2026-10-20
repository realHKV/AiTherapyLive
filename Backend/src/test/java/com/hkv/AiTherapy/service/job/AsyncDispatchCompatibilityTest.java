package com.hkv.AiTherapy.service.job;

import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.MessageRepository;
import com.hkv.AiTherapy.repository.PersonalityTraitRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.service.ai.AIGateway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsyncDispatchCompatibilityTest {

    @Configuration
    @EnableAsync
    static class AsyncConfig { }

    @Test
    void sessionEndedEventRunsListenerOnExecutorThread() throws Exception {
        var conversationId = UUID.randomUUID();
        var repository = mock(ConversationRepository.class);
        var called = new CountDownLatch(1);
        var listenerThread = new AtomicReference<Thread>();
        when(repository.findById(conversationId)).thenAnswer(invocation -> {
            listenerThread.set(Thread.currentThread());
            called.countDown();
            return Optional.empty();
        });

        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AsyncConfig.class);
            context.registerBean(SummarizationJob.class, () -> new SummarizationJob(
                    repository,
                    mock(MessageRepository.class),
                    mock(PersonalityTraitRepository.class),
                    mock(LongTermMemoryRepository.class),
                    mock(TherapyProfileRepository.class),
                    mock(AIGateway.class),
                    new ObjectMapper(),
                    mock(UserRepository.class),
                    mock(PlatformTransactionManager.class)));
            context.refresh();

            context.publishEvent(new SessionEndedEvent(conversationId));
            assertTrue(called.await(5, TimeUnit.SECONDS));
            assertNotEquals(Thread.currentThread(), listenerThread.get());
        }
    }
}
