package com.contextapi.services;

import com.contextapi.dtos.CreateLessonRequest;
import com.contextapi.dtos.LessonDTO;
import com.contextapi.dtos.LessonItemDTO;
import com.contextapi.dtos.SubmitAnswerRequest;
import com.contextapi.entities.Context;
import com.contextapi.entities.Lesson;
import com.contextapi.entities.LessonItem;
import com.contextapi.entities.StudentAnswer;
import com.contextapi.enums.LessonStatus;
import com.contextapi.exceptions.ResourceNotFoundException;
import com.contextapi.repositories.ContextRepository;
import com.contextapi.repositories.LessonItemRepository;
import com.contextapi.repositories.LessonRepository;
import com.contextapi.repositories.StudentAnswerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
public class LessonService {

    private static final String DEFAULT_DYNAMIC = "TRANSLATION_REPETITION";

    private final LessonRepository lessonRepository;
    private final LessonItemRepository lessonItemRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final ContextRepository contextRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LessonService(
            LessonRepository lessonRepository,
            LessonItemRepository lessonItemRepository,
            StudentAnswerRepository studentAnswerRepository,
            ContextRepository contextRepository,
            AiService aiService) {
        this.lessonRepository = lessonRepository;
        this.lessonItemRepository = lessonItemRepository;
        this.studentAnswerRepository = studentAnswerRepository;
        this.contextRepository = contextRepository;
        this.aiService = aiService;
    }

    public LessonDTO create(CreateLessonRequest request) {
        Lesson activeLesson = lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS)
                .orElse(null);
        if (activeLesson != null) {
            return mapToDTO(activeLesson);
        }

        String dynamicType = request != null && request.getDynamicType() != null && !request.getDynamicType().isBlank()
                ? request.getDynamicType()
                : DEFAULT_DYNAMIC;

