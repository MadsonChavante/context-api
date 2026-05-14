package com.contextapi.controllers;

import com.contextapi.dtos.ContextDTO;
import com.contextapi.exceptions.ResourceNotFoundException;
import com.contextapi.services.ContextService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContextController")
class ContextControllerTest {

    @Mock
    private ContextService contextService;

    @InjectMocks
    private ContextController contextController;

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return 200 with DTO when context exists")
        void shouldReturn200WhenContextExists() {
            ContextDTO dto = new ContextDTO(1L, "Test content", "AI analysis");
            when(contextService.findById(1L)).thenReturn(dto);

            ResponseEntity<ContextDTO> result = contextController.findById(1L);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals("Test content", result.getBody().getContent());
            assertEquals("AI analysis", result.getBody().getAiAnalysis());
            verify(contextService).findById(1L);
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException when context does not exist")
        void shouldPropagateExceptionWhenNotFound() {
            when(contextService.findById(999L)).thenThrow(new ResourceNotFoundException("Context not found with id: 999"));

            assertThrows(ResourceNotFoundException.class, () -> contextController.findById(999L));
            verify(contextService).findById(999L);
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return 200 with paginated DTOs")
        void shouldReturn200WithPaginatedDtos() {
            ContextDTO dto = new ContextDTO(1L, "Test content", null);
            Page<ContextDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);
            when(contextService.findAll(any())).thenReturn(page);

            ResponseEntity<Page<ContextDTO>> result = contextController.findAll(PageRequest.of(0, 10));

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals(1, result.getBody().getTotalElements());
            verify(contextService).findAll(any());
        }

        @Test
        @DisplayName("should return 200 with empty page when no contexts exist")
        void shouldReturn200WithEmptyPage() {
            Page<ContextDTO> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            when(contextService.findAll(any())).thenReturn(emptyPage);

            ResponseEntity<Page<ContextDTO>> result = contextController.findAll(PageRequest.of(0, 10));

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should return 201 with created DTO including AI analysis")
        void shouldReturn201WithCreatedDto() {
            ContextDTO inputDTO = new ContextDTO(null, "New content", null);
            ContextDTO createdDTO = new ContextDTO(1L, "New content", "AI analysis");
            when(contextService.create(any(ContextDTO.class))).thenReturn(createdDTO);

            ResponseEntity<ContextDTO> result = contextController.create(inputDTO);

            assertEquals(HttpStatus.CREATED, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals(1L, result.getBody().getId());
            assertEquals("New content", result.getBody().getContent());
            assertEquals("AI analysis", result.getBody().getAiAnalysis());
            verify(contextService).create(any(ContextDTO.class));
        }

        @Test
        @DisplayName("should return 201 even when AI analysis is null")
        void shouldReturn201WhenAiAnalysisIsNull() {
            ContextDTO inputDTO = new ContextDTO(null, "New content", null);
            ContextDTO createdDTO = new ContextDTO(1L, "New content", null);
            when(contextService.create(any(ContextDTO.class))).thenReturn(createdDTO);

            ResponseEntity<ContextDTO> result = contextController.create(inputDTO);

            assertEquals(HttpStatus.CREATED, result.getStatusCode());
            assertNull(result.getBody().getAiAnalysis());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should return 200 with updated DTO and fresh AI analysis")
        void shouldReturn200WithUpdatedDtoAndFreshAiAnalysis() {
            ContextDTO updateDTO = new ContextDTO(1L, "Updated content", null);
            ContextDTO updatedDTO = new ContextDTO(1L, "Updated content", "Nova análise");
            when(contextService.update(eq(1L), any(ContextDTO.class))).thenReturn(updatedDTO);

            ResponseEntity<ContextDTO> result = contextController.update(1L, updateDTO);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertEquals("Updated content", result.getBody().getContent());
            assertEquals("Nova análise", result.getBody().getAiAnalysis());
            verify(contextService).update(eq(1L), any(ContextDTO.class));
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException when context does not exist")
        void shouldPropagateExceptionWhenNotFound() {
            ContextDTO updateDTO = new ContextDTO(999L, "content", null);
            when(contextService.update(eq(999L), any())).thenThrow(new ResourceNotFoundException("Context not found with id: 999"));

            assertThrows(ResourceNotFoundException.class, () -> contextController.update(999L, updateDTO));
            verify(contextService).update(eq(999L), any());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should return 204 when context is deleted successfully")
        void shouldReturn204WhenDeleted() {
            doNothing().when(contextService).delete(1L);

            ResponseEntity<Void> result = contextController.delete(1L);

            assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
            assertNull(result.getBody());
            verify(contextService).delete(1L);
        }

        @Test
        @DisplayName("should propagate ResourceNotFoundException when context does not exist")
        void shouldPropagateExceptionWhenNotFound() {
            doThrow(new ResourceNotFoundException("Context not found with id: 999")).when(contextService).delete(999L);

            assertThrows(ResourceNotFoundException.class, () -> contextController.delete(999L));
            verify(contextService).delete(999L);
        }
    }
}
