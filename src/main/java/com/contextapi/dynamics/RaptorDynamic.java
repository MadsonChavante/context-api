package com.contextapi.dynamics;

import com.contextapi.entities.ConversationMessage;
import com.contextapi.entities.ConversationMessage.MessageType;
import com.contextapi.dtos.LessonDTO;
import com.contextapi.entities.Context;
import com.contextapi.entities.ContextStats;
import com.contextapi.enums.ConversationAuthor;
import com.contextapi.records.ClassificationResult;
import com.contextapi.records.EvaluationResult;
import com.contextapi.records.HandleAnswerResult;
import com.contextapi.records.ResponseIAResult;
import com.contextapi.records.NextExerciseResult;
import com.contextapi.services.AiService;
import com.contextapi.services.ContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RaptorDynamic {

    private final AiService aiService;
    private final ContextService contextService;
    private static final Pattern JSON_EXTRACT_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    public static final String intro = "Opa, vamos praticar! Que ótimo. Bora conversar então." +
            "Vou falar uma frase em portugues e quero que voce traduza para o ingles. " +
            "Se tiver duvidas, pode perguntar, ok?";

    public RaptorDynamic(AiService aiService, ContextService contextService) {
        this.aiService = aiService;
        this.contextService = contextService;
    }


    public ResponseIAResult startLesson() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("""

                    Escolha o contexto que MAIS PRECISA de pratica (menor media ou nao praticado).
                    Crie uma frase NATURAL e UTIL. Use VARIACOES — mude palavras, tempo verbal, ou situacao.

                        Responda SOMENTE com JSON valido:
                        {
                            "contextId": <id do contexto escolhido>,
                            "response": "frase natural em portugues baseada no contexto",
                        }
                """);

        String content = sb.toString();
        String prompt = buildPrompt(content);

        String responseAiService = aiService.complete(prompt, 800, 0.6);

        if (responseAiService == null || responseAiService.trim().isEmpty()) {
            log.error("AI service returned null or empty response");
            throw new RuntimeException("Failed to get response from AI service");
        }

        try {
            String cleanedJson = extractJsonFromResponse(responseAiService);
            JsonNode node = new ObjectMapper().readTree(cleanedJson);

            return new ResponseIAResult(node.get("contextId").asLong(), node.get("response").asText());
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", responseAiService, e);
            throw e;
        }
    }

    public String startVoice(List<ConversationMessage> conversationHistory) {
        return "traduza para o ingles: " + conversationHistory.get(conversationHistory.size() - 1).getContent();
    }

    public HandleAnswerResult handleAnswer(String answer, List<ConversationMessage> conversationHistory) throws Exception{

        StringBuilder content = new StringBuilder();
        content.append("O aluno respondeu: ").append(answer).append("\n");
        content.append("""
                    Analise a resposta do aluno,
                    classifique se é uma resposta ao pedido de traducao, uma duvida, ou ruido.
                    Considere RUIDO quando a resposta nao tiver relacao com o contexto da aula e nao for uma duvida legitima
                    (ex: sons, palavras aleatorias, frases sem sentido, silencio transcrito).

                    Se for ANSWER, avalie se a resposta esta correta.
                    Se for DOUBT, responda a duvida de forma clara e objetiva, sem rodeios, focando na duvida do aluno, sem aprofundar muito e sem continuar a conversa.
                    Se for NOISE, o campo "response" deve ser uma instrucao curta pedindo para o aluno repetir (ex: "Nao entendi, pode repetir?").

                    para proxima frase escolha o contexto que MAIS PRECISA de pratica (menor media ou nao praticado).
                    Crie uma frase NATURAL e UTIL. Use VARIACOES — mude palavras, tempo verbal, ou situacao.
                    Se o aluno estiver com duvida, priorize a mesma frase ou uma variacao simples dela, para ajudar o aluno a aprender.
                    Se for NOISE, mantenha o mesmo nextContextId e next da frase anterior.

                    FORMATO DE RESPOSTA — INSTRUCOES CRITICAS:
                    - Voce deve responder EXCLUSIVAMENTE com um objeto JSON.
                    - NAO escreva nenhum texto antes ou depois do JSON.
                    - NAO use markdown, NAO use blocos de codigo, NAO coloque explicacoes fora do JSON.
                    - NAO "pense em voz alta". Sua unica saida deve ser o JSON abaixo.

                    Exemplo de saida esperada (siga exatamente este formato):
                    {"AnswerType": <"ANSWER", "DOUBT" ou "NOISE">, "response": "sua resposta", "nextContextId": <id do contexto escolhido>, "next": "frase natural em portugues baseada no contexto"}
        """);

        String prompt = buildPrompt(content.toString(), conversationHistory);
        String responseAiService = aiService.complete(prompt, 800, 0.6);

        if (responseAiService == null || responseAiService.trim().isEmpty()) {
            log.error("AI service returned null or empty response for answer: {}", answer);
            throw new RuntimeException("Failed to get response from AI service");
        }

        try {
            String cleanedJson = extractJsonFromResponse(responseAiService);
            JsonNode node = new ObjectMapper().readTree(cleanedJson);

            String answerType = node.get("AnswerType").asText();
            String response = node.get("response").asText();
            Long nextContextId = node.get("nextContextId").asLong();
            String next = node.get("next").asText();
            return new HandleAnswerResult(answerType, response, nextContextId, next);
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", responseAiService, e);
            throw e;
        }
    }

    public String buildConversationContext(List<ConversationMessage> conversation) {
        if (conversation == null || conversation.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append("Historico da conversa ate agora:\n");

        for (ConversationMessage msg : conversation) {
            String author = msg.getAuthor() == ConversationAuthor.TEACHER ? "Teacher" : "Student";
            sb.append("- ").append(author).append(": ");
            switch (msg.getType()) {
                case EXERCISE -> sb.append("(exercise) ").append(msg.getContent());
                case ANSWER -> sb.append(msg.getContent());
                case FEEDBACK -> sb.append(msg.getContent());
                case DOUBT -> sb.append("(doubt) ").append(msg.getContent());
                case GREETING -> sb.append(msg.getContent());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public String buildContextSummary(List<Context> contexts) {
        StringBuilder contextSummary = new StringBuilder();
        contextSummary.append("Contextos do aluno:\n");

        for (Context context : contexts) {
            ContextStats contextStats = contextService.getContextStats(context.getId());
            int count = contextStats != null ? contextStats.getTotalExercises() : 0;
            double avg = contextStats != null ? contextStats.getAverageScore() : 0;
            String level = count == 0 ? "NAO PRATICADO (prioridade maxima)"
                    : avg >= 80 ? "dominado" : avg >= 50 ? "em progresso" : "PRECISA PRATICAR";
            contextSummary.append("- [id=").append(context.getId()).append("] ")
                    .append(context.getContent())
                    .append(" | ex: ").append(count)
                    .append(" | media: ").append(String.format("%.0f", avg))
                    .append(" | ").append(level).append("\n");
        }
        return contextSummary.toString();
    }

    public String buildPrompt(String content) {
        return buildPrompt(content, null);
    }

    public String buildPrompt(String content, List<ConversationMessage> conversationHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildContextSummary(contextService.findAll()));
        sb.append(
                """
                            Voce e um assistente de aprendizado de ingles descontraido e direto ao ponto.
                            O usuario quer aprender ingles ou tirar duvidas com base no contexto dele.

                            A dinamica de aprendizado funciona assim:
                            1) O usuario tem uma lista de contextos que ele quer praticar.
                            2) Para cada contexto, o usuario tem estatisticas de quantas vezes praticou, e qual a media de acertos (0-100%). Contextos com media menor devem ser priorizados.
                            3) O usuario quer praticar o contexto que mais precisa, entao voce deve sugerir uma frase para ele traduzir, baseada nesse contexto.

                            Regras:
                            - Vamos focar nessa dinamica de repeticao de tradução e variacao, sem explicacoes ou aprofundamento.
                            - se holver muita dificuldade, tente simplificar a frase, a medida que aluna vai aprendendo sobre aquele contexto vamos dificultando um pouco.

                        """);
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            sb.append(buildConversationContext(conversationHistory));
        }
        sb.append(content).append("\n");
        return sb.toString();
    }

    public String analyze(String content) {
        String prompt = "Voce e um assistente de aprendizado de ingles descontraido e direto ao ponto.\n\n"
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
        return aiService.analyze(prompt, content);
    }

    /**
     * Extrai JSON válido da resposta da IA, removendo qualquer lixo antes ou depois.
     * Usa regex para encontrar o primeiro { até o último }.
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return response;
        }
        
        Matcher matcher = JSON_EXTRACT_PATTERN.matcher(response);
        if (matcher.find()) {
            String extracted = matcher.group();
            log.debug("Extracted JSON from response: {}", extracted);
            return extracted;
        }
        
        log.warn("No JSON found in response: {}", response);
        return response;
    }
}