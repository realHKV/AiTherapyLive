package com.hkv.AiTherapy.service.account;

import com.hkv.AiTherapy.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

/**
 * Handles full account deletion — removes ALL user data from every table.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final MessageRepository messageRepository;
    private final LongTermMemoryRepository memoryRepository;
    private final PersonalityTraitRepository traitRepository;
    private final TherapyProfileRepository profileRepository;
    private final ConversationRepository conversationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    public AccountService(MessageRepository messageRepository,
                          LongTermMemoryRepository memoryRepository,
                          PersonalityTraitRepository traitRepository,
                          TherapyProfileRepository profileRepository,
                          ConversationRepository conversationRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          UserRepository userRepository,
                          StringRedisTemplate redisTemplate) {
        this.messageRepository = messageRepository;
        this.memoryRepository = memoryRepository;
        this.traitRepository = traitRepository;
        this.profileRepository = profileRepository;
        this.conversationRepository = conversationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Permanently deletes a user account and ALL associated data.
     * Order matters due to foreign key constraints:
     * 1. Messages (FK → conversations)
     * 2. LongTermMemories (FK → user, FK → conversations)
     * 3. PersonalityTraits (FK → user)
     * 4. TherapyProfile (FK → user)
     * 5. Conversations (FK → user)
     * 6. RefreshTokens (FK → user)
     * 7. User
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        log.warn("Permanently deleting account for userId={}", userId);

        // 1. Messages (depend on conversations which depend on user)
        messageRepository.deleteAllByUserId(userId);

        // 2. Long-term memories (depend on user + optionally conversations)
        memoryRepository.deleteAllByUserId(userId);

        // 3. Personality traits
        traitRepository.deleteAllByUserId(userId);

        // 4. Therapy profile
        profileRepository.deleteByUserId(userId);

        // 5. Conversations
        conversationRepository.deleteAllByUserId(userId);

        // 6. Refresh tokens
        refreshTokenRepository.deleteAllByUserId(userId);

        // 7. Finally, the user record itself
        userRepository.deleteById(userId);

        // 8. Clear Redis rate limit keys or other connected data
        redisTemplate.delete("rl:chat:" + userId.toString());

        log.info("Account deletion complete for userId={}", userId);
    }
}
