package com.hkv.AiTherapy.service.chat;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ConversationGenerationCancellationListener {
    private final ObjectProvider<ChatTurnWorkflow> workflows;

    public ConversationGenerationCancellationListener(ObjectProvider<ChatTurnWorkflow> workflows) {
        this.workflows = workflows;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void cancelAfterCommit(ConversationEndedEvent event) {
        ChatTurnWorkflow workflow = workflows.getIfAvailable();
        if (workflow != null) workflow.cancelConversation(event.userId(), event.conversationId());
    }
}
