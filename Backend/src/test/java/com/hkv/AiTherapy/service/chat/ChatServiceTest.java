package com.hkv.AiTherapy.service.chat;

import com.hkv.AiTherapy.domain.Conversation;
import com.hkv.AiTherapy.domain.Message;
import com.hkv.AiTherapy.domain.TherapyProfile;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.MessageRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.service.ai.AIGateway;
import com.hkv.AiTherapy.service.ai.PromptBuilder;
import com.hkv.AiTherapy.service.job.SessionEndedEvent;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private TherapyProfileRepository therapyProfileRepository;
    @Mock
    private AIGateway aiGateway;
    @Mock
    private PromptBuilder promptBuilder;
    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(therapyProfileRepository.findByUserId(userId)).thenReturn(Optional.of(testProfile));

        // Act
        chatService.endSession(userId, conversationId);

        // Assert
        assertEquals("completed", testConversation.getStatus());
        assertNotNull(testConversation.getEndedAt());
        assertEquals(6, testProfile.getTotalSessions());

        verify(conversationRepository).save(testConversation);
        verify(therapyProfileRepository).save(testProfile);

        ArgumentCaptor<SessionEndedEvent> eventCaptor = ArgumentCaptor.forClass(SessionEndedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        SessionEndedEvent firedEvent = eventCaptor.getValue();
        assertEquals(conversationId, firedEvent.conversationId());
    }
}