        List<Context> contexts = contextRepository
                .findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "id")))
                .getContent();

        if (contexts.isEmpty()) {
            throw new IllegalArgumentException("Create at least one context before starting a lesson");
        }

        LessonPlan plan = planLesson(contexts, dynamicType);

        Lesson lesson = new Lesson();
        lesson.setDynamicType(dynamicType);
        lesson.setIntro(plan.intro());

        for (int i = 0; i < plan.items().size(); i++) {
            PlannedItem plannedItem = plan.items().get(i);
            Context context = contexts.get(Math.min(i, contexts.size() - 1));

            LessonItem item = new LessonItem();
            item.setContext(context);
            item.setPosition(i + 1);
            item.setPromptPt(plannedItem.promptPt());
            item.setExpectedAnswerEn(plannedItem.expectedAnswerEn());
            lesson.addItem(item);
        }

        return mapToDTO(lessonRepository.save(lesson));
    }

    @Transactional(readOnly = true)
    public LessonDTO findById(Long id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Lesson not found with id: %d", id)));
        return mapToDTO(lesson);
    }

    @Transactional(readOnly = true)
    public LessonDTO findActive() {
        return lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS)
                .map(this::mapToDTO)
                .orElse(null);
    }

    public LessonDTO submitAnswer(Long lessonId, SubmitAnswerRequest request) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Lesson not found with id: %d", lessonId)));

        if (lesson.getStatus() == LessonStatus.COMPLETED) {
            throw new IllegalArgumentException("This lesson is already completed");
        }

        LessonItem item = lessonItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Lesson item not found with id: %d", request.getItemId())));

        if (!item.getLesson().getId().equals(lessonId)) {
            throw new IllegalArgumentException("Lesson item does not belong to this lesson");
        }

        TeacherEvaluation evaluation = evaluateAnswer(item, request.getAnswer());

        item.setLastAnswer(request.getAnswer().trim());
        item.setTeacherFeedback(evaluation.feedback());
        item.setScore(evaluation.score());
        item.setCompleted(true);
        lessonItemRepository.save(item);

        StudentAnswer answer = new StudentAnswer();
        answer.setLessonItem(item);
        answer.setAnswerText(request.getAnswer().trim());
        answer.setFeedback(evaluation.feedback());
        answer.setScore(evaluation.score());
        studentAnswerRepository.save(answer);

        return mapToDTO(lesson);
    }

    public LessonDTO finish(Long id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Lesson not found with id: %d", id)));

        lesson.setStatus(LessonStatus.COMPLETED);
        lesson.setCompletedAt(LocalDateTime.now());
        lesson.setFinalFeedback(buildFinalFeedback(lesson));

        return mapToDTO(lessonRepository.save(lesson));
    }

    private LessonPlan planLesson(List<Context> contexts, String dynamicType) {
        StringBuilder contextsText = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            contextsText.append(i + 1).append(". ").append(contexts.get(i).getContent()).append("\n");
        }

        String prompt = """
                Voce e um professor de ingles para um aluno brasileiro.
                Crie uma aula curta usando a dinamica %s.
                A aula deve pedir para o aluno traduzir frases do portugues para o ingles.

                Contextos reais do aluno:
                %s

                Responda somente com JSON valido neste formato:
                {
                  "intro": "explicacao curta em portugues",
                  "items": [
                    { "promptPt": "frase em portugues para o aluno traduzir", "expectedAnswerEn": "resposta natural em ingles" }
                  ]
                }

                Gere de 3 a 5 itens. Use situacoes de trabalho ou dia a dia com base nos contextos.
                """.formatted(dynamicType, contextsText);

        String response = aiService.complete(prompt, 900, 0.4);
        LessonPlan parsed = parseLessonPlan(response);
        if (parsed != null && !parsed.items().isEmpty()) {
            return parsed;
        }

        List<PlannedItem> fallbackItems = new ArrayList<>();
        for (Context context : contexts) {
            fallbackItems.add(new PlannedItem(
                    "Como voce diria isso em ingles, de forma natural: " + context.getContent(),
                    context.getContent()));
        }
        return new LessonPlan(
                "Hoje vamos praticar frases em ingles baseadas nos contextos que voce salvou. Traduza cada frase para uma versao natural em ingles.",
                fallbackItems);
    }

    private TeacherEvaluation evaluateAnswer(LessonItem item, String answer) {
        String prompt = """
                Voce e um professor de ingles objetivo e gentil.
                Avalie a resposta do aluno para uma atividade de traducao portugues -> ingles.

                Frase em portugues: %s
                Resposta esperada/natural: %s
                Resposta do aluno: %s

                Responda somente com JSON valido:
                { "score": 0, "feedback": "feedback em portugues com uma versao natural em ingles" }

                O score deve ser de 0 a 100.
                """.formatted(item.getPromptPt(), item.getExpectedAnswerEn(), answer);

        String response = aiService.complete(prompt, 500, 0.2);
        TeacherEvaluation parsed = parseTeacherEvaluation(response);
        if (parsed != null) {
            return parsed;
        }

        return new TeacherEvaluation(
                70,
                "Boa tentativa. Uma forma natural seria: " + item.getExpectedAnswerEn());
    }

    private String buildFinalFeedback(Lesson lesson) {
        StringBuilder answers = new StringBuilder();
        for (LessonItem item : lesson.getItems()) {
            answers.append("- Contexto: ").append(item.getContext().getContent()).append("\n")
                    .append("  Frase: ").append(item.getPromptPt()).append("\n")
                    .append("  Resposta: ").append(item.getLastAnswer()).append("\n")
                    .append("  Score: ").append(item.getScore()).append("\n");
        }

        String prompt = """
                Voce e um avaliador pedagogico de ingles.
                Analise a aula abaixo e diga, em portugues, quais contextos ainda valem praticar e quais parecem dominados.
                Seja curto e pratico.

                %s
                """.formatted(answers);

        String response = aiService.complete(prompt, 500, 0.3);
        if (response == null || response.isBlank()) {
            return "Aula finalizada. Revise os itens com menor score e mantenha esses contextos ativos para uma proxima pratica.";
        }
        return response.trim();
    }

    private LessonPlan parseLessonPlan(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            String intro = root.path("intro").asText("Vamos praticar seus contextos em ingles.");
            JsonNode itemsNode = root.path("items");
            List<PlannedItem> items = new ArrayList<>();

            if (itemsNode.isArray()) {
                for (JsonNode itemNode : itemsNode) {
                    String promptPt = itemNode.path("promptPt").asText();
                    String expectedAnswerEn = itemNode.path("expectedAnswerEn").asText();
                    if (!promptPt.isBlank() && !expectedAnswerEn.isBlank()) {
                        items.add(new PlannedItem(promptPt, expectedAnswerEn));
                    }
                }
            }

            return new LessonPlan(intro, items);
        } catch (Exception e) {
            log.warn("Could not parse lesson plan JSON: {}", e.getMessage());
            return null;
        }
    }

    private TeacherEvaluation parseTeacherEvaluation(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            int score = Math.max(0, Math.min(100, root.path("score").asInt(0)));
            String feedback = root.path("feedback").asText();
            if (feedback.isBlank()) {
                return null;
            }
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

    private LessonDTO mapToDTO(Lesson lesson) {
        List<LessonItemDTO> itemDTOs = lesson.getItems().stream()
                .map(this::mapToDTO)
                .toList();

        return new LessonDTO(
                lesson.getId(),
                lesson.getDynamicType(),
                lesson.getStatus(),
                lesson.getIntro(),
                lesson.getFinalFeedback(),
                lesson.getCreatedAt(),
                lesson.getCompletedAt(),
                itemDTOs);
    }

    private LessonItemDTO mapToDTO(LessonItem item) {
        return new LessonItemDTO(
                item.getId(),
                item.getContext().getId(),
                item.getPosition(),
                item.getPromptPt(),
                item.isCompleted() ? item.getExpectedAnswerEn() : null,
                item.getLastAnswer(),
                item.getTeacherFeedback(),
                item.getScore(),
                item.isCompleted());
    }

    private record LessonPlan(String intro, List<PlannedItem> items) {
    }

    private record PlannedItem(String promptPt, String expectedAnswerEn) {
    }

    private record TeacherEvaluation(Integer score, String feedback) {
    }
}
