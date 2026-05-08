package com.hkv.AiTherapy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private String id;
    private String status;
    private int tokenCount;
    private Instant startedAt;
    
    // Included when returning a fresh session with its initial greeting
    private List<ChatMessageResponse> recentMessages;
}
