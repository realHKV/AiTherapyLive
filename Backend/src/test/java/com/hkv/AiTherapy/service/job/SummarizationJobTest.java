package com.hkv.AiTherapy.service.job;

import tools.jackson.databind.ObjectMapper;
import com.hkv.AiTherapy.domain.Conversation;
import com.hkv.AiTherapy.domain.Message;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.MessageRepository;
import com.hkv.AiTherapy.repository.PersonalityTraitRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.service.ai.AIGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummarizationJobTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private PersonalityTraitRepository traitRepository;
    @Mock
    private LongTermMemoryRepository memoryRepository;
    @Mock
    private AIGateway aiGateway;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SummarizationJob summarizationJob;

    private Conversation testConversation;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        conversationId = UUID.randomUUID();
        User testUser = User.builder().id(UUID.randomUUID()).isActive(true).build();
        
        testConversation = new Conversation();
        testConversation.setId(conversationId);
        testConversation.setUser(testUser);
        testConversation.setStatus("completed");
        testConversation.setStartedAt(Instant.now());
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(userRepository.existsByIdAndIsActiveTrue(testUser.getId())).thenReturn(true);
        lenient().when(userRepository.findByIdForUpdate(testUser.getId())).thenReturn(Optional.of(testUser));
    }

    @Test
    void testProcessEvent_SuccessfulExtraction() {
        // Arrange
        SessionEndedEvent event = new SessionEndedEvent(conversationId);
        
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        
        Message userMsg = Message.builder().role("user").content("I feel anxious doing exams").build();
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)).thenReturn(List.of(userMsg));

        String mockAiJson = """
                ```json
                {
                  "summary": "User discussed exam anxiety.",
                  "traits": [
                    {
                      "key": "Test Anxiety",
                      "confidence": 0.95
                    }
                  ],
                  "memories": [
                    {
                      "title": "Struggles with exams",
                      "detail": "Gets anxious before and during standard exams",
                      "importance": 7,
                      "type": "fact",
                      "followUpAt": "2024-05-01"
                    }
                  ]
                }
                ```
                """;
        
        when(aiGateway.summarizeSession(anyString())).thenReturn(mockAiJson);

        // Act
        summarizationJob.processUnsummarizedSessions(event);

        // Assert
        assertNotNull(testConversation.getSummarizedAt());
        assertTrue(testConversation.getSessionSummary().contains("User discussed exam anxiety"));
        
        verify(traitRepository, never()).save(any());
        verify(memoryRepository, times(1)).save(any());
        var candidate = ArgumentCaptor.forClass(com.hkv.AiTherapy.domain.LongTermMemory.class);
        verify(memoryRepository).save(candidate.capture());
        assertTrue("pending".equals(candidate.getValue().getStatus()));
        assertTrue(candidate.getValue().getConfirmedAt() == null);
        verify(conversationRepository).save(testConversation);
    }

    @Test
    void deletionDuringProviderCallDiscardsResult() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of(Message.builder().role("user").content("synthetic text").build()));
        when(aiGateway.summarizeSession(anyString())).thenAnswer(invocation -> {
            when(userRepository.findByIdForUpdate(testConversation.getUser().getId()))
                    .thenReturn(Optional.empty());
            return "{\"summary\":\"synthetic summary\"}";
        });

        summarizationJob.processUnsummarizedSessions(new SessionEndedEvent(conversationId));

        verify(conversationRepository, never()).save(any());
        verifyNoInteractions(traitRepository, memoryRepository);
    }

    @Test
    void contextResetDuringProviderCallDiscardsResult() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of(Message.builder().role("user").content("synthetic text").build()));
        when(aiGateway.summarizeSession(anyString())).thenAnswer(invocation -> {
            testConversation.getUser().setContextRevision(1);
            return "{\"summary\":\"stale summary\"}";
        });

        summarizationJob.processUnsummarizedSessions(new SessionEndedEvent(conversationId));

        verify(conversationRepository, never()).save(any());
        verifyNoInteractions(traitRepository, memoryRepository);
    }
}
