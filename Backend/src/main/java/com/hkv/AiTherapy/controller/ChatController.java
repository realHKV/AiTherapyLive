package com.hkv.AiTherapy.controller;

import com.hkv.AiTherapy.dto.request.ChatMessageRequest;
import com.hkv.AiTherapy.dto.response.ApiResponse;
import com.hkv.AiTherapy.dto.response.ChatMessageResponse;
import com.hkv.AiTherapy.dto.response.ConversationResponse;
import com.hkv.AiTherapy.service.chat.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/chat/sessions")
public class ChatController {

    private final ChatService chatService;
    private final com.hkv.AiTherapy.service.job.SummarizationJob summarizationJob;
    private final com.hkv.AiTherapy.repository.ConversationRepository conversationRepository;

    public ChatController(ChatService chatService, 
                          com.hkv.AiTherapy.service.job.SummarizationJob summarizationJob,
                          com.hkv.AiTherapy.repository.ConversationRepository conversationRepository) {
        this.chatService = chatService;
        this.summarizationJob = summarizationJob;
        this.conversationRepository = conversationRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> startSession(@AuthenticationPrincipal String userId) {
        ConversationResponse response = chatService.startSession(UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(@AuthenticationPrincipal String userId,
                                                                        @PathVariable UUID sessionId,
                                                                        @Valid @RequestBody ChatMessageRequest request) {
        ChatMessageResponse response = chatService.sendMessage(UUID.fromString(userId), sessionId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{sessionId}/end")
    public ResponseEntity<ApiResponse<Void>> endSession(@AuthenticationPrincipal String userId,
                                                        @PathVariable UUID sessionId) {
        chatService.endSession(UUID.fromString(userId), sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{sessionId}/refresh-profile")
    public ResponseEntity<ApiResponse<Void>> refreshProfileMidSession(@AuthenticationPrincipal String userId,
                                                                      @PathVariable UUID sessionId) {
        com.hkv.AiTherapy.domain.Conversation conv = conversationRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
                
        if (!conv.getUser().getId().equals(UUID.fromString(userId))) {
            throw new SecurityException("Not authorized");
        }
        
        // This will run the AI summarization prompt but NOT mark the session as completed
        // and WILL NOT save duplicate memories. It updates current traits and profile info.
        summarizationJob.extractActiveTraitsAndProfile(conv);
        
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @PathVariable UUID id,
            @RequestBody @Valid ChatMessageRequest request,
            Authentication authentication) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());

        // Capture SecurityContext before handing off to async thread
        // org.springframework.security.core.context.SecurityContext context =
        //         org.springframework.security.core.context.SecurityContextHolder.getContext();

        SseEmitter emitter = chatService.streamMessage(id, userId, request);
        return emitter;
    }
}
