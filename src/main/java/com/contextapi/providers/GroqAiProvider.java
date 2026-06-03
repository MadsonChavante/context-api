package com.contextapi.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
public class GroqAiProvider implements AiProvider {

    private static final String GROQ_API_BASE = "https://api.groq.com/openai/v1/chat/completions";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public GroqAiProvider(WebClient webClient, String apiKey, String model) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String complete(String prompt, int maxTokens, double temperature) {
        return complete(prompt, maxTokens, temperature, false);
    }

    @Override
    public String complete(String prompt, int maxTokens, double temperature, boolean jsonMode) {
        if (!isConfigured()) {
            log.warn("Groq API key not configured, skipping AI completion");
            return null;
        }

        try {
            Map<String, Object> message = Map.of("role", "user", "content", prompt);
            
            Map<String, Object> requestBodyBuilder = new java.util.HashMap<>();
            requestBodyBuilder.put("model", model);
            requestBodyBuilder.put("messages", List.of(message));
            requestBodyBuilder.put("max_tokens", maxTokens);
            requestBodyBuilder.put("temperature", temperature);
            
            if (jsonMode) {
                requestBodyBuilder.put("response_format", Map.of("type", "json_object"));
                log.debug("Groq request with JSON mode enabled");
            }
            
            Map<?, ?> response = webClient.post()
                    .uri(GROQ_API_BASE)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBodyBuilder)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(err -> {
                        log.error("Groq API error: {}", err.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response != null) {
                List<?> choices = (List<?>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                    Map<?, ?> msg = (Map<?, ?>) choice.get("message");
                    if (msg != null) {
                        return (String) msg.get("content");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get AI completion from Groq: {}", e.getMessage(), e);
        }

        return null;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getProviderName() {
        return "Groq";
    }
}
