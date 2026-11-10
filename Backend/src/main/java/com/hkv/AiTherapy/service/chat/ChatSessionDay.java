package com.hkv.AiTherapy.service.chat;

import com.hkv.AiTherapy.domain.Conversation;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.DateTimeException;

/** A conversation may be resumed only on the day it began in the visitor's time zone. */
final class ChatSessionDay {
    private ChatSessionDay() {}

    static boolean isToday(Conversation conversation, String timeZone) {
        if (conversation.getStartedAt() == null) return false;
        ZoneId zone;
        try {
            zone = timeZone == null || timeZone.isBlank() ? ZoneOffset.UTC : ZoneId.of(timeZone);
        } catch (DateTimeException ex) {
            zone = ZoneOffset.UTC;
        }
        return conversation.getStartedAt().atZone(zone).toLocalDate()
                .equals(Instant.now().atZone(zone).toLocalDate());
    }
}
