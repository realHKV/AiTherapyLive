package com.hkv.AiTherapy.service.job;

import com.hkv.AiTherapy.domain.Message;
import com.hkv.AiTherapy.dto.ai.SummaryExtraction;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strictly validates structured model output and verifies every proposed fact against a user message. */
@Component
public class SummaryExtractionValidator {
    private static final Set<String> PROFILE_FIELDS = Set.of("name", "age_range", "gender");
    private static final Set<String> MEMORY_TYPES = Set.of("fact", "life_event", "event", "goal", "relationship", "preference");
    private final ObjectMapper mapper;

    public SummaryExtractionValidator(ObjectMapper mapper) { this.mapper = mapper; }

    public ValidatedExtraction validate(String raw, List<Message> transcript) {
        if (raw == null || raw.length() > 30_000) throw invalid();
        final SummaryExtraction extraction;
        String json = raw.strip();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            if (firstNewline < 0 || !json.endsWith("```")) throw invalid();
            String language = json.substring(3, firstNewline).strip();
            if (!language.isEmpty() && !"json".equalsIgnoreCase(language)) throw invalid();
            json = json.substring(firstNewline + 1, json.length() - 3).strip();
        }
        try { extraction = mapper.readValue(json, SummaryExtraction.class); }
        catch (RuntimeException e) { throw invalid(); }
        if (extraction == null || blank(extraction.getSummary()) || extraction.getSummary().length() > 2000) throw invalid();

        Map<UUID, Message> userMessages = transcript.stream()
                .filter(message -> "user".equals(message.getRole()) && message.getId() != null)
                .collect(java.util.stream.Collectors.toMap(Message::getId, message -> message, (first, ignored) -> first));
        List<ValidatedCandidate> candidates = new ArrayList<>();
        Set<String> fields = new HashSet<>();
        int outputIndex = 0;

        List<SummaryExtraction.ProfileCandidateExtraction> profiles = extraction.getProfileCandidates() == null
                ? List.of() : extraction.getProfileCandidates();
        List<SummaryExtraction.TraitExtraction> traits = extraction.getTraits() == null ? List.of() : extraction.getTraits();
        List<SummaryExtraction.MemoryExtraction> memories = extraction.getMemories() == null ? List.of() : extraction.getMemories();
        if (profiles.size() > 10 || traits.size() > 3 || memories.size() > 20) throw invalid();
        int rejected = 0;

        for (SummaryExtraction.ProfileCandidateExtraction candidate : profiles) {
            try {
                String field = candidate == null || candidate.getField() == null ? "" : candidate.getField().trim().toLowerCase(Locale.ROOT);
                int max = "name".equals(field) ? 100 : 50;
                if (!PROFILE_FIELDS.contains(field) || blank(candidate.getValue()) || candidate.getValue().length() > max)
                    throw invalid();
                Evidence proof = evidence(candidate.getSourceMessageId(), candidate.getEvidence(), userMessages);
                if (!fields.add(field)) throw invalid();
                candidates.add(new ValidatedCandidate(outputIndex++, field, candidate.getValue().trim(), proof));
            } catch (IllegalArgumentException ignored) { rejected++; }
        }
        for (SummaryExtraction.TraitExtraction trait : traits) {
            try {
                if (trait == null || blank(trait.getKey()) || trait.getKey().trim().length() > 80
                        || blank(trait.getValue()) || trait.getValue().length() > 500 || trait.getConfidence() == null
                        || !Double.isFinite(trait.getConfidence()) || trait.getConfidence() < 0 || trait.getConfidence() > 1)
                    throw invalid();
                String field = "trait:" + trait.getKey().trim().toLowerCase(Locale.ROOT);
                Evidence proof = evidence(trait.getSourceMessageId(), trait.getEvidence(), userMessages);
                if (!fields.add(field)) throw invalid();
                candidates.add(new ValidatedCandidate(outputIndex++, field,
                        trait.getKey().trim() + ": " + trait.getValue().trim(), trait.getConfidence(), proof));
            } catch (IllegalArgumentException ignored) { rejected++; }
        }
        for (SummaryExtraction.MemoryExtraction memory : memories) {
            try {
                if (memory == null || blank(memory.getTitle()) || memory.getTitle().trim().length() > 200
                        || (memory.getDetail() != null && memory.getDetail().length() > 2000)
                        || !MEMORY_TYPES.contains(memory.getType()) || memory.getImportance() < 1 || memory.getImportance() > 10)
                    throw invalid();
                LocalDate followUp = null;
                if (!blank(memory.getFollowUpAt())) {
                    try { followUp = LocalDate.parse(memory.getFollowUpAt()); }
                    catch (RuntimeException invalidDate) { throw invalid(); }
                }
                Evidence proof = evidence(memory.getSourceMessageId(), memory.getEvidence(), userMessages);
                candidates.add(new ValidatedCandidate(outputIndex++, "memory", memory.getTitle().trim(),
                        null, proof, memory.getType().equals("event") ? "life_event" : memory.getType(),
                        blank(memory.getDetail()) ? null : memory.getDetail().trim(), memory.getImportance(), followUp));
            } catch (IllegalArgumentException ignored) { rejected++; }
        }
        if (rejected > 0 && candidates.isEmpty()) throw invalid();
        return new ValidatedExtraction(extraction.getSummary().trim(), List.copyOf(candidates));
    }

    private static Evidence evidence(String sourceId, String quote, Map<UUID, Message> messages) {
        if (blank(sourceId) || blank(quote) || quote.length() > 500) throw invalid();
        UUID id;
        try { id = UUID.fromString(sourceId); }
        catch (RuntimeException e) { throw invalid(); }
        Message source = messages.get(id);
        if (source == null || !normalize(source.getContent()).contains(normalize(quote))) throw invalid();
        return new Evidence(source, quote.trim());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid or unsupported extraction output"); }

    public record Evidence(Message message, String quote) { }
    public record ValidatedCandidate(int outputIndex, String field, String value, Double confidence, Evidence evidence,
                                     String memoryType, String detail, Integer importance, LocalDate followUpAt) {
        public ValidatedCandidate(int outputIndex, String field, String value, Evidence evidence) {
            this(outputIndex, field, value, null, evidence, null, null, null, null);
        }
        public ValidatedCandidate(int outputIndex, String field, String value, Double confidence, Evidence evidence) {
            this(outputIndex, field, value, confidence, evidence, null, null, null, null);
        }
    }
    public record ValidatedExtraction(String summary, List<ValidatedCandidate> candidates) { }
}
