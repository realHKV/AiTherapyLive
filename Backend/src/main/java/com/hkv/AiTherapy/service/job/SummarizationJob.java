package com.hkv.AiTherapy.service.job;

import tools.jackson.databind.ObjectMapper;
import com.hkv.AiTherapy.domain.Conversation;
import com.hkv.AiTherapy.domain.LongTermMemory;
import com.hkv.AiTherapy.domain.Message;
import com.hkv.AiTherapy.dto.ai.SummaryExtraction;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.MessageRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.service.ai.AIGateway;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Service
public class SummarizationJob {
    private static final Logger log = LoggerFactory.getLogger(SummarizationJob.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final LongTermMemoryRepository memoryRepository;
    private final AIGateway aiGateway;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final TransactionTemplate transactions;

    public SummarizationJob(ConversationRepository conversationRepository,
                            MessageRepository messageRepository,
                            LongTermMemoryRepository memoryRepository,
                            AIGateway aiGateway,
                            ObjectMapper objectMapper,
                            UserRepository userRepository,
                            PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.memoryRepository = memoryRepository;
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Async
    @EventListener
    public void processUnsummarizedSessions(SessionEndedEvent event) {
        Conversation conv = conversationRepository.findById(event.conversationId())
                .orElse(null);
                
        if (conv != null && conv.isContextEligible() && "completed".equals(conv.getStatus()) && conv.getSummarizedAt() == null) {
            try {
                // For final end-of-session summary: save memories = true, markCompleted = true
                summarizeConversation(conv, true, true);
            } catch (Exception e) {
                log.warn("Failed to summarize conversation {}; it remains unsummarized for retry ({})",
                        conv.getId(), e.getClass().getSimpleName());
            }
        }
    }

    private void summarizeConversation(Conversation conv, boolean saveMemories, boolean markCompleted) {
        if (!userRepository.existsByIdAndIsActiveTrue(conv.getUser().getId())) return;
        if (!conv.isContextEligible()) return;
        long sourceRevision = conv.getUser().getContextRevision();
        List<Message> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());
        
        if (history.isEmpty()) {
            if (markCompleted) transactions.executeWithoutResult(status -> {
                if (userRepository.findByIdForUpdate(conv.getUser().getId())
                        .filter(u -> u.isActive() && u.getContextRevision() == sourceRevision).isEmpty()) return;
                conversationRepository.findById(conv.getId()).ifPresent(current -> {
                    if (!current.isContextEligible()) return;
                    current.setSummarizedAt(Instant.now());
                    current.setSummaryRevision(sourceRevision);
                    conversationRepository.save(current);
                });
            });
            return;
        }

        StringBuilder transcript = new StringBuilder();
        for (Message m : history) {
            transcript.append(m.getRole().toUpperCase()).append(": ").append(m.getContent()).append("\n\n");
        }

        String rawAiResponse = aiGateway.summarizeSession(transcript.toString());
        
        String jsonPayload = cleanJsonResponse(rawAiResponse);

        // The account lock serializes this write with account deletion. Never hold it during AI work.
        transactions.executeWithoutResult(status -> {
        if (userRepository.findByIdForUpdate(conv.getUser().getId())
                .filter(u -> u.isActive() && u.getContextRevision() == sourceRevision).isEmpty()) return;
        Conversation current = conversationRepository.findById(conv.getId()).orElse(null);
        if (current == null || !current.isContextEligible() || "abandoned".equals(current.getStatus())) return;
        try {
            SummaryExtraction extraction = objectMapper.readValue(jsonPayload, SummaryExtraction.class);
            if (extraction.getSummary() == null || extraction.getSummary().isBlank())
                throw new IllegalArgumentException("Missing summary");
            
            if (markCompleted) {
                current.setSessionSummary(extraction.getSummary());
                current.setSummaryRevision(sourceRevision);
            }

            if (saveMemories && extraction.getMemories() != null) {
                int outputIndex = 0;
                for (SummaryExtraction.MemoryExtraction m : extraction.getMemories()) {
                    UUID outputId = UUID.nameUUIDFromBytes((current.getId() + ":" + outputIndex++)
                            .getBytes(StandardCharsets.UTF_8));
                    if (memoryRepository.existsBySourceOutputId(outputId)) continue;
                    if (m.getTitle() == null || m.getTitle().isBlank()) continue;
                    LocalDate followUp = null;
                    if (m.getFollowUpAt() != null) {
                        try {
                            followUp = LocalDate.parse(m.getFollowUpAt());
                        } catch (DateTimeParseException ignored) {}
                    }
                    
                    String type = m.getType() != null && Set.of("life_event", "goal", "relationship", "preference", "fact").contains(m.getType())
                            ? m.getType() : "fact";
                    LongTermMemory ltm = LongTermMemory.builder()
                            .user(current.getUser())
                            .sourceConversation(current)
                            .sourceOutputId(outputId)
                            .sourceRevision(sourceRevision)
                            .sourceKind("ai_inferred")
                            .status("pending")
                            .title(truncate(m.getTitle().trim(), 200))
                            .detail(truncate(m.getDetail(), 2000))
                            .memoryType(type)
                            .importance(Math.max(1, Math.min(10, m.getImportance())))
                            .followUpAt(followUp)
                            .build();
                    memoryRepository.save(ltm);
                }
            }
            
        } catch (Exception e) {
            throw new IllegalStateException("Invalid extraction result", e);
        }
        
        if (markCompleted) {
            current.setSummarizedAt(Instant.now());
            conversationRepository.save(current);
        }
        });
    }
    
    private String cleanJsonResponse(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");
        
        if (start != -1 && end != -1 && start < end) {
            return raw.substring(start, end + 1);
        }
        return "{}";
    }

    /** Safely truncates a string to maxLen characters to avoid VARCHAR overflow. */
    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
