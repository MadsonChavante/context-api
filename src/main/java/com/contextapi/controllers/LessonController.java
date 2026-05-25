package com.contextapi.controllers;

import com.contextapi.dtos.CreateLessonRequest;
import com.contextapi.dtos.LessonDTO;
import com.contextapi.dtos.SubmitAnswerRequest;
import com.contextapi.services.LessonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/lessons")
@AllArgsConstructor
public class LessonController {

    private final LessonService lessonService;

    @PostMapping
    public ResponseEntity<LessonDTO> create(@RequestBody(required = false) CreateLessonRequest request) {
        log.debug("POST request to create lesson");
        return ResponseEntity.status(HttpStatus.CREATED).body(lessonService.create(request));
    }

    @GetMapping("/active")
    public ResponseEntity<LessonDTO> findActive() {
        log.debug("GET request to find active lesson");
        LessonDTO activeLesson = lessonService.findActive();
        if (activeLesson == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(activeLesson);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LessonDTO> findById(@PathVariable Long id) {
        log.debug("GET request to find lesson by id: {}", id);
        return ResponseEntity.ok(lessonService.findById(id));
    }

    @PostMapping("/{id}/answers")
    public ResponseEntity<LessonDTO> submitAnswer(
            @PathVariable Long id,
            @Valid @RequestBody SubmitAnswerRequest request) {
        log.debug("POST request to submit answer for lesson id: {}", id);
        return ResponseEntity.ok(lessonService.submitAnswer(id, request));
    }

    @PostMapping("/{id}/next")
    public ResponseEntity<LessonDTO> next(@PathVariable Long id) {
        log.debug("POST request to generate next exercise for lesson id: {}", id);
        return ResponseEntity.ok(lessonService.next(id));
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<LessonDTO> finish(@PathVariable Long id) {
        log.debug("POST request to finish lesson id: {}", id);
        return ResponseEntity.ok(lessonService.finish(id));
    }
}
