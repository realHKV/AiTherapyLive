package com.hkv.AiTherapy.service.chat;

import java.util.UUID;

public record ConversationEndedEvent(UUID userId, UUID conversationId) { }
