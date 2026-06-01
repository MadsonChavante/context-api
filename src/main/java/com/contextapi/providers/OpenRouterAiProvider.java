package com.contextapi.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
public class OpenRouterAiProvider implements AiProvider {

    private static final String OPENROUTER_API_BASE = "https://openrouter.ai/api/v1/chat/completions";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public OpenRouterAiProvider(WebClient webClient, String apiKey, String model) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String complete(String prompt, int maxTokens, double temperature) {
        if (!isConfigured()) {
            log.warn("OpenRouter API key not configured, skipping AI completion");
            return null;
        }

        try {
            Map<String, Object> message = Map.of("role", "user", "content", prompt);
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(message),
                    "max_tokens", maxTokens,
                    "temperature", temperature);

            Map<?, ?> response = webClient.post()
                    .uri(OPENROUTER_API_BASE)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(err -> {
                        log.error("OpenRouter API error: {}", err.getMessage());
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
            log.error("Failed to get AI completion from OpenRouter: {}", e.getMessage(), e);
        }

        return null;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getProviderName() {
        return "OpenRouter";
    }
}