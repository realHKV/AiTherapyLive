package com.hkv.AiTherapy.service.memory;

import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class ContextRevisionService {
    private final UserRepository users;
    private final ConversationRepository conversations;

    public ContextRevisionService(UserRepository users, ConversationRepository conversations) {
        this.users = users;
        this.conversations = conversations;
    }

    /** Lock ordering matches the summarization worker's final write transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long advance(UUID userId, UUID sourceConversationId, boolean resetAll) {
        var user = users.findByIdForUpdate(userId)
                .filter(u -> u.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        user.setContextRevision(user.getContextRevision() + 1);
        if (resetAll) user.setContextResetAt(Instant.now());
        users.save(user);

        conversations.invalidateSummaries(userId);
        if (resetAll) conversations.excludeAllFromContext(userId);
        else if (sourceConversationId != null) conversations.excludeFromContext(userId, sourceConversationId);
        return user.getContextRevision();
    }
}
