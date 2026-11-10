package com.hkv.AiTherapy.service.chat;

import com.hkv.AiTherapy.service.ai.routing.GenerationMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hkv.AiTherapy.service.ai.routing.GenerationMessage.Role.ASSISTANT;
import static com.hkv.AiTherapy.service.ai.routing.GenerationMessage.Role.SYSTEM;
import static com.hkv.AiTherapy.service.ai.routing.GenerationMessage.Role.USER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatTurnWorkflowHistoryTest {
    @Test
    void greetingWithoutStoredUserPromptIsOmittedFromFirstNovaTurn() {
        List<GenerationMessage> history = ChatTurnWorkflow.normalizeProviderHistory(List.of(
                new GenerationMessage(SYSTEM, "System prompt"),
                new GenerationMessage(ASSISTANT, "Welcome"),
                new GenerationMessage(USER, "First chat message")));

        assertEquals(List.of(SYSTEM, USER), history.stream().map(GenerationMessage::role).toList());
        assertEquals("First chat message", history.get(1).content());
    }

    @Test
    void failedTurnKeepsBothUserMessagesInOneAlternatingTurn() {
        List<GenerationMessage> history = ChatTurnWorkflow.normalizeProviderHistory(List.of(
                new GenerationMessage(SYSTEM, "System prompt"),
                new GenerationMessage(USER, "First request"),
                new GenerationMessage(USER, "Second request"),
                new GenerationMessage(ASSISTANT, "Response"),
                new GenerationMessage(USER, "Follow up")));

        assertEquals(List.of(SYSTEM, USER, ASSISTANT, USER),
                history.stream().map(GenerationMessage::role).toList());
        assertEquals("First request\nSecond request", history.get(1).content());
    }
}
