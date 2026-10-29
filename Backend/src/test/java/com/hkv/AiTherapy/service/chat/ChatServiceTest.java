package com.hkv.AiTherapy.service.chat;

import com.hkv.AiTherapy.domain.Conversation;
import com.hkv.AiTherapy.domain.Message;
import com.hkv.AiTherapy.domain.TherapyProfile;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.MessageRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.service.ai.AIGateway;
import com.hkv.AiTherapy.service.ai.PromptBuilder;
import com.hkv.AiTherapy.service.job.ExtractionJobQueue;
import com.hkv.AiTherapy.service.payment.EntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.hkv.AiTherapy.dto.request.ChatMessageRequest;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private TherapyProfileRepository therapyProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AIGateway aiGateway;
    @Mock
    private PromptBuilder promptBuilder;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private ExtractionJobQueue extractionJobQueue;

    @InjectMocks
    private ChatService chatService;

    private User testUser;
    private Conversation testConversation;
    private TherapyProfile testProfile;
    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        testUser = User.builder().id(userId).email("test@test.com").build();

        testConversation = new Conversation();
        testConversation.setId(conversationId);
        testConversation.setUser(testUser);
        testConversation.setStatus("active");
        testConversation.setTokenCount(0);

        testProfile = new TherapyProfile();
        testProfile.setUser(testUser);
        testProfile.setTotalSessions(5);
    }

    @Test
    void testEndSession_FiresEventAndUpdatesProfile() {
        // Arrange
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(testUser));
        when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(testConversation));
        when(therapyProfileRepository.findByUserId(userId)).thenReturn(Optional.of(testProfile));

        // Act
        chatService.endSession(userId, conversationId);

        // Assert
        assertEquals("completed", testConversation.getStatus());
        assertNotNull(testConversation.getEndedAt());
        assertEquals(6, testProfile.getTotalSessions());

        verify(conversationRepository).save(testConversation);
        verify(therapyProfileRepository).save(testProfile);

        verify(extractionJobQueue).enqueue(userId, conversationId, testUser.getContextRevision());
        verify(eventPublisher).publishEvent(any(ConversationEndedEvent.class));
    }

    @Test
    void repeatedEndDoesNotIncrementCountsOrRepublishEvent() {
        testConversation.setStatus("completed");
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(testUser));
        when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(testConversation));

        chatService.endSession(userId, conversationId);

        verifyNoInteractions(therapyProfileRepository, eventPublisher);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void profileRefreshQueuesCurrentMessageWithoutEndingSession() {
        UUID messageId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Message message = Message.builder().id(messageId).conversation(testConversation).role("user")
                .content("I am studying nursing.").build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(testUser));
        when(conversationRepository.findByIdForUpdate(conversationId)).thenReturn(Optional.of(testConversation));
        when(messageRepository.findFirstByConversationIdAndRoleOrderByCreatedAtDescIdDesc(conversationId, "user"))
                .thenReturn(Optional.of(message));
        when(messageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversationId))
                .thenReturn(Optional.of(message));
        when(extractionJobQueue.enqueueRefresh(userId, conversationId, testUser.getContextRevision(), messageId))
                .thenReturn(jobId);

        assertEquals(jobId, chatService.requestProfileRefresh(userId, conversationId));
        assertEquals("active", testConversation.getStatus());
        verifyNoInteractions(therapyProfileRepository, eventPublisher);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void anotherUserCannotSendMessageToConversation() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("Synthetic message");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage(UUID.randomUUID(), conversationId, request));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(messageRepository, aiGateway);
    }
}
