package com.hkv.AiTherapy.controller;

import com.hkv.AiTherapy.dto.response.ApiResponse;
import com.hkv.AiTherapy.dto.response.ChatRequestStatusResponse;
import com.hkv.AiTherapy.service.chat.ChatTurnWorkflow;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/chat/requests")
public class ChatRequestController {
    private final ObjectProvider<ChatTurnWorkflow> workflow;

    public ChatRequestController(ObjectProvider<ChatTurnWorkflow> workflow) { this.workflow = workflow; }

    @GetMapping("/{requestId}")
    public ApiResponse<ChatRequestStatusResponse> status(@AuthenticationPrincipal String userId,
                                                         @PathVariable UUID requestId) {
        ChatTurnWorkflow active = workflow.getIfAvailable();
        if (active == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Version 2 chat is not enabled on this server.");
        return ApiResponse.success(active.status(UUID.fromString(userId), requestId));
    }
}
