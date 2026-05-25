package com.contextapi.services;

import com.contextapi.dtos.*;
import com.contextapi.entities.*;
import com.contextapi.enums.LessonStatus;
import com.contextapi.exceptions.ResourceNotFoundException;
import com.contextapi.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LessonService")
class LessonServiceTest {

    @Mock private LessonRepository lessonRepository;
    @Mock private LessonExerciseRepository exerciseRepository;
    @Mock private ContextStatsRepository contextStatsRepository;
    @Mock private ContextRepository contextRepository;
    @Mock private AiService aiService;

    @InjectMocks
    private LessonService lessonService;

    private Context context;
    private Lesson lesson;
    private LessonExercise exercise;

    @BeforeEach
    void setUp() {
        context = new Context();
        context.setId(1L);
        context.setContent("How to say hello in English");

        lesson = new Lesson();
        lesson.setId(1L);
        lesson.setStatus(LessonStatus.IN_PROGRESS);
        lesson.setDynamicType("TRANSLATION_REPETITION");
        lesson.setIntro("Test intro");

        exercise = new LessonExercise();
        exercise.setId(1L);
        exercise.setLesson(lesson);
        exercise.setContext(context);
        exercise.setPromptPt("Como voce diz ola em ingles?");
        exercise.setExpectedAnswerEn("Hello");
        exercise.setAnswered(false);

        lesson.getExercises().add(exercise);
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("should return existing active lesson")
        void shouldReturnExistingActiveLesson() {
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(lesson));

            LessonDTO result = lessonService.create(null);

            assertNotNull(result);
            assertEquals(lesson.getId(), result.getId());
            verify(contextRepository, never()).findAll();
        }

        @Test
        @DisplayName("should throw exception when no contexts exist")
        void shouldThrowWhenNoContextsExist() {
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            when(contextRepository.findAll()).thenReturn(List.of());

            assertThrows(IllegalArgumentException.class, () -> lessonService.create(null));
        }

        @Test
        @DisplayName("should create new lesson with intro and first exercise")
        void shouldCreateNewLesson() {
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            when(contextRepository.findAll()).thenReturn(List.of(context));
            when(aiService.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("""
                            {
                              "intro": "Hello! I'm Teacher!",
                              "exercise": {
                                "contextId": 1,
                                "promptPt": "Frase em portugues",
                                "expectedAnswerEn": "English phrase",
                                "variationNote": ""
                              }
                            }""");

            when(lessonRepository.save(any(Lesson.class))).thenAnswer(inv -> {
                Lesson l = inv.getArgument(0);
                if (l.getId() == null) l.setId(1L);
                return l;
            });

            LessonDTO result = lessonService.create(null);

            assertNotNull(result);
            assertEquals("TRANSLATION_REPETITION", result.getDynamicType());
            assertEquals("Hello! I'm Teacher!", result.getIntro());
            assertNotNull(result.getCurrentExercise());
        }

        @Test
        @DisplayName("should fallback when AI returns invalid JSON")
        void shouldFallbackWhenAiInvalid() {
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            when(contextRepository.findAll()).thenReturn(List.of(context));
            when(aiService.complete(anyString(), anyInt(), anyDouble())).thenReturn("not json");

            when(lessonRepository.save(any(Lesson.class))).thenAnswer(inv -> {
                Lesson l = inv.getArgument(0);
                if (l.getId() == null) l.setId(1L);
                return l;
            });

            LessonDTO result = lessonService.create(null);
            assertNotNull(result);
            assertNotNull(result.getIntro());
            assertNotNull(result.getCurrentExercise());
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("should return lesson DTO when exists")
        void shouldReturnLessonDto() {
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(contextStatsRepository.findByLessonId(1L)).thenReturn(List.of());

            LessonDTO result = lessonService.findById(1L);

            assertNotNull(result);
            assertEquals(lesson.getId(), result.getId());
        }

        @Test
        @DisplayName("should throw when not found")
        void shouldThrowWhenNotFound() {
            when(lessonRepository.findById(999L)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> lessonService.findById(999L));
        }
    }

    @Nested
    @DisplayName("findActive")
    class FindActiveTests {

        @Test
        @DisplayName("should return active lesson")
        void shouldReturnActive() {
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(lesson));
            when(contextStatsRepository.findByLessonId(1L)).thenReturn(List.of());

            LessonDTO result = lessonService.findActive();
            assertNotNull(result);
        }

        @Test
        @DisplayName("should return null when none active")
        void shouldReturnNull() {
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            assertNull(lessonService.findActive());
        }
    }

