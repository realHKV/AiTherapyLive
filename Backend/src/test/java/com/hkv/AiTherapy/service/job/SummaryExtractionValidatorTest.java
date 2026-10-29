package com.hkv.AiTherapy.service.job;

import com.hkv.AiTherapy.domain.Message;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SummaryExtractionValidatorTest {
    private final SummaryExtractionValidator validator = new SummaryExtractionValidator(new ObjectMapper());
    private final UUID sourceId = UUID.randomUUID();
    private final List<Message> transcript = List.of(Message.builder().id(sourceId).role("user")
            .content("I am working toward finishing my nursing course.").build());

    @Test
    void acceptsBoundedCandidatesWithExactUserMessageEvidence() {
        var result = validator.validate("""
                {"summary":"Discussed education goal.",
                 "profileCandidates":[],"traits":[],
                 "memories":[{"type":"goal","title":"Finish nursing course","detail":"A personal goal.",
                 "importance":7,"followUpAt":"2027-04-01","sourceMessageId":"%s",
                 "evidence":"working toward finishing my nursing course"}]}
                """.formatted(sourceId), transcript);

        assertEquals("Discussed education goal.", result.summary());
        assertEquals(1, result.candidates().size());
        assertEquals("goal", result.candidates().getFirst().memoryType());
        assertEquals(sourceId, result.candidates().getFirst().evidence().message().getId());
    }

    @Test
    void rejectsEvidenceThatCannotBeFoundInTheCitedUserMessage() {
        String output = """
                {"summary":"Summary.","memories":[{"type":"fact","title":"Invented detail","importance":5,
                 "sourceMessageId":"%s","evidence":"I own a private island"}]}
                """.formatted(sourceId);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(output, transcript));
    }

    @Test
    void keepsSupportedMemoryWhenAnotherSuggestionHasInvalidEvidence() {
        var result = validator.validate("""
                {"summary":"Discussed education goal.","profileCandidates":[{"field":"name","value":"Alex",
                 "sourceMessageId":"%s","evidence":"My name is Alex"}],"traits":[],
                 "memories":[{"type":"goal","title":"Finish nursing course","importance":7,
                 "sourceMessageId":"%s","evidence":"working toward finishing my nursing course"}]}
                """.formatted(sourceId, sourceId), transcript);
        assertEquals(1, result.candidates().size());
        assertEquals("goal", result.candidates().getFirst().memoryType());
    }

    @Test
    void rejectsInvalidDatesAndOutOfRangeImportance() {
        String output = """
                {"summary":"Summary.","memories":[{"type":"goal","title":"Finish course","importance":11,
                 "followUpAt":"tomorrow","sourceMessageId":"%s","evidence":"nursing course"}]}
                """.formatted(sourceId);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(output, transcript));
    }
}
