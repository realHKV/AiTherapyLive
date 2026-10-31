package com.hkv.AiTherapy.service.ai;

import com.hkv.AiTherapy.dto.ai.AiMessage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.function.Consumer;

/** Fails closed for the pre-accounting API. All production generation must use the Bedrock v2 workflow. */
@Component
public class DisabledLegacyAIGateway implements AIGateway {
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "This chat API version is no longer enabled. Update the client to use chat version 2.");
    }

    @Override public String generateChatResponse(List<AiMessage> messages) { throw unavailable(); }
    @Override public String generateChatResponse(List<AiMessage> messages, boolean isPro) { throw unavailable(); }

    @Override public void streamChatResponse(List<AiMessage> messages, Consumer<String> onNext,
                                             Runnable onComplete, Consumer<Throwable> onError) {
        onError.accept(unavailable());
    }

    @Override public void streamChatResponse(List<AiMessage> messages, boolean isPro, Consumer<String> onNext,
                                             Runnable onComplete, Consumer<Throwable> onError) {
        onError.accept(unavailable());
    }
}
