package com.hkv.AiTherapy.service.job;

import java.util.UUID;

public record SessionEndedEvent(UUID conversationId) {
}
