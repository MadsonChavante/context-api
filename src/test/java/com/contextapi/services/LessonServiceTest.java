package com.contextapi.services;

import com.contextapi.dynamics.RaptorDynamic;
import com.contextapi.dtos.*;
import com.contextapi.entities.*;
import com.contextapi.enums.ConversationAuthor;
import com.contextapi.enums.LessonStatus;
import com.contextapi.exceptions.ResourceNotFoundException;
import com.contextapi.records.HandleAnswerResult;
import com.contextapi.records.ResponseIAResult;
import com.contextapi.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LessonService")
class LessonServiceTest {

    @Mock private LessonRepository lessonRepository;
    @Mock private ContextRepository contextRepository;
    @Mock private RaptorDynamic raptorDynamic;

    @InjectMocks
    private LessonService lessonService;

    private Context context;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        context = new Context();
        context.setId(1L);
        context.setContent("How to say hello in English");

        lesson = new Lesson();
        lesson.setId(1L);
        lesson.setStatus(LessonStatus.IN_PROGRESS);
        lesson.setConversationHistory(new ArrayList<>());
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("should throw exception when no contexts exist")
        void shouldThrowWhenNoContextsExist() {
            when(contextRepository.findAll()).thenReturn(List.of());

            assertThrows(IllegalArgumentException.class,
                    () -> lessonService.create());
        }

        @Test
        @DisplayName("should create new lesson with greeting and first exercise")
        void shouldCreateNewLesson() throws Exception {
            when(contextRepository.findAll()).thenReturn(List.of(context));
            when(raptorDynamic.startLesson())
                    .thenReturn(new ResponseIAResult(1L, "Traduza: Como vai voce?"));
            when(lessonRepository.save(any(Lesson.class))).thenAnswer(inv -> {
                Lesson l = inv.getArgument(0);
                if (l.getId() == null) l.setId(1L);
                return l;
            });

            LessonDTO result = lessonService.create();

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals(LessonStatus.IN_PROGRESS, result.getStatus());

            List<ConversationMessage> history = result.getConversationHistory();
            assertNotNull(history);
            assertEquals(2, history.size());

            assertEquals(ConversationAuthor.TEACHER, history.get(0).getAuthor());
            assertEquals(ConversationMessage.MessageType.GREETING, history.get(0).getType());

            assertEquals(ConversationAuthor.TEACHER, history.get(1).getAuthor());
            assertEquals(ConversationMessage.MessageType.EXERCISE, history.get(1).getType());
            assertEquals("Traduza: Como vai voce?", history.get(1).getContent());
            assertEquals(1L, history.get(1).getContextId());

            verify(lessonRepository).save(any(Lesson.class));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("should return lesson DTO when exists")
        void shouldReturnLessonDto() {
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));

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
            assertThrows(ResourceNotFoundException.class, () -> lessonService.next(999L, "Hello"));
        }

        @Test
        @DisplayName("should throw when lesson completed")
        void shouldThrowWhenCompleted() {
            lesson.setStatus(LessonStatus.COMPLETED);
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            assertThrows(IllegalArgumentException.class, () -> lessonService.next(1L, "Hello"));
        }

        @Test
        @DisplayName("should process answer and add conversation messages")
        void shouldProcessAnswer() throws Exception {
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(raptorDynamic.handleAnswer("Hello", lesson.getConversationHistory()))
                    .thenReturn(new HandleAnswerResult("ANSWER", "Muito bem!", null, null));
            when(lessonRepository.save(any(Lesson.class))).thenReturn(lesson);

            LessonDTO result = lessonService.next(1L, "Hello");

            assertNotNull(result);

            List<ConversationMessage> history = result.getConversationHistory();
            assertNotNull(history);
            assertEquals(3, history.size());

            assertEquals(ConversationAuthor.STUDENT, history.get(0).getAuthor());
            assertEquals(ConversationMessage.MessageType.ANSWER, history.get(0).getType());
            assertEquals("Hello", history.get(0).getContent());

            assertEquals(ConversationAuthor.TEACHER, history.get(1).getAuthor());
            assertEquals(ConversationMessage.MessageType.EXERCISE, history.get(1).getType());
            assertEquals("Muito bem!", history.get(1).getContent());

            assertEquals(ConversationAuthor.TEACHER, history.get(2).getAuthor());
            assertEquals(ConversationMessage.MessageType.EXERCISE, history.get(2).getType());

            verify(lessonRepository).save(any(Lesson.class));
        }

        @Test
        @DisplayName("should process doubt and add feedback message")
        void shouldProcessDoubt() throws Exception {
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(raptorDynamic.handleAnswer("como se diz?", lesson.getConversationHistory()))
                    .thenReturn(new HandleAnswerResult("DOUBT", "A frase seria...", null, null));
            when(lessonRepository.save(any(Lesson.class))).thenReturn(lesson);

            LessonDTO result = lessonService.next(1L, "como se diz?");

            assertNotNull(result);

            List<ConversationMessage> history = result.getConversationHistory();
            assertEquals(3, history.size());
            
            assertEquals(ConversationAuthor.STUDENT, history.get(0).getAuthor());
            assertEquals(ConversationMessage.MessageType.DOUBT, history.get(0).getType());

            assertEquals(ConversationAuthor.TEACHER, history.get(1).getAuthor());
            assertEquals(ConversationMessage.MessageType.FEEDBACK, history.get(1).getType());
            
            assertEquals(ConversationAuthor.TEACHER, history.get(2).getAuthor());
            assertEquals(ConversationMessage.MessageType.EXERCISE, history.get(2).getType());
            assertEquals("A frase seria...", history.get(1).getContent());

            verify(lessonRepository).save(any(Lesson.class));
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
        @DisplayName("should mark completed")
        void shouldMarkCompleted() {
            when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
            when(lessonRepository.save(any(Lesson.class))).thenReturn(lesson);

            LessonDTO result = lessonService.finish(1L);
            assertNotNull(result);
            assertEquals(LessonStatus.COMPLETED, result.getStatus());
            verify(lessonRepository).save(any(Lesson.class));
        }
    }
}