    @Nested
    @DisplayName("next")
    class NextTests {

        @Test
        @DisplayName("should throw when lesson not found")
        void shouldThrowWhenNotFound() {
            when(lessonRepository.findById(999L)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> lessonService.next(999L));
        }

        @Test
        @DisplayName("should throw when lesson completed")
        void shouldThrowWhenCompleted() {
            lesson.setStatus(LessonStatus.COMPLETED);
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            assertThrows(IllegalArgumentException.class, () -> lessonService.next(1L));
        }

        @Test
        @DisplayName("should return existing pending exercise")
        void shouldReturnPendingExercise() {
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(contextStatsRepository.findByLessonId(1L)).thenReturn(List.of());

            LessonDTO result = lessonService.next(1L);
            assertNotNull(result.getCurrentExercise());
            assertFalse(result.getCurrentExercise().isAnswered());
            verify(aiService, never()).complete(anyString(), anyInt(), anyDouble());
        }

        @Test
        @DisplayName("should generate next exercise when none pending")
        void shouldGenerateNextExercise() {
            // Mark existing exercise as answered
            exercise.setAnswered(true);
            lesson.getExercises().clear();
            lesson.getExercises().add(exercise);

            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(contextRepository.findAll()).thenReturn(List.of(context));
            when(aiService.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("""
                            {
                              "contextId": 1,
                              "promptPt": "Nova frase",
                              "expectedAnswerEn": "New sentence",
                              "variationNote": "past tense"
                            }""");
            when(lessonRepository.save(any(Lesson.class))).thenReturn(lesson);
            when(exerciseRepository.findTop10ByLessonIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
            when(contextStatsRepository.findByLessonId(1L)).thenReturn(List.of());

            LessonDTO result = lessonService.next(1L);
            assertNotNull(result.getCurrentExercise());
            assertFalse(result.getCurrentExercise().isAnswered());
        }
    }

    @Nested
    @DisplayName("submitAnswer")
    class SubmitAnswerTests {

        @Test
        @DisplayName("should throw when lesson not found")
        void shouldThrowWhenLessonNotFound() {
            SubmitAnswerRequest req = new SubmitAnswerRequest();
            req.setExerciseId(1L);
            req.setAnswer("Hello");
            when(lessonRepository.findById(999L)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> lessonService.submitAnswer(999L, req));
        }

        @Test
        @DisplayName("should throw when lesson completed")
        void shouldThrowWhenCompleted() {
            lesson.setStatus(LessonStatus.COMPLETED);
            SubmitAnswerRequest req = new SubmitAnswerRequest();
            req.setExerciseId(1L);
            req.setAnswer("Hello");
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            assertThrows(IllegalArgumentException.class, () -> lessonService.submitAnswer(1L, req));
        }

        @Test
        @DisplayName("should throw when exercise not found")
        void shouldThrowWhenExerciseNotFound() {
            SubmitAnswerRequest req = new SubmitAnswerRequest();
            req.setExerciseId(999L);
            req.setAnswer("Hello");
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(exerciseRepository.findById(999L)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> lessonService.submitAnswer(1L, req));
        }

        @Test
        @DisplayName("should save answer and update stats")
        void shouldSaveAnswerAndUpdateStats() {
            SubmitAnswerRequest req = new SubmitAnswerRequest();
            req.setExerciseId(1L);
            req.setAnswer("  Hello  ");

            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(exerciseRepository.findById(1L)).thenReturn(Optional.of(exercise));
            // First call: classify (not a doubt)
            when(aiService.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("{\"isDoubt\": false, \"teacherMessage\": \"\"}")
                    // Second call: evaluate
                    .thenReturn("{\"score\": 85, \"feedback\": \"Great!\"}");
            when(exerciseRepository.save(any(LessonExercise.class))).thenReturn(exercise);
            when(contextStatsRepository.findByLessonIdAndContextId(1L, 1L))
                    .thenReturn(Optional.empty());
            when(contextStatsRepository.save(any(ContextStats.class))).thenAnswer(inv -> inv.getArgument(0));
            when(contextStatsRepository.findByLessonId(1L)).thenReturn(List.of());

            LessonDTO result = lessonService.submitAnswer(1L, req);
            assertNotNull(result);

            verify(exerciseRepository).save(any(LessonExercise.class));
            verify(contextStatsRepository).save(any(ContextStats.class));
        }

        @Test
        @DisplayName("should not count doubt as translation attempt")
        void shouldClassifyDoubtAndNotCountAsAnswer() {
            SubmitAnswerRequest req = new SubmitAnswerRequest();
            req.setExerciseId(1L);
            req.setAnswer("como falar isso em ingles?");

            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(exerciseRepository.findById(1L)).thenReturn(Optional.of(exercise));
            // Classification: it's a doubt
            when(aiService.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("{\"isDoubt\": true, \"teacherMessage\": \"Claro! A frase seria...\"}");
            when(exerciseRepository.save(any(LessonExercise.class))).thenReturn(exercise);
            when(contextStatsRepository.findByLessonId(1L)).thenReturn(List.of());

            LessonDTO result = lessonService.submitAnswer(1L, req);
            assertNotNull(result);
            assertTrue(result.isLastWasDoubt());
            assertEquals("Claro! A frase seria...", result.getLastTeacherMessage());
            // Exercise should NOT be marked answered
            assertNotNull(result.getCurrentExercise());
            assertFalse(result.getCurrentExercise().isAnswered());
            // Stats should NOT be updated
            verify(contextStatsRepository, never()).save(any(ContextStats.class));
        }

        @Test
        @DisplayName("should throw when exercise already answered")
        void shouldThrowWhenAlreadyAnswered() {
            exercise.setAnswered(true);
            SubmitAnswerRequest req = new SubmitAnswerRequest();
            req.setExerciseId(1L);
            req.setAnswer("Hello");
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(exerciseRepository.findById(1L)).thenReturn(Optional.of(exercise));
            assertThrows(IllegalArgumentException.class, () -> lessonService.submitAnswer(1L, req));
        }
    }

    @Nested
    @DisplayName("finish")
    class FinishTests {

        @Test
        @DisplayName("should throw when lesson not found")
        void shouldThrowWhenNotFound() {
            when(lessonRepository.findById(999L)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> lessonService.finish(999L));
        }

        @Test
        @DisplayName("should mark completed with feedback")
        void shouldMarkCompleted() {
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(aiService.complete(anyString(), anyInt(), anyDouble())).thenReturn("Great progress!");
            when(lessonRepository.save(any(Lesson.class))).thenReturn(lesson);
            when(contextStatsRepository.findByLessonId(1L)).thenReturn(List.of());

            LessonDTO result = lessonService.finish(1L);
            assertNotNull(result);

            verify(lessonRepository).save(any(Lesson.class));
        }

        @Test
        @DisplayName("should use default feedback when AI returns null")
        void shouldUseDefaultFeedback() {
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(aiService.complete(anyString(), anyInt(), anyDouble())).thenReturn(null);
            when(lessonRepository.save(any(Lesson.class))).thenReturn(lesson);
            when(contextStatsRepository.findByLessonId(1L)).thenReturn(List.of());

            LessonDTO result = lessonService.finish(1L);
            assertNotNull(result);
        }
    }
}
