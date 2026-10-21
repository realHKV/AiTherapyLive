package com.hkv.AiTherapy.service.ai;

import tools.jackson.databind.ObjectMapper;
import com.hkv.AiTherapy.dto.ai.AiMessage;
import com.hkv.AiTherapy.dto.ai.AiRequest;
import com.hkv.AiTherapy.dto.ai.AiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class OpenAICompatibleGateway implements AIGateway {

    private final String apiUrl;
    private final String apiKey;
    private final String defaultModel;
    
    private final String proApiUrl;
    private final String proApiKey;
    private final String proModel;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAICompatibleGateway(
            @Value("${ai.provider.base-url}") String apiUrl,
            @Value("${ai.provider.api-key}") String apiKey,
            @Value("${ai.provider.model}") String defaultModel,
            @Value("${ai.provider.pro-base-url:${ai.provider.base-url}}") String proApiUrl,
            @Value("${ai.provider.pro-api-key:${ai.provider.api-key}}") String proApiKey,
            @Value("${ai.provider.pro-model:${ai.provider.model}}") String proModel,
            ObjectMapper objectMapper) {

        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        
        this.proApiUrl = proApiUrl;
        this.proApiKey = proApiKey;
        this.proModel = proModel;
        
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String generateChatResponse(List<AiMessage> contextMessages) {
        return generateChatResponse(contextMessages, false);
    }

    @Override
    public String generateChatResponse(List<AiMessage> contextMessages, boolean isPro) {
        String model = isPro ? proModel : defaultModel;
        AiRequest request = AiRequest.builder()
                .model(model)
                .messages(contextMessages)
                .temperature(0.7)
                .maxTokens(1500)
                .build();
        return executeRequest(request, isPro);
    }

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 1000; // 1 second

    private String executeRequest(AiRequest requestBody, boolean isPro) {
        Exception lastException = null;
        String targetUrl = isPro ? proApiUrl : apiUrl;
        String targetKey = isPro ? proApiKey : apiKey;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String jsonPayload = objectMapper.writeValueAsString(requestBody);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl + "/chat/completions"))
                        .header("Authorization", "Bearer " + targetKey)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429) {
                    long waitMs = BASE_BACKOFF_MS * (1L << (attempt - 1)); // 1s, 2s, 4s
                    System.out.printf("AI provider rate-limited (attempt %d/%d). Retrying in %dms...%n",
                            attempt, MAX_RETRIES, waitMs);
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(waitMs);
                        continue;
                    }
                    // All retries exhausted
                    throw new RuntimeException("RATE_LIMIT_EXCEEDED");
                }

                if (response.statusCode() != 200) {
                    throw new RuntimeException("AI provider returned error: " + response.statusCode() + " " + response.body());
                }

                AiResponse aiResponse = objectMapper.readValue(response.body(), AiResponse.class);
                return aiResponse.getResponseText();

            } catch (RuntimeException e) {
                throw e; // propagate non-retryable errors immediately
            } catch (Exception e) {
                lastException = e;
            }
        }

        throw new RuntimeException(lastException != null ? lastException.getMessage() : "Request failed after retries", lastException);
    }

    @Override
    public void streamChatResponse(List<AiMessage> contextMessages, java.util.function.Consumer<String> onNext, Runnable onComplete, java.util.function.Consumer<Throwable> onError) {
        streamChatResponse(contextMessages, false, onNext, onComplete, onError);
    }

    @Override
    public void streamChatResponse(List<AiMessage> contextMessages, boolean isPro, java.util.function.Consumer<String> onNext, Runnable onComplete, java.util.function.Consumer<Throwable> onError) {
        String targetUrl = isPro ? proApiUrl : apiUrl;
        String targetKey = isPro ? proApiKey : apiKey;
        String model = isPro ? proModel : defaultModel;
        
        AiRequest requestBody = AiRequest.builder()
                .model(model)
                .messages(contextMessages)
                .temperature(0.7)
                .maxTokens(1500)
                .stream(true)
                .build();

        try {
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + targetKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() == 429) {
                            onNext.accept("[ERROR: RATE_LIMIT_EXCEEDED]");
                            onComplete.run();
                            return;
                        } else if (response.statusCode() != 200) {
                            onError.accept(new RuntimeException("AI API error: " + response.statusCode()));
                            return;
                        }
                        
                        response.body().forEach(line -> {
                            if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
                                try {
                                    String json = line.substring(6);
                                    tools.jackson.databind.JsonNode node = objectMapper.readTree(json);
                                    tools.jackson.databind.JsonNode contentNode = node.path("choices").get(0).path("delta").path("content");
                                    if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                                        onNext.accept(contentNode.asText());
                                    }
                                } catch (Exception ignored) {}
                            }
                        });
                        onComplete.run();
                    }).exceptionally(ex -> {
                        onError.accept(ex);
                        return null;
                    });
        } catch (Exception e) {
            onError.accept(e);
        }
    }
}
