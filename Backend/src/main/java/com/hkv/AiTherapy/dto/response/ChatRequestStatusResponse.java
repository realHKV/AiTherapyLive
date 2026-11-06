package com.hkv.AiTherapy.dto.response;

import com.hkv.AiTherapy.service.ai.routing.GenerationUsage;

public record ChatRequestStatusResponse(String requestId, String sessionId, String status,
                                        String failureCode, ChatMessageResponse assistantMessage,
                                        GenerationUsage usage) { }
