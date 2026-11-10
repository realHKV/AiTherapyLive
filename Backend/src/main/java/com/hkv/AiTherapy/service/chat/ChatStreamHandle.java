package com.hkv.AiTherapy.service.chat;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public record ChatStreamHandle(String requestId, SseEmitter emitter) { }
