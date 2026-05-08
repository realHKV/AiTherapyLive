package com.hkv.AiTherapy.service.chat;

import com.hkv.AiTherapy.domain.Conversation;
import com.hkv.AiTherapy.domain.Message;
import com.hkv.AiTherapy.domain.TherapyProfile;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.ai.AiMessage;
import com.hkv.AiTherapy.dto.request.ChatMessageRequest;
import com.hkv.AiTherapy.dto.response.ChatMessageResponse;
import com.hkv.AiTherapy.dto.response.ConversationResponse;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.MessageRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.repository.UserSubscriptionRepository;
import com.hkv.AiTherapy.service.ai.AIGateway;
import com.hkv.AiTherapy.service.ai.PromptBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;


@Service
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final TherapyProfileRepository therapyProfileRepository;
    private final AIGateway aiGateway;
    private final PromptBuilder promptBuilder;
    private final ApplicationEventPublisher eventPublisher;
    private final UserSubscriptionRepository userSubscriptionRepository;

    public ChatService(ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       UserRepository userRepository,
                       TherapyProfileRepository therapyProfileRepository,
                       AIGateway aiGateway,
                       PromptBuilder promptBuilder,
                       ApplicationEventPublisher eventPublisher,
                       UserSubscriptionRepository userSubscriptionRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.therapyProfileRepository = therapyProfileRepository;
        this.aiGateway = aiGateway;
        this.promptBuilder = promptBuilder;
        this.eventPublisher = eventPublisher;
        this.userSubscriptionRepository = userSubscriptionRepository;
    }

    @Transactional
    public ConversationResponse startSession(UUID userId) {
        // 1. Check for an existing active session to use as context
        java.util.Optional<Conversation> activeSessionOpt = conversationRepository.findFirstByUserIdAndStatusOrderByStartedAtDesc(userId, "active");
        
        String previousTranscript = "";
        
        if (activeSessionOpt.isPresent()) {
            Conversation oldSession = activeSessionOpt.get();
            // Fetch recent messages to summarize what happened immediately before reload
            List<Message> history = messageRepository.findByConversationId(oldSession.getId(), PageRequest.of(0, 6, Sort.by("createdAt").descending()));
            List<Message> chronologicalHistory = new ArrayList<>(history);
            Collections.reverse(chronologicalHistory);
            
            StringBuilder sb = new StringBuilder();
            for (Message m : chronologicalHistory) {
                sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
            }
            previousTranscript = sb.toString();
            
            // Close the old session so it gets summarized by the AI background job
            endSession(userId, oldSession.getId()); 
        }

        // 2. Start a NEW session
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        Conversation conversation = Conversation.builder()
                .user(user)
                .status("active")
                .tokenCount(0)
                .build();
        
        conversation = conversationRepository.save(conversation);

        // Build the system prompt
        String systemPrompt = promptBuilder.buildSystemPrompt(userId);
        
        // Generate a localized, context-aware greeting
        String greetingInstruction;
        
        if (!previousTranscript.isEmpty()) {
            greetingInstruction = "Greeting instruction: The user just refreshed the page and returned from an interrupted session. " +
                                  "Here is the transcript of the last few messages before they left:\n" + previousTranscript +
                                  "\nGreet the user, briefly acknowledge the specific topic they were just discussing in the transcript, " +
                                  "and ask how they would like to continue.";
        } else {
            greetingInstruction = "Greeting instruction: Introduce yourself briefly, welcome the user to a new session, " +
                                  "and ask an open-ended question about how they are doing right now based on past memory context if any.";
        }
        
        List<AiMessage> messages = List.of(
                AiMessage.system(systemPrompt),
                AiMessage.user(greetingInstruction)
        );

        String greetingText = aiGateway.generateChatResponse(messages, isProUser(userId));
        
        // Assume rough token usage (in real apps, use exact counts from response)
        conversation.setTokenCount(conversation.getTokenCount() + 150);

        Message aiMessage = Message.builder()
                .conversation(conversation)
                .role("assistant")
                .content(greetingText)
                .tokenCount(50)
                .build();

        messageRepository.save(aiMessage);
        conversationRepository.save(conversation);

        return ConversationResponse.builder()
                .id(conversation.getId().toString())
                .status(conversation.getStatus())
                .startedAt(conversation.getStartedAt())
                .tokenCount(conversation.getTokenCount())
                .recentMessages(List.of(mapToDto(aiMessage)))
                .build();
    }

    @Transactional
    public ChatMessageResponse sendMessage(UUID userId, UUID conversationId, ChatMessageRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
                
        if (!conversation.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }
        
        if (!"active".equals(conversation.getStatus())) {
            throw new RuntimeException("Cannot send message to a non-active conversation");
        }

        // 1. Save user's message
        Message userMessage = Message.builder()
                .conversation(conversation)
                .role("user")
                .content(request.getContent())
                .tokenCount(request.getContent().length() / 4)
                .build();
        messageRepository.save(userMessage);

        // 2. Fetch conversational context (last 6 messages)
        List<Message> history = messageRepository.findByConversationId(conversationId, PageRequest.of(0, 6, Sort.by("createdAt").descending()));
        
        // Reverse because we queried descending to get the most recent, but AI needs them in chronological order
        List<Message> chronologicalHistory = new ArrayList<>(history);
        Collections.reverse(chronologicalHistory);

        // 3. Assemble full prompt
        List<AiMessage> aiMessages = new ArrayList<>();
        aiMessages.add(AiMessage.system(promptBuilder.buildSystemPrompt(userId)));
        
        for (Message msg : chronologicalHistory) {
            if ("user".equals(msg.getRole())) {
                aiMessages.add(AiMessage.user(msg.getContent()));
            } else {
                aiMessages.add(AiMessage.assistant(msg.getContent()));
            }
        }

        // 4. Generate AI Response
        String aiResponseText = aiGateway.generateChatResponse(aiMessages, isProUser(userId));

        // 5. Save AI message
        Message aiMessage = Message.builder()
                .conversation(conversation)
                .role("assistant")
                .content(aiResponseText)
                .tokenCount(aiResponseText.length() / 4)
                .build();
        messageRepository.save(aiMessage);

        // Update basic conversation stats
        conversation.setTokenCount(conversation.getTokenCount() + userMessage.getTokenCount() + aiMessage.getTokenCount());
        conversationRepository.save(conversation);

        return mapToDto(aiMessage);
    }

    public SseEmitter streamMessage(UUID conversationId, UUID userId, ChatMessageRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (!conversation.getUser().getId().equals(userId)) {
            throw new SecurityException("Not authorized to access this conversation");
        }

        if ("completed".equals(conversation.getStatus())) {
            throw new IllegalStateException("Cannot send message to a completed conversation");
        }

        // 1. Save user's message
        Message userMessage = Message.builder()
                .conversation(conversation)
                .role("user")
                .content(request.getContent())
                .tokenCount(request.getContent().length() / 4)
                .build();
        messageRepository.save(userMessage);

        // 2. Fetch context
        List<Message> history = messageRepository.findByConversationId(conversationId, PageRequest.of(0, 6, Sort.by("createdAt").descending()));
        List<Message> chronologicalHistory = new ArrayList<>(history);
        java.util.Collections.reverse(chronologicalHistory);

        // 3. Prepare AI Prompt
        List<AiMessage> aiMessages = new ArrayList<>();
        aiMessages.add(AiMessage.system(promptBuilder.buildSystemPrompt(userId)));
        
        for (Message msg : chronologicalHistory) {
            if ("user".equals(msg.getRole())) {
                aiMessages.add(AiMessage.user(msg.getContent()));
            } else {
                aiMessages.add(AiMessage.assistant(msg.getContent()));
            }
        }

        // 4. Stream response
        boolean isPro = isProUser(userId);
        SseEmitter emitter = new SseEmitter(60000L); // 1-minute timeout
        StringBuilder fullResponse = new StringBuilder();

        aiGateway.streamChatResponse(aiMessages, isPro,
                chunk -> {
                    try {
                        fullResponse.append(chunk);
                        emitter.send(chunk);
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                },
                () -> {
                    // On Complete, save AI message to DB
                    Message aiMessage = Message.builder()
                            .conversation(conversation)
                            .role("assistant")
                            .content(fullResponse.toString())
                            .tokenCount(fullResponse.length() / 4)
                            .build();
                    messageRepository.save(aiMessage);

                    conversation.setTokenCount(conversation.getTokenCount() + userMessage.getTokenCount() + aiMessage.getTokenCount());
                    conversationRepository.save(conversation);

                    emitter.complete();
                },
                error -> emitter.completeWithError(error)
        );

        return emitter;
    }

    @Transactional
    public void endSession(UUID userId, UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (!conversation.getUser().getId().equals(userId)) {
            throw new SecurityException("Not authorized to access this conversation");
        }

        conversation.setStatus("completed");
        conversation.setEndedAt(Instant.now());
        conversationRepository.save(conversation);

        java.util.Optional<TherapyProfile> profileOpt = therapyProfileRepository.findByUserId(userId);
        if (profileOpt.isPresent()) {
            TherapyProfile profile = profileOpt.get();
            profile.setTotalSessions(profile.getTotalSessions() + 1);
            therapyProfileRepository.save(profile);
        }

        // Instantly trigger the background summarization job
        eventPublisher.publishEvent(new com.hkv.AiTherapy.service.job.SessionEndedEvent(conversationId));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Returns true if the user has an active PRO subscription. */
    private boolean isProUser(UUID userId) {
        return userSubscriptionRepository.findByUserId(userId)
                .map(sub -> sub.isPro())
                .orElse(false);
    }

    private ChatMessageResponse mapToDto(Message message) {
        return ChatMessageResponse.builder()
                .id(message.getId().toString())
                .conversationId(message.getConversation().getId().toString())
                .senderRole(message.getRole())
                .content(message.getContent())
                .sentAt(message.getCreatedAt())
                .build();
    }
}
