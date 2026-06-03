package com.contextapi.dynamics;

import com.contextapi.entities.ConversationMessage;
import com.contextapi.entities.Context;
import com.contextapi.entities.ContextStats;
import com.contextapi.enums.ConversationAuthor;
import com.contextapi.exceptions.AiServiceException;
import com.contextapi.records.HandleAnswerResult;
import com.contextapi.records.ResponseIAResult;
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
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long INITIAL_BACKOFF_MS = 500; 
    public static final String INTRO = "Opa, vamos praticar! Que ótimo. Bora conversar então." +
            "Vou falar uma frase em portugues e quero que voce traduza para o ingles. " +
            "Se tiver duvidas, pode perguntar, ok?";

    public RaptorDynamic(AiService aiService, ContextService contextService) {
        this.aiService = aiService;
        this.contextService = contextService;
    }

    private String callAiWithRetry(String prompt, int maxTokens, double temperature, boolean jsonMode, String methodName) throws AiServiceException {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                log.debug("AI call attempt {}/{} for {}", attempt, MAX_RETRY_ATTEMPTS, methodName);
                String response = aiService.complete(prompt, maxTokens, temperature, jsonMode);
                
                if (response == null || response.trim().isEmpty()) {
                    throw new AiServiceException("AI service returned null or empty response on attempt " + attempt);
                }
                
                if (jsonMode) {
                    try {
                        new ObjectMapper().readTree(response);
                        log.debug("Valid JSON received on attempt {}", attempt);
                        return response;
                    } catch (Exception e) {
                        log.warn("JSON parsing failed on attempt {} (jsonMode=true): {}", attempt, e.getMessage());
                        if (attempt == MAX_RETRY_ATTEMPTS) {
                            throw new AiServiceException("Failed to get valid JSON after " + MAX_RETRY_ATTEMPTS + " attempts", e);
                        }
                    }
                } else {
                    return response;
                }
                
            } catch (Exception e) {
                lastException = e;
                log.warn("AI call failed on attempt {}/{} for {}: {}", attempt, MAX_RETRY_ATTEMPTS, methodName, e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    long backoffMs = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt - 1);
                    log.info("Retrying in {}ms...", backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AiServiceException("Retry interrupted", ie);
                    }
                }
            }
        }
        
        throw new AiServiceException("Failed after " + MAX_RETRY_ATTEMPTS + " attempts for " + methodName, lastException);
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

        String responseAiService = callAiWithRetry(prompt, 800, 0.6, true, "startLesson");

        try {
            String cleanedJson = extractJsonFromResponse(responseAiService);
            JsonNode node = new ObjectMapper().readTree(cleanedJson);

            return new ResponseIAResult(node.get("contextId").asLong(), node.get("response").asText());
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", responseAiService, e);
            throw new AiServiceException("Failed to parse AI response JSON", e);
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

                    Se for ANSWER, avalie se a resposta esta correta e de uma pontuacao de 0 a 100.
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
                    {"AnswerType": <"ANSWER", "DOUBT" ou "NOISE">, "response": "sua resposta", "nextContextId": <id do contexto escolhido>, "next": "frase natural em portugues baseada no contexto", "score": <pontuacao de 0 a 100, apenas se AnswerType for ANSWER, caso contrario 0>}
        """);

        String prompt = buildPrompt(content.toString(), conversationHistory);
        String responseAiService = callAiWithRetry(prompt, 800, 0.6, true, "handleAnswer");

        try {
            String cleanedJson = extractJsonFromResponse(responseAiService);
            JsonNode node = new ObjectMapper().readTree(cleanedJson);

            String answerType = node.get("AnswerType").asText();
            String response = node.get("response").asText();
            Long nextContextId = node.get("nextContextId").asLong();
            String next = node.get("next").asText();
            int score = node.has("score") ? node.get("score").asInt(0) : 0;
            return new HandleAnswerResult(answerType, response, nextContextId, next, score);
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", responseAiService, e);
            throw new AiServiceException("Failed to parse AI response JSON", e);
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
                    : determineLevel(avg);
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
        String prompt = """
                Voce e um assistente de aprendizado de ingles descontraido e direto ao ponto.

                O usuario quer aprender ingles ou tirar duvidas com base neste contexto:
                "%s"

                Responda em no maximo 2-3 linhas, em portugues, dizendo apenas o que entendeu do contexto.

                Regras:
                - Apenas diga o que entendeu do contexto, sem explicar ou aprofundar
                - Mantenha o foco em ingles com base no contexto do usuario
                - Nao fuja do tema
                - Nao faca perguntas nem convide para continuar a conversa
                - Se houver termos em ingles faca a traducao de forma natural para portugues na resposta
                - Se houver termos em portugues faca a traducao de forma natural para ingles na resposta
                - Nada de saudacoes ou introducoes""";
        return aiService.analyze(prompt, content);
    }

    private static String determineLevel(double avg) {
        if (avg >= 80) {
            return "dominado";
        } else if (avg >= 50) {
            return "em progresso";
        }
        return "PRECISA PRATICAR";
    }

    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return response;
        }
        
        Matcher matcher = JSON_EXTRACT_PATTERN.matcher(response);
        if (matcher.find()) {
            String extracted = matcher.group();
            try {
                new ObjectMapper().readTree(extracted);
                log.debug("Extracted JSON using regex strategy: {}", extracted);
                return extracted;
            } catch (Exception e) {
                log.debug("Regex extracted invalid JSON, trying balanced brace strategy");
            }
        }
        
        // Strategy 2: Find balanced JSON by counting braces
        String balancedJson = extractBalancedJson(response);
        if (balancedJson != null) {
            try {
                new ObjectMapper().readTree(balancedJson);
                log.debug("Extracted JSON using balanced brace strategy: {}", balancedJson);
                return balancedJson;
            } catch (Exception e) {
                log.debug("Balanced brace extracted invalid JSON");
            }
        }
        
        log.warn("No valid JSON found in response: {}", response);
        return response;
    }
    
    private String extractBalancedJson(String response) {
        int startIndex = response.indexOf('{');
        if (startIndex == -1) {
            return null;
        }
        
        int braceCount = 0;
        int endIndex = -1;
        
        for (int i = startIndex; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    endIndex = i;
                    break;
                }
            }
        }
        
        if (endIndex != -1) {
            return response.substring(startIndex, endIndex + 1);
        }
        
        return null;
    }
}