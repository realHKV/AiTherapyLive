package com.hkv.AiTherapy.service.ai;

import com.hkv.AiTherapy.domain.LongTermMemory;
import com.hkv.AiTherapy.domain.TherapyProfile;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromptBuilderAcceptanceTest {
    private final UUID userId = UUID.randomUUID();
    private final TherapyProfileRepository profiles = mock(TherapyProfileRepository.class);
    private final LongTermMemoryRepository memories = mock(LongTermMemoryRepository.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private PromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new PromptBuilder(profiles, memories, objectMapper, conversations);
        when(profiles.findByUserId(userId)).thenReturn(java.util.Optional.of(TherapyProfile.builder().build()));
        when(conversations.findLastCompleted(userId)).thenReturn(java.util.Optional.empty());
    }

    @Test
    void topicChangesExcludeIrrelevantHighImportanceFactsAndKeepDueFollowUp() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        when(memories.findEligibleContextCandidates(userId, 50)).thenReturn(List.of(
                memory("Work anxiety coping", "Preparing for a difficult manager conversation", 3, null),
                memory("Dental appointment", "Ask whether the follow-up was scheduled", 2, yesterday),
                memory("Favorite vacation destination", "Synthetic unrelated fact", 10, null)));

        String workPrompt = builder.buildSystemPrompt(userId, "work anxiety", 600);
        assertTrue(workPrompt.contains("Work anxiety coping"));
        assertTrue(workPrompt.contains("Dental appointment"));
        assertFalse(workPrompt.contains("Favorite vacation destination"));
        assertTrue(workPrompt.indexOf("Dental appointment") < workPrompt.indexOf("Work anxiety coping"));

        String sleepPrompt = builder.buildSystemPrompt(userId, "sleep routine", 600);
        assertFalse(sleepPrompt.contains("Work anxiety coping"));
        assertFalse(sleepPrompt.contains("Favorite vacation destination"));
        assertTrue(sleepPrompt.contains("Dental appointment"));
        verify(memories, times(2)).findEligibleContextCandidates(userId, 50);
    }

    private static LongTermMemory memory(String title, String detail, int importance, LocalDate followUpAt) {
        return LongTermMemory.builder()
                .title(title)
                .detail(detail)
                .memoryType("life_event")
                .status("active")
                .confirmedAt(Instant.now())
                .importance(importance)
                .followUpAt(followUpAt)
                .createdAt(Instant.now())
                .build();
    }
}
