package com.contextapi.services;

import com.contextapi.dtos.*;
import com.contextapi.entities.*;
import com.contextapi.enums.LessonStatus;
import com.contextapi.exceptions.ResourceNotFoundException;
import com.contextapi.repositories.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
public class LessonService {

    private static final String DEFAULT_DYNAMIC = "TRANSLATION_REPETITION";

    private final LessonRepository lessonRepository;
    private final LessonExerciseRepository exerciseRepository;
    private final ContextStatsRepository contextStatsRepository;
    private final ContextRepository contextRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LessonService(
            LessonRepository lessonRepository,
            LessonExerciseRepository exerciseRepository,
            ContextStatsRepository contextStatsRepository,
            ContextRepository contextRepository,
            AiService aiService) {
        this.lessonRepository = lessonRepository;
        this.exerciseRepository = exerciseRepository;
        this.contextStatsRepository = contextStatsRepository;
        this.contextRepository = contextRepository;
        this.aiService = aiService;
    }

    // ════════════════════════════════════════════════════════════════
    // CREATE — starts a continuous lesson with intro + first exercise
    // ════════════════════════════════════════════════════════════════

    public LessonDTO create(CreateLessonRequest request) {
        // Reuse active lesson if any
        Lesson activeLesson = lessonRepository
                .findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS)
                .orElse(null);
        if (activeLesson != null) {
            return mapToDTO(activeLesson);
        }

        String dynamicType = request != null && request.getDynamicType() != null
                && !request.getDynamicType().isBlank()
                ? request.getDynamicType()
                : DEFAULT_DYNAMIC;

        List<Context> contexts = contextRepository.findAll();
        if (contexts.isEmpty()) {
            throw new IllegalArgumentException("Create at least one context before starting a lesson");
        }

        // Save lesson first
        Lesson lesson = new Lesson();
        lesson.setDynamicType(dynamicType);
        lesson = lessonRepository.save(lesson);

        // Generate intro + first exercise together via AI
        String introJson = aiService.complete(buildIntroAndFirstExercisePrompt(contexts, dynamicType), 800, 0.6);
        IntroAndExerciseResult parsed = parseIntroAndFirstExercise(introJson);

        String intro = parsed != null ? parsed.intro() : generateFallbackIntro(contexts);
        lesson.setIntro(intro);
        lesson = lessonRepository.save(lesson);

        // Create first exercise
        Context firstContext = contexts.get(0);
        if (parsed != null && parsed.exercise() != null && parsed.exercise().contextId() > 0) {
            firstContext = findContextById(contexts, parsed.exercise().contextId());
        }

        LessonExercise firstExercise = createExerciseEntity(lesson, firstContext, parsed);
        lesson.addExercise(firstExercise);

        // Initialize preferred order
        lesson.setPreferredContextIds(buildPreferredOrderStr(contexts));
        lesson = lessonRepository.save(lesson);

