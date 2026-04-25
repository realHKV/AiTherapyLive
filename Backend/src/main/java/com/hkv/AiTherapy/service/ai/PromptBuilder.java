package com.hkv.AiTherapy.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hkv.AiTherapy.domain.LongTermMemory;
import com.hkv.AiTherapy.domain.PersonalityTrait;
import com.hkv.AiTherapy.domain.TherapyProfile;
import com.hkv.AiTherapy.repository.LongTermMemoryRepository;
import com.hkv.AiTherapy.repository.PersonalityTraitRepository;
import com.hkv.AiTherapy.repository.TherapyProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PromptBuilder {

    private final TherapyProfileRepository profileRepository;
    private final PersonalityTraitRepository traitRepository;
    private final LongTermMemoryRepository memoryRepository;
    private final ObjectMapper objectMapper;

    public PromptBuilder(TherapyProfileRepository profileRepository,
                         PersonalityTraitRepository traitRepository,
                         LongTermMemoryRepository memoryRepository,
                         ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.traitRepository = traitRepository;
        this.memoryRepository = memoryRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds the comprehensive System prompt for the AI therapy session.
     * Injects the user's profile preferences, top personality traits, and key unresolved memories.
     *
     * @param userId The UUID of the user
     * @return The formatted System prompt string
     */
    public String buildSystemPrompt(UUID userId) {
        TherapyProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null) {
            return "You are an empathetic, professional AI therapist. The user has not set up a profile yet. " +
                   "Be supportive, listen actively, and encourage them to express their feelings.";
        }

        // Fetch top 5 personality traits by confidence
        List<PersonalityTrait> topTraits = traitRepository.findByUserIdOrderByConfidenceDesc(userId)
                .stream().limit(5).toList();

        // Fetch top 5 most important unresolved memories
        List<LongTermMemory> topMemories = memoryRepository.findTopByImportance(userId, 5);

        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Role: AI Therapist.\n");
        
        if (profile.getAiPersona() != null && !profile.getAiPersona().isBlank()) {
            prompt.append("Persona: ").append(profile.getAiPersona()).append("\n");
        }
        
        if (profile.getCommunicationStyle() != null && !profile.getCommunicationStyle().isBlank()) {
            prompt.append("Style: ").append(profile.getCommunicationStyle()).append("\n");
        }

        prompt.append("\n[Patient]\n");
        prompt.append("Name: ").append(profile.getPreferredName() != null ? profile.getPreferredName() : "User").append("\n");
        if (profile.getAgeRange() != null) prompt.append("Age: ").append(profile.getAgeRange()).append("\n");

        if (profile.getTopicsOfConcern() != null && !profile.getTopicsOfConcern().isBlank()) {
            try {
                List<String> topics = objectMapper.readValue(profile.getTopicsOfConcern(), new TypeReference<>() {});
                if (!topics.isEmpty()) {
                    prompt.append("Topics: ").append(String.join(",", topics)).append("\n");
                }
            } catch (JsonProcessingException ignored) {
            }
        }

        if (!topTraits.isEmpty()) {
            prompt.append("\n[Traits]\n");
            for (PersonalityTrait trait : topTraits) {
                prompt.append("- ").append(trait.getTraitKey()).append("\n");
            }
        }

        if (!topMemories.isEmpty()) {
            prompt.append("\n[Memories]\n");
            for (LongTermMemory memory : topMemories) {
                prompt.append("- ").append(memory.getTitle());
                if (memory.getDetail() != null && !memory.getDetail().isBlank()) {
                    prompt.append(": ").append(memory.getDetail());
                }
                prompt.append("\n");
            }
        }

        prompt.append("\n[Rules]\n");
        prompt.append("1. Transparent AI, never claim to be human.\n");
        prompt.append("2. Crisis? Urge immediate professional/hotline help.\n");
        prompt.append("3. Format responses with proper Markdown and newlines. Each numbered list item MUST be on its own line. Always add a blank line between paragraphs and before lists.\n");
        prompt.append("4. Keep responses concise and structured for easy reading in a chat interface.\n");
        prompt.append("5. Validate emotions, ask exploratory questions, avoid immediate solutions.\n");

        return prompt.toString();
    }
}
