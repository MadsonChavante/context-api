package com.contextapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiService {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    private static final String PROMPT_TEMPLATE = "Voce e um assistente de aprendizado de ingles descontraido e direto ao ponto.\n\n"
            + "O usuario quer aprender ingles ou tirar duvidas com base neste contexto:\n"
            + "\"%s\"\n\n"
            + "Responda em no maximo 2-3 linhas, em portugues, dizendo apenas o que entendeu do contexto.\n\n"
            + "Regras:\n"
            + "- Apenas diga o que entendeu do contexto, sem explicar ou aprofundar\n"
            + "- Mantenha o foco em ingles com base no contexto do usuario\n"
            + "- Nao fuja do tema\n"
            + "- Nao faca perguntas nem convide para continuar a conversa\n"
            + "- Se houver termos em ingles faca a traducao de forma natural para portugues na resposta\n"
            + "- Se houver termos em portugues faca a traducao de forma natural para ingles na resposta\n"
            + "- Nada de saudacoes ou introducoes";

    public AiService(
            WebClient groqWebClient,
            @Value("${groq.api.key:}") String apiKey,
            @Value("${groq.model}") String model) {
        this.webClient = groqWebClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String analyze(String content) {
        String sanitizedContent = sanitizeForPromptInjection(content);
        String prompt = String.format(PROMPT_TEMPLATE, sanitizedContent);
        return complete(prompt, 300, 0.7);
    }

    public String complete(String prompt, int maxTokens, double temperature) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GROQ_API_KEY not configured, skipping AI completion");
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
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
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
            log.error("Failed to get AI completion from Groq: {}", e.getMessage());
        }

        return null;
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
