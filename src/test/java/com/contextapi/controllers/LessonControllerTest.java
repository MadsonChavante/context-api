package com.contextapi.controllers;

import com.contextapi.dtos.CreateLessonRequest;
import com.contextapi.dtos.LessonDTO;
import com.contextapi.dtos.SubmitAnswerRequest;
import com.contextapi.services.LessonService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LessonController Tests")
class LessonControllerTest {

    @Mock
    private LessonService lessonService;

    @InjectMocks
    private LessonController lessonController;

    @Test
    @DisplayName("POST /lessons - Should create lesson successfully")
    void create_ShouldReturnCreatedLesson() throws Exception {
        // Arrange
        LessonDTO expectedDto = new LessonDTO();
        expectedDto.setId(1L);
        when(lessonService.create()).thenReturn(expectedDto);

        // Act
        ResponseEntity<LessonDTO> response = lessonController.create(null);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        verify(lessonService, times(1)).create();
    }

    @Test
    @DisplayName("POST /lessons with request body - Should create lesson successfully")
    void create_WithRequestBody_ShouldReturnCreatedLesson() throws Exception {
        // Arrange
        CreateLessonRequest request = new CreateLessonRequest();
        LessonDTO expectedDto = new LessonDTO();
        expectedDto.setId(1L);
        when(lessonService.create()).thenReturn(expectedDto);

        // Act
        ResponseEntity<LessonDTO> response = lessonController.create(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(lessonService, times(1)).create();
    }

    @Test
    @DisplayName("GET /lessons/active - Should return active lesson when exists")
    void findActive_WhenLessonExists_ShouldReturnLesson() {
        // Arrange
        LessonDTO expectedDto = new LessonDTO();
        expectedDto.setId(1L);
        when(lessonService.findActive()).thenReturn(expectedDto);

        // Act
        ResponseEntity<LessonDTO> response = lessonController.findActive();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        verify(lessonService, times(1)).findActive();
    }

    @Test
    @DisplayName("GET /lessons/active - Should return no content when no active lesson")
    void findActive_WhenNoActiveLesson_ShouldReturnNoContent() {
        // Arrange
        when(lessonService.findActive()).thenReturn(null);

        // Act
        ResponseEntity<LessonDTO> response = lessonController.findActive();

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
        verify(lessonService, times(1)).findActive();
    }

    @Test
    @DisplayName("GET /lessons/{id} - Should return lesson by id")
    void findById_ShouldReturnLesson() {
        // Arrange
        Long lessonId = 1L;
        LessonDTO expectedDto = new LessonDTO();
        expectedDto.setId(lessonId);
        when(lessonService.findById(lessonId)).thenReturn(expectedDto);

        // Act
        ResponseEntity<LessonDTO> response = lessonController.findById(lessonId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(lessonId, response.getBody().getId());
        verify(lessonService, times(1)).findById(lessonId);
    }

    @Test
    @DisplayName("POST /lessons/{id}/next - Should generate next interaction")
    void next_ShouldReturnUpdatedLesson() throws Exception {
        // Arrange
        Long lessonId = 1L;
        String answer = "Test answer";
        SubmitAnswerRequest request = new SubmitAnswerRequest();
        request.setAnswer(answer);
        
        LessonDTO expectedDto = new LessonDTO();
        expectedDto.setId(lessonId);
        when(lessonService.next(anyLong(), anyString())).thenReturn(expectedDto);

        // Act
        ResponseEntity<LessonDTO> response = lessonController.next(lessonId, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(lessonService, times(1)).next(lessonId, answer);
    }

    @Test
    @DisplayName("POST /lessons/{id}/finish - Should finish lesson successfully")
    void finish_ShouldReturnFinishedLesson() {
        // Arrange
        Long lessonId = 1L;
        LessonDTO expectedDto = new LessonDTO();
        expectedDto.setId(lessonId);
        when(lessonService.finish(lessonId)).thenReturn(expectedDto);

        // Act
        ResponseEntity<LessonDTO> response = lessonController.finish(lessonId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(lessonId, response.getBody().getId());
        verify(lessonService, times(1)).finish(lessonId);
    }
}