        return mapToDTO(lesson);
    }

    // ════════════════════════════════════════════════════════════════
    // NEXT — generate the next exercise
    // ════════════════════════════════════════════════════════════════

    public LessonDTO next(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Lesson not found with id: %d", lessonId)));

        if (lesson.getStatus() == LessonStatus.COMPLETED) {
            throw new IllegalArgumentException("This lesson is already completed");
        }

        // Check if there's already a pending (unanswered) exercise
        Optional<LessonExercise> pending = lesson.getExercises().stream()
                .filter(e -> !e.isAnswered())
                .findFirst();
        if (pending.isPresent()) {
            return mapToDTO(lesson);
        }

        List<Context> allContexts = contextRepository.findAll();
        if (allContexts.isEmpty()) {
            throw new IllegalArgumentException("No contexts available");
        }

        // AI chooses context + generates exercise
        String contextSummary = buildContextSummary(lesson, allContexts);
        String recentPrompts = buildRecentPromptsSummary(lesson);

        String exerciseJson = aiService.complete(
                buildNextExercisePrompt(contextSummary, recentPrompts, lesson.getDynamicType()),
                600, 0.5);

        NextExerciseResult result = parseNextExercise(exerciseJson);

        // Pick context: AI choice or smart fallback
        Context chosenContext = allContexts.get(0);
        if (result != null && result.contextId() > 0) {
            Long cid = result.contextId();
            chosenContext = allContexts.stream()
                    .filter(c -> c.getId().equals(cid))
                    .findFirst()
                    .orElse(chosenContext);
        } else {
            chosenContext = pickWeakestContext(lesson, allContexts);
        }

        LessonExercise exercise = new LessonExercise();
        exercise.setContext(chosenContext);
        exercise.setPromptPt(result != null ? result.promptPt() : buildFallbackPrompt(chosenContext));
        exercise.setExpectedAnswerEn(result != null ? result.expectedAnswerEn() : chosenContext.getContent());
        exercise.setVariationNote(result != null ? result.variationNote() : "");
        lesson.addExercise(exercise);

        // Rotate: move just-used context to end of priority list
        updatePreferredOrder(lesson, chosenContext.getId(), allContexts);

        lessonRepository.save(lesson);
        return mapToDTO(lesson);
    }

    // ════════════════════════════════════════════════════════════════
    // SUBMIT ANSWER
    // ════════════════════════════════════════════════════════════════

    public LessonDTO submitAnswer(Long lessonId, SubmitAnswerRequest request) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Lesson not found with id: %d", lessonId)));

        if (lesson.getStatus() == LessonStatus.COMPLETED) {
            throw new IllegalArgumentException("This lesson is already completed");
        }

        LessonExercise exercise = exerciseRepository.findById(request.getExerciseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Exercise not found with id: %d", request.getExerciseId())));

        if (!exercise.getLesson().getId().equals(lessonId)) {
            throw new IllegalArgumentException("Exercise does not belong to this lesson");
        }

        String studentInput = request.getAnswer().trim();

        // First, ask AI to classify: is the student answering the translation, or asking a doubt?
        ClassificationResult classification = classifyStudentInput(exercise, studentInput);
        String teacherMessage;
        boolean wasDoubt;

        if (classification.isDoubt()) {
            // Student asked a question — answer it but DON'T count as translation attempt
            teacherMessage = classification.teacherMessage();
            exercise.setStudentAnswer(studentInput);
            // Keep exercise unanswered so student can still translate it
            exerciseRepository.save(exercise);
            wasDoubt = true;
        } else {
            // Student attempted the translation — evaluate normally
            if (exercise.isAnswered()) {
                throw new IllegalArgumentException("This exercise has already been answered");
            }
            TeacherEvaluation eval = evaluateAnswer(exercise, studentInput);
            exercise.setStudentAnswer(studentInput);
            exercise.setFeedback(eval.feedback());
            exercise.setScore(eval.score());
            exercise.setAnswered(true);
            exerciseRepository.save(exercise);

            // Update context stats
            ContextStats stats = contextStatsRepository
                    .findByLessonIdAndContextId(lessonId, exercise.getContext().getId())
                    .orElseGet(() -> {
                        ContextStats s = new ContextStats();
                        s.setLesson(lesson);
                        s.setContext(exercise.getContext());
                        return s;
                    });
            stats.addExercise(eval.score());
            contextStatsRepository.save(stats);

            teacherMessage = eval.feedback();
            wasDoubt = false;
        }

        lessonRepository.save(lesson);
        return mapToDTO(lesson, teacherMessage, wasDoubt);
    }

    // ════════════════════════════════════════════════════════════════
    // FINISH
    // ════════════════════════════════════════════════════════════════

    public LessonDTO finish(Long id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Lesson not found with id: %d", id)));

        lesson.setStatus(LessonStatus.COMPLETED);
        lesson.setCompletedAt(LocalDateTime.now());
        lesson.setFinalFeedback(buildFinalFeedback(lesson));

        return mapToDTO(lessonRepository.save(lesson));
    }

    // ════════════════════════════════════════════════════════════════
    // READ
    // ════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public LessonDTO findById(Long id) {
        return lessonRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Lesson not found with id: %d", id)));
    }

    @Transactional(readOnly = true)
    public LessonDTO findActive() {
        return lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS)
                .map(this::mapToDTO)
                .orElse(null);
    }

    // ════════════════════════════════════════════════════════════════
    // AI PROMPTS
    // ════════════════════════════════════════════════════════════════

    private String buildIntroAndFirstExercisePrompt(List<Context> contexts, String dynamicType) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                Voce e um professor de ingles carismatico chamado Teacher, para um aluno brasileiro.
                A aula e continua — o aluno pratica o quanto quiser.
                Dinamica: voce fala uma frase em portugues e o aluno traduz para o ingles.

                Contextos do aluno:
                """);
        for (int i = 0; i < contexts.size(); i++) {
            sb.append(i + 1).append(". [id=").append(contexts.get(i).getId())
                    .append("] ").append(contexts.get(i).getContent()).append("\n");
        }
        sb.append("""

                Responda SOMENTE com JSON valido:
                {
                  "intro": "saudacao calorosa de 2-3 frases em portugues. Se apresente como Teacher e explique: vou falar uma frase em portugues e voce traduz para o ingles. Vamos praticar o quanto quiser!",
                  "exercise": {
                    "contextId": <id do primeiro contexto>,
                    "promptPt": "frase natural em portugues baseada no contexto",
                    "expectedAnswerEn": "traducao natural em ingles",
                    "variationNote": ""
                  }
                }
                """);
        return sb.toString();
    }

    private String buildNextExercisePrompt(String contextSummary, String recentPrompts, String dynamicType) {
        return """
                Voce e Teacher, professor de ingles em uma aula continua de traducao PT->EN.
                Escolha o contexto que MAIS PRECISA de pratica (menor media ou nao praticado).
                Crie uma frase NATURAL e UTIL. Use VARIACOES — mude palavras, tempo verbal, ou situacao.

                Contextos com estatisticas (escolha o mais fraco):
                %s

                Frases recentes (EVITE repetir):
                %s

                Responda SOMENTE com JSON valido:
                {
                  "contextId": <id do contexto escolhido>,
                  "promptPt": "frase em portugues variada do contexto",
                  "expectedAnswerEn": "resposta natural em ingles",
                  "variationNote": "breve explicacao da variacao (ex: mudei para passado)"
                }
                """.formatted(contextSummary, recentPrompts);
    }

    private String buildContextSummary(Lesson lesson, List<Context> allContexts) {
        StringBuilder sb = new StringBuilder();
        for (Context ctx : allContexts) {
            ContextStats stats = contextStatsRepository
                    .findByLessonIdAndContextId(lesson.getId(), ctx.getId())
                    .orElse(null);
            int count = stats != null ? stats.getTotalExercises() : 0;
            double avg = stats != null ? stats.getAverageScore() : 0;
            String level = count == 0 ? "NAO PRATICADO (prioridade maxima)" :
                    avg >= 80 ? "dominado" : avg >= 50 ? "em progresso" : "PRECISA PRATICAR";
            sb.append("- [id=").append(ctx.getId()).append("] ")
                    .append(ctx.getContent())
                    .append(" | ex: ").append(count)
                    .append(" | media: ").append(String.format("%.0f", avg))
                    .append(" | ").append(level).append("\n");
        }
        return sb.toString();
    }

    private String buildRecentPromptsSummary(Lesson lesson) {
        List<LessonExercise> recent = exerciseRepository
                .findTop10ByLessonIdOrderByCreatedAtDesc(lesson.getId());
        if (recent.isEmpty()) return "(nenhuma ainda)";
        StringBuilder sb = new StringBuilder();
        for (LessonExercise ex : recent) {
            sb.append("- \"").append(ex.getPromptPt()).append("\"\n");
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    // CLASSIFY: doubt vs translation
    // ════════════════════════════════════════════════════════════════

    /**
     * Asks the AI to determine if the student input is a translation attempt or a doubt/question.
     * If it's a doubt, the AI also provides an answer.
     */
    private ClassificationResult classifyStudentInput(LessonExercise exercise, String studentInput) {
        String prompt = """
                Voce e Teacher, professor de ingles de um aluno brasileiro.
                O aluno esta em uma aula de traducao PT->EN. A frase que voce pediu foi:

                "%s"

                O aluno digitou (ou falou): "%s"

                Classifique a entrada do aluno:
                - Se for uma duvida/pergunta (ex: "como falo X em ingles", "o que significa Y", "nao entendi", "pode repetir"),
                  responda: { "isDoubt": true, "teacherMessage": "resposta curta e util em portugues" }
                - Se for uma tentativa de traducao (mesmo que ruim), responda:
                  { "isDoubt": false, "teacherMessage": "" }

                Responda SOMENTE com JSON valido.
                """.formatted(exercise.getPromptPt(), studentInput);

        String response = aiService.complete(prompt, 300, 0.1);
        ClassificationResult parsed = parseClassification(response);
        if (parsed != null) return parsed;

        // Fallback: heuristics
        String lower = studentInput.toLowerCase().trim();
        boolean looksLikeDoubt = lower.contains("como falo") || lower.contains("como falar")
                || lower.contains("o que significa") || lower.contains("o que e")
                || lower.contains("nao entendi") || lower.contains("pode repetir")
                || lower.contains("qual a diferenca") || lower.contains("como se diz")
                || lower.contains("?") || lower.contains("explique")
                || lower.startsWith("duvida");

        if (looksLikeDoubt) {
            return new ClassificationResult(true,
                    "Entendi sua duvida! "
                            + exercise.getPromptPt() + " em ingles seria: \""
                            + exercise.getExpectedAnswerEn() + "\". Alguma outra duvida?");
        }
        return new ClassificationResult(false, "");
    }

    private ClassificationResult parseClassification(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            return new ClassificationResult(
                    root.path("isDoubt").asBoolean(false),
                    root.path("teacherMessage").asText(""));
        } catch (Exception e) {
            log.warn("Could not parse classification JSON: {}", e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════
    // EVALUATION
    // ════════════════════════════════════════════════════════════════

    private TeacherEvaluation evaluateAnswer(LessonExercise exercise, String answer) {
        String prompt = """
                Voce e um professor de ingles objetivo e gentil.
                Avalie a traducao PT->EN do aluno.

                Frase em portugues: %s
                Resposta natural esperada: %s
                Resposta do aluno: %s

                Responda SOMENTE com JSON valido:
                { "score": 0, "feedback": "feedback em portugues (max 2 frases). Inclua a versao natural em ingles." }

                Score 0-100. Seja justo mas incentive.
                """.formatted(exercise.getPromptPt(), exercise.getExpectedAnswerEn(), answer);

        String response = aiService.complete(prompt, 400, 0.2);
        TeacherEvaluation parsed = parseTeacherEvaluation(response);
        if (parsed != null) return parsed;
        return new TeacherEvaluation(70,
                "Boa tentativa! Uma forma natural seria: " + exercise.getExpectedAnswerEn());
    }

    private String buildFinalFeedback(Lesson lesson) {
        List<ContextStats> stats = contextStatsRepository.findByLessonId(lesson.getId());
        StringBuilder summary = new StringBuilder();
        summary.append("Total de exercicios: ").append(lesson.getExerciseCount()).append("\n");
        for (ContextStats s : stats) {
            summary.append("- ").append(s.getContext().getContent())
                    .append(" | ex: ").append(s.getTotalExercises())
                    .append(" | media: ").append(String.format("%.0f", s.getAverageScore()))
                    .append("\n");
        }
        String prompt = """
                Voce e um avaliador pedagogico de ingles.
                Resuma o progresso do aluno em 3-4 frases em portugues.
                Destaque dominados e o que precisa praticar. Seja motivador.

                %s
                """.formatted(summary);
        String response = aiService.complete(prompt, 400, 0.3);
        if (response == null || response.isBlank()) {
            return "Aula finalizada! Continue praticando os contextos com menor media. Voce esta progredindo!";
        }
        return response.trim();
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    private Context pickWeakestContext(Lesson lesson, List<Context> allContexts) {
        return allContexts.stream()
                .min(Comparator.comparingDouble(ctx -> {
                    ContextStats s = contextStatsRepository
                            .findByLessonIdAndContextId(lesson.getId(), ctx.getId())
                            .orElse(null);
                    return s != null ? s.getAverageScore() : 0;
                }))
                .orElse(allContexts.get(0));
    }

    private String buildPreferredOrderStr(List<Context> contexts) {
        return contexts.stream().map(c -> String.valueOf(c.getId())).reduce((a, b) -> a + "," + b).orElse("");
    }

    private void updatePreferredOrder(Lesson lesson, Long justUsedId, List<Context> allContexts) {
        List<Long> order = new ArrayList<>(allContexts.stream().map(Context::getId).toList());
        order.remove(justUsedId);
        order.add(justUsedId);
        lesson.setPreferredContextIds(order.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
    }

    private Context findContextById(List<Context> contexts, long id) {
        return contexts.stream().filter(c -> c.getId() == id).findFirst().orElse(contexts.get(0));
    }

    private LessonExercise createExerciseEntity(Lesson lesson, Context context, IntroAndExerciseResult parsed) {
        LessonExercise exercise = new LessonExercise();
        exercise.setContext(context);
        if (parsed != null && parsed.exercise() != null) {
            exercise.setPromptPt(parsed.exercise().promptPt());
            exercise.setExpectedAnswerEn(parsed.exercise().expectedAnswerEn());
            exercise.setVariationNote(parsed.exercise().variationNote());
        } else {
            exercise.setPromptPt(buildFallbackPrompt(context));
            exercise.setExpectedAnswerEn(context.getContent());
        }
        return exercise;
    }

    private String buildFallbackPrompt(Context context) {
        return "Como voce diria isso em ingles: \"" + context.getContent() + "\"";
    }

    private String generateFallbackIntro(List<Context> contexts) {
        return "Hello! I'm Teacher, seu professor de ingles. " +
                "Vou falar frases em portugues baseadas nos seus contextos e voce traduz para o ingles. " +
                "Pratique o quanto quiser! Vamos comecar?";
    }

    // ════════════════════════════════════════════════════════════════
    // JSON PARSING
    // ════════════════════════════════════════════════════════════════

    private IntroAndExerciseResult parseIntroAndFirstExercise(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            String intro = root.path("intro").asText();
            JsonNode ex = root.path("exercise");
            if (intro.isBlank() || !ex.isObject()) return null;
            return new IntroAndExerciseResult(intro, new ExercisePart(
                    ex.path("contextId").asLong(0),
                    ex.path("promptPt").asText(""),
                    ex.path("expectedAnswerEn").asText(""),
                    ex.path("variationNote").asText("")));
        } catch (Exception e) {
            log.warn("Could not parse intro+exercise JSON: {}", e.getMessage());
            return null;
        }
    }

    private NextExerciseResult parseNextExercise(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            return new NextExerciseResult(
                    root.path("contextId").asLong(0),
                    root.path("promptPt").asText(""),
                    root.path("expectedAnswerEn").asText(""),
                    root.path("variationNote").asText(""));
        } catch (Exception e) {
            log.warn("Could not parse exercise JSON: {}", e.getMessage());
            return null;
        }
    }

    private TeacherEvaluation parseTeacherEvaluation(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            int score = Math.max(0, Math.min(100, root.path("score").asInt(0)));
            String feedback = root.path("feedback").asText();
            if (feedback.isBlank()) return null;
            return new TeacherEvaluation(score, feedback);
        } catch (Exception e) {
            log.warn("Could not parse teacher evaluation JSON: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    // ════════════════════════════════════════════════════════════════
    // DTO MAPPING
    // ════════════════════════════════════════════════════════════════

    private LessonDTO mapToDTO(Lesson lesson) {
        return mapToDTO(lesson, null, false);
    }

    private LessonDTO mapToDTO(Lesson lesson, String lastTeacherMessage, boolean lastWasDoubt) {
        ExerciseDTO currentExercise = lesson.getExercises().stream()
                .filter(e -> !e.isAnswered())
                .findFirst()
                .map(ex -> new ExerciseDTO(
                        ex.getId(),
                        ex.getContext().getId(),
                        ex.getContext().getContent(),
                        ex.getPromptPt(),
                        ex.isAnswered() ? ex.getVariationNote() : null,
                        ex.isAnswered() ? ex.getFeedback() : null,
                        ex.isAnswered() ? ex.getScore() : null,
                        ex.isAnswered()))
                .orElse(null);

        List<ContextStatsDTO> statsDTOs = contextStatsRepository.findByLessonId(lesson.getId())
                .stream()
                .map(s -> new ContextStatsDTO(
                        s.getContext().getId(),
                        s.getContext().getContent(),
                        s.getTotalExercises(),
                        s.getTotalScore(),
                        s.getAverageScore()))
                .toList();

        return new LessonDTO(
                lesson.getId(),
                lesson.getDynamicType(),
                lesson.getStatus(),
                lesson.getIntro(),
                lesson.getFinalFeedback(),
                lesson.getCreatedAt(),
                lesson.getCompletedAt(),
                lesson.getExerciseCount(),
                currentExercise,
                lastTeacherMessage,
                lastWasDoubt,
                statsDTOs);
    }

    // ════════════════════════════════════════════════════════════════
    // RECORDS
    // ════════════════════════════════════════════════════════════════

    private record IntroAndExerciseResult(String intro, ExercisePart exercise) {}
    private record ExercisePart(long contextId, String promptPt, String expectedAnswerEn, String variationNote) {}
    private record NextExerciseResult(long contextId, String promptPt, String expectedAnswerEn, String variationNote) {}
    private record TeacherEvaluation(Integer score, String feedback) {}
    private record ClassificationResult(boolean isDoubt, String teacherMessage) {}
}
