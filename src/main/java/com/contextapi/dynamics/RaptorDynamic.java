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

@Slf4j
@Component
public class RaptorDynamic {

    private final AiService aiService;
    private final ContextService contextService;
    public final String intro = "Opa, vamos praticar! Que ótimo. Bora conversar então." +
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
            JsonNode node = new ObjectMapper().readTree(responseAiService);

            Long contextId = node.get("contextId").asLong();
            String response = node.get("response").asText();

            return new ResponseIAResult(contextId, response);
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", responseAiService, e);
            throw e;
        }
    }

    public String startVoice() {
        String content = "Voces ja estavam conversando por texto e agora vao comecar a conversar por fala,";
        String prompt = buildPrompt(content);
        return aiService.complete(prompt, 800, 0.6);
    }

    public HandleAnswerResult handleAnswer(String answer) throws Exception{

        StringBuilder content = new StringBuilder();
        content.append("O aluno respondeu: ").append(answer).append("\n");
        content.append("""
                    Analise a resposta do aluno,
                    classifique se uma resposta ao pedido de traducao seu ou se e uma duvida.
                    Se for resposta, avalie se a resposta esta correta, 
                    Se for duvida, responda a duvida de forma clara e objetiva, sem rodeios, focando na duvida do aluno, sem aprofundar muito e sem continuar a conversa.

                    para proxima frase escolha o contexto que MAIS PRECISA de pratica (menor media ou nao praticado).
                    Crie uma frase NATURAL e UTIL. Use VARIACOES — mude palavras, tempo verbal, ou situacao.
                    Se o aluno estiver com dúvida, priorize a mesma frase ou uma variação simples dela, para ajudar o aluno a aprender.

                    FORMATO DE RESPOSTA — INSTRUCOES CRITICAS:
                    - Voce deve responder EXCLUSIVAMENTE com um objeto JSON.
                    - NAO escreva nenhum texto antes ou depois do JSON.
                    - NAO use markdown, NAO use blocos de codigo, NAO coloque explicacoes fora do JSON.
                    - NAO "pense em voz alta". Sua unica saida deve ser o JSON abaixo.

                    Exemplo de saida esperada (siga exatamente este formato):
                    {"AnswerType": < "ANSWER" ou "DOUBT" >, "response": "sua resposta", "nextContextId": <id do contexto escolhido>, "next": "frase natural em portugues baseada no contexto"}
        """);

        String prompt = buildPrompt(content.toString());
        String responseAiService = aiService.complete(prompt, 800, 0.6);

        if (responseAiService == null || responseAiService.trim().isEmpty()) {
            log.error("AI service returned null or empty response for answer: {}", answer);
            throw new RuntimeException("Failed to get response from AI service");
        }

        try {
            JsonNode node = new ObjectMapper().readTree(responseAiService);

            String AnswerType = node.get("AnswerType").asText();
            String response = node.get("response").asText();
            Long nextContextId = node.get("nextContextId").asLong();
            String next = node.get("next").asText();
            return new HandleAnswerResult(AnswerType, response, nextContextId, next);
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

}