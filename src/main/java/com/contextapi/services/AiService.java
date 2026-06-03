package com.contextapi.services;

import com.contextapi.providers.AiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiService {

    private final AiProvider aiProvider;

    public AiService(AiProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String analyze(String prompt, String content) {
        String sanitizedContent = sanitizeForPromptInjection(content);
        String promptComplete = String.format(prompt, sanitizedContent);
        return complete(promptComplete, 300, 0.7);
    }

    public String complete(String prompt, int maxTokens, double temperature) {
        return complete(prompt, maxTokens, temperature, false);
    }

    public String complete(String prompt, int maxTokens, double temperature, boolean jsonMode) {
        if (!aiProvider.isConfigured()) {
            log.warn("{} provider not configured, skipping AI completion", aiProvider.getProviderName());
            return null;
        }

        log.debug("Sending request to {} provider (jsonMode={})", aiProvider.getProviderName(), jsonMode);
        return aiProvider.complete(prompt, maxTokens, temperature, jsonMode);
    }

    public String getProviderName() {
        return aiProvider.getProviderName();
    }

    private String sanitizeForPromptInjection(String content) {
        if (content == null) {
            return "";
        }

        String sanitized = content
                .replaceAll("\\n\\n+", "\n")
                .replaceAll("[\\u0000-\\u001F\\u007F]", "")
                .trim();

        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000);
        }

        return sanitized;
    }
}
