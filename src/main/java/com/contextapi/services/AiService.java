package com.contextapi.services;

import com.contextapi.providers.AiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiService {

    private final AiProvider aiProvider;

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

    public AiService(AiProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String analyze(String content) {
        String sanitizedContent = sanitizeForPromptInjection(content);
        String prompt = String.format(PROMPT_TEMPLATE, sanitizedContent);
        return complete(prompt, 300, 0.7);
    }

    public String complete(String prompt, int maxTokens, double temperature) {
        if (!aiProvider.isConfigured()) {
            log.warn("{} provider not configured, skipping AI completion", aiProvider.getProviderName());
            return null;
        }

        log.debug("Sending request to {} provider", aiProvider.getProviderName());
        return aiProvider.complete(prompt, maxTokens, temperature);
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
