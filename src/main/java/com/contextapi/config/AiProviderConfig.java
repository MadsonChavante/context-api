package com.contextapi.config;

import com.contextapi.providers.AiProvider;
import com.contextapi.providers.GroqAiProvider;
import com.contextapi.providers.OpenRouterAiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiProviderConfig {

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String groqModel;

    @Value("${openrouter.api.key:}")
    private String openRouterApiKey;

    @Value("${openrouter.model:openai/gpt-4o}")
    private String openRouterModel;

    @Value("${ai.provider:groq}")
    private String activeProvider;

    @Bean
    public AiProvider aiProvider(WebClient webClient) {
        return switch (activeProvider.toLowerCase()) {
            case "openrouter" -> new OpenRouterAiProvider(webClient, openRouterApiKey, openRouterModel);
            default -> new GroqAiProvider(webClient, groqApiKey, groqModel);
        };
    }
}

