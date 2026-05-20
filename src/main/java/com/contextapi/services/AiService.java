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

    private static final String PROMPT_TEMPLATE = "Você é um assistente de aprendizado de inglês descontraído e direto ao ponto.\n\n"
            +
            "O usuário quer aprender inglês ou tirar dúvidas com base neste contexto:\n" +
            "\"%s\"\n\n" +
            "Responda em no máximo 2-3 linhas, em português, dizendo apenas o que entendeu do contexto.\n\n" +
            "Regras:\n" +
            "- Apenas diga o que entendeu do contexto, sem explicar ou aprofundar\n" +
            "- Mantenha o foco em inglês com base no contexto do usuário\n" +
            "- Não fuja do tema\n" +
            "- Não faça perguntas nem convide para continuar a conversa\n" +
            "- Se houver termos em inglês faça a tradução de forma natural para portugues na resposta\n" +
            "- Se houver termos em português faça a tradução de forma natural para inglês na resposta\n" +
            "- Nada de saudações ou introduções";

    public AiService(
            WebClient groqWebClient,
            @Value("${groq.api.key}") String apiKey,
            @Value("${groq.model}") String model) {
        this.webClient = groqWebClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String analyze(String content) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GROQ_API_KEY not configured, skipping AI analysis");
            return null;
        }

        try {
            // Sanitizar conteúdo contra prompt injection
            String sanitizedContent = sanitizeForPromptInjection(content);
            String prompt = String.format(PROMPT_TEMPLATE, sanitizedContent);

            Map<String, Object> message = Map.of("role", "user", "content", prompt);
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(message),
                    "max_tokens", 300,
                    "temperature", 0.7);

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
            log.error("Failed to get AI analysis from Groq: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Sanitiza conteúdo para prevenir prompt injection
     * Remove ou escapa caracteres perigosos que poderiam interferir no prompt
     */
    private String sanitizeForPromptInjection(String content) {
        if (content == null) {
            return "";
        }

        // Remover quebras de linha múltiplas e caracteres de controle perigosos
        String sanitized = content
                .replaceAll("\\n\\n+", "\n") // Múltiplas quebras de linha
                .replaceAll("[\\u0000-\\u001F\\u007F]", "") // Caracteres de controle
                .trim();

        // Limitar tamanho para segurança adicional
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000);
        }

        return sanitized;
    }
}
