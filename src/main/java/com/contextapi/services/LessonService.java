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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
public class LessonService {

    private static final String LESSON_NOT_FOUND = "Lesson not found with id: %d";

    private final LessonRepository lessonRepository;
    private final ContextRepository contextRepository;
    private final RaptorDynamic raptorDynamic;

    public LessonService(
            LessonRepository lessonRepository,
            ContextRepository contextRepository,
            RaptorDynamic raptorDynamic) {
        this.lessonRepository = lessonRepository;
        this.contextRepository = contextRepository;
        this.raptorDynamic = raptorDynamic;
    }

    public String startVoice() {
        Lesson lesson = lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS)
                .orElseThrow(() -> new IllegalStateException("No active lesson"));

        return raptorDynamic.startVoice(lesson.getConversationHistory());
    }

    public LessonDTO create() throws Exception {
        List<Context> contexts = contextRepository.findAll();
        if (contexts.isEmpty()) {
            throw new IllegalArgumentException("Create at least one context before starting a lesson");
        }

        Lesson lesson = new Lesson();

        ConversationMessage conversationMessage = new ConversationMessage();
        conversationMessage.setAuthor(ConversationAuthor.TEACHER);
        conversationMessage.setType(ConversationMessage.MessageType.GREETING);
        conversationMessage.setContent(RaptorDynamic.INTRO);
        lesson.getConversationHistory().add(conversationMessage);

        ResponseIAResult result = raptorDynamic.startLesson();

        conversationMessage = new ConversationMessage();
        conversationMessage.setAuthor(ConversationAuthor.TEACHER);
        conversationMessage.setType(ConversationMessage.MessageType.EXERCISE);
        conversationMessage.setContent(result.response());
        conversationMessage.setContextId(result.contextId());
        lesson.getConversationHistory().add(conversationMessage);

        lesson = lessonRepository.save(lesson);

        return mapToDTO(lesson);
    }

    public LessonDTO next(Long lessonId, String answer) throws Exception {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException(LESSON_NOT_FOUND.formatted(lessonId)));

        if (lesson.getStatus() == LessonStatus.COMPLETED) {
            throw new IllegalArgumentException("This lesson is already completed");
        }

        HandleAnswerResult handleAnswerResult = raptorDynamic.handleAnswer(answer, lesson.getConversationHistory());

        ConversationMessage studentMessage = new ConversationMessage();
        studentMessage.setAuthor(ConversationAuthor.STUDENT);
        studentMessage.setType(handleAnswerResult.AnswerType().equals("DOUBT") ? ConversationMessage.MessageType.DOUBT
                : ConversationMessage.MessageType.ANSWER);
        studentMessage.setContent(answer);
        lesson.getConversationHistory().add(studentMessage);

        ConversationMessage teacherMessage = new ConversationMessage();
        teacherMessage.setAuthor(ConversationAuthor.TEACHER);
        teacherMessage.setType(handleAnswerResult.AnswerType().equals("DOUBT") ? ConversationMessage.MessageType.FEEDBACK
                : ConversationMessage.MessageType.EXERCISE);
        teacherMessage.setContent(handleAnswerResult.response());
        lesson.getConversationHistory().add(teacherMessage);

        ConversationMessage nextTeacherMessage = new ConversationMessage();
        nextTeacherMessage.setAuthor(ConversationAuthor.TEACHER);
        nextTeacherMessage.setType(ConversationMessage.MessageType.EXERCISE);
        nextTeacherMessage.setContextId(handleAnswerResult.nextContextId());
        nextTeacherMessage.setContent(handleAnswerResult.next());
        lesson.getConversationHistory().add(nextTeacherMessage);

        lessonRepository.save(lesson);
        return mapToDTO(lesson);
    }

    public LessonDTO finish(Long id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(LESSON_NOT_FOUND.formatted(id)));

        lesson.setStatus(LessonStatus.COMPLETED);
        lesson.setCompletedAt(LocalDateTime.now());

        return mapToDTO(lessonRepository.save(lesson));
    }

    @Transactional(readOnly = true)
    public LessonDTO findById(Long id) {
        return lessonRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException(LESSON_NOT_FOUND.formatted(id)));
    }

    @Transactional(readOnly = true)
    public LessonDTO findActive() {
        return lessonRepository.findFirstByStatusOrderByCreatedAtDesc(LessonStatus.IN_PROGRESS)
                .map(this::mapToDTO)
                .orElse(null);
    }

    private LessonDTO mapToDTO(Lesson lesson) {
        LessonDTO lessonDTO = new LessonDTO();
        lessonDTO.setId(lesson.getId());
        lessonDTO.setStatus(lesson.getStatus());
        lessonDTO.setCreatedAt(lesson.getCreatedAt());
        lessonDTO.setCompletedAt(lesson.getCompletedAt());
        lessonDTO.setConversationHistory(lesson.getConversationHistory());
        return lessonDTO;
    }

}
