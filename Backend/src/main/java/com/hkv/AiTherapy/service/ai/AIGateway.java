package com.hkv.AiTherapy.service.ai;

import com.hkv.AiTherapy.dto.ai.AiMessage;

import java.util.List;

public interface AIGateway {
    
    /**
     * Conducts a synchronous chat completion call.
     * @param contextMessages The context windows (System + past messages)
     * @return The AI's text response
     */
    String generateChatResponse(List<AiMessage> contextMessages);

    /** PRO-aware variant — uses pro model if isPro=true. */
    String generateChatResponse(List<AiMessage> contextMessages, boolean isPro);
    
    /**
     * Steams the chat response asynchronously.
     */
    void streamChatResponse(List<AiMessage> contextMessages, java.util.function.Consumer<String> onNext, Runnable onComplete, java.util.function.Consumer<Throwable> onError);

    /** PRO-aware variant. */
    void streamChatResponse(List<AiMessage> contextMessages, boolean isPro, java.util.function.Consumer<String> onNext, Runnable onComplete, java.util.function.Consumer<Throwable> onError);

    /**
     * Extracts a summary and key traits from a conversation transcript.
     * @param sessionTranscript The entire transcript in string format
     * @return A structured JSON-like summary
     */
    String summarizeSession(String sessionTranscript);
}
