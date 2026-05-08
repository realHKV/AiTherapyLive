package com.hkv.AiTherapy.service.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hkv.AiTherapy.domain.Conversation;
import com.hkv.AiTherapy.domain.LongTermMemory;
import com.hkv.AiTherapy.domain.Message;
import com.hkv.AiTherapy.domain.PersonalityTrait;
import com.hkv.AiTherapy.dto.ai.SummaryExtraction;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.MessageRepository;
import com.hkv.AiTherapy.repository.PersonalityTraitRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import com.hkv.AiTherapy.service.ai.AIGateway;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class SummarizationJob {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PersonalityTraitRepository traitRepository;
    private final LongTermMemoryRepository memoryRepository;
    private final TherapyProfileRepository profileRepository;
    private final AIGateway aiGateway;
    private final ObjectMapper objectMapper;

    public SummarizationJob(ConversationRepository conversationRepository,
                            MessageRepository messageRepository,
                            PersonalityTraitRepository traitRepository,
                            LongTermMemoryRepository memoryRepository,
                            TherapyProfileRepository profileRepository,
                            AIGateway aiGateway,
                            ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.traitRepository = traitRepository;
        this.memoryRepository = memoryRepository;
        this.profileRepository = profileRepository;
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
    }

    @Async
    @EventListener
    public void processUnsummarizedSessions(SessionEndedEvent event) {
        Conversation conv = conversationRepository.findById(event.conversationId())
                .orElse(null);
                
        if (conv != null && "completed".equals(conv.getStatus()) && conv.getSummarizedAt() == null) {
            try {
                // For final end-of-session summary: save memories = true, markCompleted = true
                summarizeConversation(conv, true, true);
            } catch (Exception e) {
                // In production, log warning and let it retry next cycle or mark as failed
                // log.warn("Failed to summarize conversation {}", conv.getId(), e);
            }
        }
    }

    public void extractActiveTraitsAndProfile(Conversation conv) {
        // For mid-session targeted refresh: save memories = false, markCompleted = false
        summarizeConversation(conv, false, false);
    }

    private void summarizeConversation(Conversation conv, boolean saveMemories, boolean markCompleted) {
        List<Message> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId());
        
        if (history.isEmpty()) {
            if (markCompleted) {
                conv.setSummarizedAt(Instant.now());
                conversationRepository.save(conv);
            }
            return;
        }

        StringBuilder transcript = new StringBuilder();
        for (Message m : history) {
            transcript.append(m.getRole().toUpperCase()).append(": ").append(m.getContent()).append("\n\n");
        }

        String rawAiResponse = aiGateway.summarizeSession(transcript.toString());
        
        String jsonPayload = cleanJsonResponse(rawAiResponse);
        
        try {
            SummaryExtraction extraction = objectMapper.readValue(jsonPayload, SummaryExtraction.class);
            
            if (markCompleted) {
                conv.setSessionSummary(extraction.getSummary());
            }

            if (extraction.getProfileUpdates() != null) {
                SummaryExtraction.ProfileUpdates updates = extraction.getProfileUpdates();
                profileRepository.findByUserId(conv.getUser().getId()).ifPresent(profile -> {
                    boolean changed = false;
                    if (updates.getName() != null && !updates.getName().isBlank()) {
                        // preferred_name is TEXT/encrypted — no length limit needed
                        profile.setPreferredName(updates.getName().trim());
                        changed = true;
                    }
                    if (updates.getAge() != null && !updates.getAge().isBlank()) {
                        // age_range is VARCHAR(50) — truncate defensively
                        profile.setAgeRange(truncate(updates.getAge().trim(), 50));
                        changed = true;
                    }
                    if (changed) {
                        profileRepository.save(profile);
                    }
                });
            }

            if (extraction.getTraits() != null) {
                List<PersonalityTrait> existingTraits = traitRepository.findByUserIdOrderByConfidenceDesc(conv.getUser().getId());
                for (SummaryExtraction.TraitExtraction t : extraction.getTraits()) {
                    if (t.getKey() == null || t.getKey().isBlank()) continue;
                    String normalizedKey = t.getKey().trim().toLowerCase();
                    
                    PersonalityTrait pt = existingTraits.stream()
                            .filter(existing -> existing.getTraitKey() != null && existing.getTraitKey().trim().toLowerCase().equals(normalizedKey))
                            .findFirst()
                            .orElseGet(() -> PersonalityTrait.builder()
                                    .user(conv.getUser())
                                    .traitKey(t.getKey().trim())
                                    .build());
                    
                    pt.setConfidence(t.getConfidence());
                    pt.setSource("Session " + conv.getStartedAt().toString().substring(0, 10));
                    traitRepository.save(pt);
                }
            }

            if (saveMemories && extraction.getMemories() != null) {
                List<LongTermMemory> existingMemories = memoryRepository.findByUserId(conv.getUser().getId());
                for (SummaryExtraction.MemoryExtraction m : extraction.getMemories()) {
                    if (m.getTitle() == null || m.getTitle().isBlank()) continue;
                    LocalDate followUp = null;
                    if (m.getFollowUpAt() != null) {
                        try {
                            followUp = LocalDate.parse(m.getFollowUpAt());
                        } catch (DateTimeParseException ignored) {}
                    }
                    
                    String normalizedTitle = m.getTitle().trim().toLowerCase();
                    LongTermMemory ltm = existingMemories.stream()
                            .filter(existing -> existing.getTitle() != null && existing.getTitle().trim().toLowerCase().equals(normalizedTitle))
                            .findFirst()
                            .orElseGet(() -> LongTermMemory.builder()
                                    .user(conv.getUser())
                                    .title(m.getTitle().trim())
                                    .occurredAt(LocalDate.now())
                                    .isResolved(false)
                                    .build());

                    ltm.setMemoryType(m.getType() != null ? m.getType() : "fact");
                    ltm.setDetail(m.getDetail());
                    ltm.setImportance(m.getImportance() > 0 ? m.getImportance() : 5);
                    ltm.setFollowUpAt(followUp);
                    memoryRepository.save(ltm);
                }
            }
            
        } catch (Exception e) {
            if (markCompleted) {
                conv.setSessionSummary("Automated extraction failed. Raw output: " + rawAiResponse);
            }
        }
        
        if (markCompleted) {
            conv.setSummarizedAt(Instant.now());
            conversationRepository.save(conv);
        }
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
