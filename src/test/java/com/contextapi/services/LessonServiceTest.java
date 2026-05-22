package com.contextapi.services;

import com.contextapi.dtos.CreateLessonRequest;
import com.contextapi.dtos.LessonDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LessonService")
class LessonServiceTest {

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonItemRepository lessonItemRepository;

    @Mock
    private StudentAnswerRepository studentAnswerRepository;

    @Mock
    private ContextRepository contextRepository;

    @Mock
    private AiService aiService;

    @InjectMocks
    private LessonService lessonService;

    private Context context;
    private Lesson lesson;
    private LessonItem lessonItem;

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

        lessonItem = new LessonItem();
        lessonItem.setId(1L);
        lessonItem.setLesson(lesson);
        lessonItem.setContext(context);
        lessonItem.setPosition(1);
        lessonItem.setPromptPt("Como você diz olá em inglês?");
        lessonItem.setExpectedAnswerEn("Hello");
        lessonItem.setLastAnswer(null);
        lessonItem.setTeacherFeedback(null);
        lessonItem.setScore(0);
        lessonItem.setCompleted(false);

        lesson.addItem(lessonItem);
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("should return existing active lesson if one is already in progress")
        void shouldReturnExistingActiveLesson() {
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(lesson));

            LessonDTO result = lessonService.create(null);

