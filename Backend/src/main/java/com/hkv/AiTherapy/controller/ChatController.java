package com.hkv.AiTherapy.controller;

import com.hkv.AiTherapy.dto.request.ChatMessageRequest;
import com.hkv.AiTherapy.dto.response.ApiResponse;
import com.hkv.AiTherapy.dto.response.ChatMessageResponse;
import com.hkv.AiTherapy.dto.response.ConversationResponse;
import com.hkv.AiTherapy.service.chat.ChatService;
import com.hkv.AiTherapy.repository.ExtractionJobRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import com.hkv.AiTherapy.service.chat.ChatTurnWorkflow;

@RestController
@RequestMapping("/chat/sessions")
public class ChatController {

    private final ChatService chatService;
    private final com.hkv.AiTherapy.repository.ConversationRepository conversationRepository;
    private final ObjectProvider<ChatTurnWorkflow> chatWorkflow;
    private final ExtractionJobRepository extractionJobs;

    public ChatController(ChatService chatService,
                          com.hkv.AiTherapy.repository.ConversationRepository conversationRepository,
                          ObjectProvider<ChatTurnWorkflow> chatWorkflow,
                          ExtractionJobRepository extractionJobs) {
        this.chatService = chatService;
        this.conversationRepository = conversationRepository;
        this.chatWorkflow = chatWorkflow;
        this.extractionJobs = extractionJobs;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> startSession(@AuthenticationPrincipal String userId,
                                                                          @RequestHeader(value = "X-Chat-Request-Version", required = false) String version,
                                                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (version != null) {
            requireV2(version, "X-Chat-Request-Version");
            return ResponseEntity.ok(ApiResponse.success(workflow().startSession(UUID.fromString(userId), idempotencyKey)));
        }
        ConversationResponse response = workflow().startSession(UUID.fromString(userId), effectiveKey(idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(@AuthenticationPrincipal String userId,
                                                                        @PathVariable UUID sessionId,
                                                                        @RequestHeader(value = "X-Chat-Request-Version", required = false) String version,
                                                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                                        @Valid @RequestBody ChatMessageRequest request) {
        if (version != null) {
            requireV2(version, "X-Chat-Request-Version");
            ChatMessageResponse response = workflow().submit(UUID.fromString(userId), sessionId, idempotencyKey, request.getContent());
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        ChatMessageResponse response = workflow().submit(UUID.fromString(userId), sessionId,
                effectiveKey(idempotencyKey), request.getContent());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{sessionId}/end")
    public ResponseEntity<ApiResponse<Void>> endSession(@AuthenticationPrincipal String userId,
                                                        @PathVariable UUID sessionId) {
        chatService.endSession(UUID.fromString(userId), sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{sessionId}/refresh-profile")
    public ResponseEntity<ApiResponse<ExtractionStatus>> refreshProfileMidSession(@AuthenticationPrincipal String userId,
                                                                      @PathVariable UUID sessionId) {
        UUID owner = UUID.fromString(userId);
        ownedConversation(owner, sessionId);
        chatService.endSession(owner, sessionId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(extractionStatus(sessionId)));
    }

    @GetMapping("/{sessionId}/extraction-status")
    public ResponseEntity<ApiResponse<ExtractionStatus>> extractionStatus(@AuthenticationPrincipal String userId,
                                                                           @PathVariable UUID sessionId) {
        ownedConversation(UUID.fromString(userId), sessionId);
        return ResponseEntity.ok(ApiResponse.success(extractionStatus(sessionId)));
    }

    private void ownedConversation(UUID userId, UUID sessionId) {
        var conversation = conversationRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.getUser().getId().equals(userId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
    }

    private ExtractionStatus extractionStatus(UUID sessionId) {
        return extractionJobs.findFirstByConversationIdOrderByCreatedAtDesc(sessionId)
                .map(job -> new ExtractionStatus(job.getStatus()))
                .orElseGet(() -> new ExtractionStatus("NOT_QUEUED"));
    }

    public record ExtractionStatus(String status) { }

    @PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamMessage(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Chat-Stream-Version", required = false) String version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid ChatMessageRequest request,
            Authentication authentication) {
        UUID userId = UUID.fromString((String) authentication.getPrincipal());

        if (version != null) {
            requireV2(version, "X-Chat-Stream-Version");
            var stream = workflow().stream(userId, id, idempotencyKey, request.getContent());
            return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                    .cacheControl(CacheControl.noStore()).header("X-Request-Id", stream.requestId())
                    .body(stream.emitter());
        }

        // Capture SecurityContext before handing off to async thread
        // org.springframework.security.core.context.SecurityContext context =
        //         org.springframework.security.core.context.SecurityContextHolder.getContext();

        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "This stream API version is no longer enabled. Update the client to use stream version 2.");
    }

    private ChatTurnWorkflow workflow() {
        ChatTurnWorkflow workflow = chatWorkflow.getIfAvailable();
        if (workflow == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Version 2 chat is not enabled on this server.");
        return workflow;
    }

    private static void requireV2(String version, String header) {
        if (!"2".equals(version)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, header + " must be 2.");
    }

    private static ResponseStatusException legacyChatDisabled(String header) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "This chat API version is no longer enabled. Update the client to use version 2 with " + header + ".");
    }

    private static String effectiveKey(String supplied) {
        return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
    }
}
