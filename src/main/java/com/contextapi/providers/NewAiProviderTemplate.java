package com.contextapi.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
public class NewAiProviderTemplate implements AiProvider {

    private static final String API_ENDPOINT = "https://api.example.com/v1/chat/completions";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public NewAiProviderTemplate(WebClient webClient, String apiKey, String model) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String complete(String prompt, int maxTokens, double temperature) {
        if (!isConfigured()) {
            log.warn("{} provider not configured, skipping AI completion", getProviderName());
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
                    .uri(API_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(err -> {
                        log.error("{} API error: {}", getProviderName(), err.getMessage());
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
            log.error("Failed to get AI completion from {}: {}", getProviderName(), e.getMessage(), e);
        }

        return null;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getProviderName() {
        return "NewProvider";
    }
}