            assertNotNull(result);
            assertEquals(lesson.getId(), result.getId());
            verify(lessonRepository).findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS);
            verify(contextRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("should throw exception when no contexts exist")
        void shouldThrowExceptionWhenNoContextsExist() {
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            when(contextRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 5), 0));

            assertThrows(IllegalArgumentException.class, () -> lessonService.create(null));
            assertTrue(true);
        }

        @Test
        @DisplayName("should create new lesson with default dynamic type")
        void shouldCreateNewLessonWithDefaultDynamicType() {
            List<Context> contexts = List.of(context);
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            when(contextRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(contexts, PageRequest.of(0, 5), 1));
            when(aiService.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("""
                            {
                              "intro": "AI intro",
                              "items": [
                                { "promptPt": "Frase 1", "expectedAnswerEn": "Sentence 1" },
                                { "promptPt": "Frase 2", "expectedAnswerEn": "Sentence 2" }
                              ]
                            }""");

            Lesson savedLesson = new Lesson();
            savedLesson.setId(1L);
            savedLesson.setStatus(LessonStatus.IN_PROGRESS);
            savedLesson.setDynamicType("TRANSLATION_REPETITION");
            savedLesson.setIntro("AI intro");

            when(lessonRepository.save(any(Lesson.class))).thenReturn(savedLesson);

            LessonDTO result = lessonService.create(null);

            assertNotNull(result);
            assertEquals("TRANSLATION_REPETITION", result.getDynamicType());
            verify(lessonRepository).save(any(Lesson.class));
        }

        @Test
        @DisplayName("should create new lesson with custom dynamic type")
        void shouldCreateNewLessonWithCustomDynamicType() {
            CreateLessonRequest request = new CreateLessonRequest();
            request.setDynamicType("CUSTOM_TYPE");

            List<Context> contexts = List.of(context);
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            when(contextRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(contexts, PageRequest.of(0, 5), 1));
            when(aiService.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("""
                            {
                              "intro": "AI intro",
                              "items": [
                                { "promptPt": "Frase 1", "expectedAnswerEn": "Sentence 1" }
                              ]
                            }""");

            Lesson savedLesson = new Lesson();
            savedLesson.setId(1L);
            savedLesson.setStatus(LessonStatus.IN_PROGRESS);
            savedLesson.setDynamicType("CUSTOM_TYPE");
            savedLesson.setIntro("AI intro");

            when(lessonRepository.save(any(Lesson.class))).thenReturn(savedLesson);

            LessonDTO result = lessonService.create(request);

            assertNotNull(result);
            assertEquals("CUSTOM_TYPE", result.getDynamicType());
        }

        @Test
        @DisplayName("should fallback to default plan when AI response is invalid")
        void shouldFallbackWhenAiResponseInvalid() {
            List<Context> contexts = List.of(context);
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());
            when(contextRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(contexts, PageRequest.of(0, 5), 1));
            when(aiService.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("invalid json");

            Lesson savedLesson = new Lesson();
            savedLesson.setId(1L);
            savedLesson.setStatus(LessonStatus.IN_PROGRESS);
            savedLesson.setDynamicType("TRANSLATION_REPETITION");

            when(lessonRepository.save(any(Lesson.class))).thenReturn(savedLesson);

            LessonDTO result = lessonService.create(null);

            assertNotNull(result);
            verify(lessonRepository).save(any(Lesson.class));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("should return lesson DTO when lesson exists")
        void shouldReturnLessonDtoWhenExists() {
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));

            LessonDTO result = lessonService.findById(1L);

            assertNotNull(result);
            assertEquals(lesson.getId(), result.getId());
            assertEquals(lesson.getDynamicType(), result.getDynamicType());
            verify(lessonRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when lesson does not exist")
        void shouldThrowWhenLessonNotFound() {
            when(lessonRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> lessonService.findById(999L));
            verify(lessonRepository).findById(999L);
        }
    }

    @Nested
    @DisplayName("findActive")
    class FindActiveTests {

        @Test
        @DisplayName("should return active lesson DTO when one exists")
        void shouldReturnActiveLessonDtoWhenExists() {
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.of(lesson));

            LessonDTO result = lessonService.findActive();

            assertNotNull(result);
            assertEquals(lesson.getId(), result.getId());
            verify(lessonRepository).findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("should return null when no active lesson exists")
        void shouldReturnNullWhenNoActiveLessonExists() {
            when(lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS))
                    .thenReturn(Optional.empty());

            LessonDTO result = lessonService.findActive();

            assertNull(result);
            verify(lessonRepository).findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS);
        }
    }

    @Nested
    @DisplayName("submitAnswer")
    class SubmitAnswerTests {

        @Test
        @DisplayName("should throw ResourceNotFoundException when lesson does not exist")
        void shouldThrowWhenLessonNotFound() {
            SubmitAnswerRequest request = new SubmitAnswerRequest();
            request.setItemId(1L);
            request.setAnswer("Hello");

            when(lessonRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> lessonService.submitAnswer(999L, request));
        }

        @Test
        @DisplayName("should throw exception when lesson is already completed")
        void shouldThrowWhenLessonCompleted() {
            SubmitAnswerRequest request = new SubmitAnswerRequest();
            request.setItemId(1L);
            request.setAnswer("Hello");

            Lesson completedLesson = new Lesson();
            completedLesson.setId(1L);
            completedLesson.setStatus(LessonStatus.COMPLETED);

            when(lessonRepository.findById(1L)).thenReturn(Optional.of(completedLesson));

            assertThrows(IllegalArgumentException.class, () -> lessonService.submitAnswer(1L, request));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when lesson item does not exist")
        void shouldThrowWhenLessonItemNotFound() {
            SubmitAnswerRequest request = new SubmitAnswerRequest();
            request.setItemId(999L);
            request.setAnswer("Hello");

            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(lessonItemRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> lessonService.submitAnswer(1L, request));
        }

        @Test
        @DisplayName("should throw exception when lesson item does not belong to lesson")
        void shouldThrowWhenItemDoesNotBelongToLesson() {
            SubmitAnswerRequest request = new SubmitAnswerRequest();
            request.setItemId(1L);
            request.setAnswer("Hello");

            Lesson otherLesson = new Lesson();
            otherLesson.setId(2L);
            otherLesson.setStatus(LessonStatus.IN_PROGRESS);

            LessonItem itemFromOtherLesson = new LessonItem();
            itemFromOtherLesson.setId(1L);
            itemFromOtherLesson.setLesson(otherLesson);

            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(lessonItemRepository.findById(1L)).thenReturn(Optional.of(itemFromOtherLesson));

            assertThrows(IllegalArgumentException.class, () -> lessonService.submitAnswer(1L, request));
        }

        @Test
        @DisplayName("should save answer with evaluation when submission is valid")
        void shouldSaveAnswerWithEvaluation() {
            SubmitAnswerRequest request = new SubmitAnswerRequest();
            request.setItemId(1L);
            request.setAnswer("  Hello  ");

            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(lessonItemRepository.findById(1L)).thenReturn(Optional.of(lessonItem));
            when(aiService.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("""
                            {
                              "score": 85,
                              "feedback": "Great! 'Hello' is perfect."
                            }""");
            when(lessonItemRepository.save(any(LessonItem.class))).thenReturn(lessonItem);
            when(studentAnswerRepository.save(any(StudentAnswer.class))).thenReturn(new StudentAnswer());

            LessonDTO result = lessonService.submitAnswer(1L, request);

            assertNotNull(result);
            verify(lessonItemRepository).save(any(LessonItem.class));
            verify(studentAnswerRepository).save(any(StudentAnswer.class));

            ArgumentCaptor<LessonItem> itemCaptor = ArgumentCaptor.forClass(LessonItem.class);
            verify(lessonItemRepository).save(itemCaptor.capture());
            LessonItem savedItem = itemCaptor.getValue();
            assertEquals("Hello", savedItem.getLastAnswer());
            assertTrue(savedItem.isCompleted());
        }

        @Test
        @DisplayName("should use fallback evaluation when AI response is invalid")
        void shouldUseFallbackEvaluationWhenAiResponseInvalid() {
            SubmitAnswerRequest request = new SubmitAnswerRequest();
            request.setItemId(1L);
            request.setAnswer("Hello");

            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(lessonItemRepository.findById(1L)).thenReturn(Optional.of(lessonItem));
            when(aiService.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("invalid json");
            when(lessonItemRepository.save(any(LessonItem.class))).thenReturn(lessonItem);
            when(studentAnswerRepository.save(any(StudentAnswer.class))).thenReturn(new StudentAnswer());

            LessonDTO result = lessonService.submitAnswer(1L, request);

            assertNotNull(result);
            verify(lessonItemRepository).save(any(LessonItem.class));
            verify(studentAnswerRepository).save(any(StudentAnswer.class));

            ArgumentCaptor<LessonItem> itemCaptor = ArgumentCaptor.forClass(LessonItem.class);
            verify(lessonItemRepository).save(itemCaptor.capture());
            LessonItem savedItem = itemCaptor.getValue();
            assertEquals(70, savedItem.getScore());
        }
    }

    @Nested
    @DisplayName("finish")
    class FinishTests {

        @Test
        @DisplayName("should throw ResourceNotFoundException when lesson does not exist")
        void shouldThrowWhenLessonNotFound() {
            when(lessonRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> lessonService.finish(999L));
        }

        @Test
        @DisplayName("should mark lesson as completed with final feedback")
        void shouldMarkLessonAsCompletedWithFeedback() {
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(aiService.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("Great performance!");
            when(lessonRepository.save(any(Lesson.class))).thenReturn(lesson);

            LessonDTO result = lessonService.finish(1L);

            assertNotNull(result);
            ArgumentCaptor<Lesson> lessonCaptor = ArgumentCaptor.forClass(Lesson.class);
            verify(lessonRepository).save(lessonCaptor.capture());
            Lesson savedLesson = lessonCaptor.getValue();
            assertEquals(LessonStatus.COMPLETED, savedLesson.getStatus());
            assertNotNull(savedLesson.getCompletedAt());
            assertNotNull(savedLesson.getFinalFeedback());
        }

        @Test
        @DisplayName("should use default feedback when AI response is null")
        void shouldUseDefaultFeedbackWhenAiResponseIsNull() {
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(aiService.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn(null);
            when(lessonRepository.save(any(Lesson.class))).thenReturn(lesson);

            LessonDTO result = lessonService.finish(1L);

            assertNotNull(result);
            ArgumentCaptor<Lesson> lessonCaptor = ArgumentCaptor.forClass(Lesson.class);
            verify(lessonRepository).save(lessonCaptor.capture());
            Lesson savedLesson = lessonCaptor.getValue();
            assertEquals(LessonStatus.COMPLETED, savedLesson.getStatus());
            assertTrue(savedLesson.getFinalFeedback().contains("Aula finalizada"));
        }
    }
}
