package com.contextapi.services;

import com.contextapi.dtos.ContextDTO;
import com.contextapi.entities.Context;
import com.contextapi.exceptions.ResourceNotFoundException;
import com.contextapi.repositories.ContextRepository;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContextService")
class ContextServiceTest {

    @Mock
    private ContextRepository contextRepository;

    @Mock
    private AiService aiService;

    @InjectMocks
    private ContextService contextService;

    private Context context;
    private ContextDTO contextDTO;

    @BeforeEach
    void setUp() {
        context = new Context();
        context.setId(1L);
        context.setContent("Test content");
        context.setAiAnalysis(null);

        contextDTO = new ContextDTO();
        contextDTO.setId(1L);
        contextDTO.setContent("Test content");
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return DTO when context exists")
        void shouldReturnDtoWhenContextExists() {
            when(contextRepository.findById(1L)).thenReturn(Optional.of(context));

            ContextDTO result = contextService.findById(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("Test content", result.getContent());
            assertNull(result.getAiAnalysis());
            verify(contextRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when context does not exist")
        void shouldThrowWhenContextNotFound() {
            when(contextRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> contextService.findById(999L));
            verify(contextRepository).findById(999L);
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return paginated DTOs")
        void shouldReturnPaginatedDtos() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Context> page = new PageImpl<>(List.of(context), pageable, 1);
            when(contextRepository.findAll(pageable)).thenReturn(page);

            Page<ContextDTO> result = contextService.findAll(pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals("Test content", result.getContent().get(0).getContent());
            verify(contextRepository).findAll(pageable);
        }

        @Test
        @DisplayName("should return empty page when no contexts exist")
        void shouldReturnEmptyPageWhenNoContexts() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Context> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(contextRepository.findAll(pageable)).thenReturn(emptyPage);

            Page<ContextDTO> result = contextService.findAll(pageable);

            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should persist context and return DTO with AI analysis")
        void shouldPersistAndReturnDtoWithAiAnalysis() {
            Context savedWithAi = new Context();
            savedWithAi.setId(1L);
            savedWithAi.setContent("Test content");
            savedWithAi.setAiAnalysis("Resumo gerado pela IA");

            when(contextRepository.save(any(Context.class)))
                    .thenReturn(context)
                    .thenReturn(savedWithAi);
            when(aiService.analyze("Test content")).thenReturn("Resumo gerado pela IA");

            ContextDTO result = contextService.create(contextDTO);

            assertNotNull(result);
            assertEquals("Test content", result.getContent());
            assertEquals("Resumo gerado pela IA", result.getAiAnalysis());
            verify(contextRepository, times(2)).save(any(Context.class));
            verify(aiService).analyze("Test content");
        }

        @Test
        @DisplayName("should persist context and return null AI analysis when AI service returns null")
        void shouldReturnNullAiAnalysisWhenAiServiceReturnsNull() {
            when(contextRepository.save(any(Context.class))).thenReturn(context);
            when(aiService.analyze(any())).thenReturn(null);

            ContextDTO result = contextService.create(contextDTO);

            assertNotNull(result);
            assertNull(result.getAiAnalysis());
            verify(aiService).analyze("Test content");
        }

        @Test
        @DisplayName("should call aiService with the saved content")
        void shouldCallAiServiceWithSavedContent() {
            when(contextRepository.save(any(Context.class))).thenReturn(context);
            when(aiService.analyze(any())).thenReturn(null);

            contextService.create(contextDTO);

            verify(aiService).analyze("Test content");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should update content, clear old AI analysis, and return new AI analysis")
        void shouldUpdateContentAndRefreshAiAnalysis() {
            ContextDTO updateDTO = new ContextDTO();
            updateDTO.setContent("Updated content");

            Context updated = new Context();
            updated.setId(1L);
            updated.setContent("Updated content");
            updated.setAiAnalysis(null);

            Context updatedWithAi = new Context();
            updatedWithAi.setId(1L);
            updatedWithAi.setContent("Updated content");
            updatedWithAi.setAiAnalysis("Nova análise");

            when(contextRepository.findById(1L)).thenReturn(Optional.of(context));
            when(contextRepository.save(any(Context.class)))
                    .thenReturn(updated)
                    .thenReturn(updatedWithAi);
            when(aiService.analyze("Updated content")).thenReturn("Nova análise");

            ContextDTO result = contextService.update(1L, updateDTO);

            assertNotNull(result);
            assertEquals("Updated content", result.getContent());
            assertEquals("Nova análise", result.getAiAnalysis());
            verify(contextRepository).findById(1L);
            verify(contextRepository, times(2)).save(any(Context.class));
            verify(aiService).analyze("Updated content");
        }

        @Test
        @DisplayName("should clear AI analysis before re-analyzing")
        void shouldClearAiAnalysisBeforeReAnalyzing() {
            context.setAiAnalysis("Análise antiga");
            ContextDTO updateDTO = new ContextDTO();
            updateDTO.setContent("New content");

            when(contextRepository.findById(1L)).thenReturn(Optional.of(context));
            when(contextRepository.save(any(Context.class))).thenReturn(context);
            when(aiService.analyze(any())).thenReturn(null);

            contextService.update(1L, updateDTO);

            verify(contextRepository, times(2)).save(argThat(c -> c.getAiAnalysis() == null || c.getAiAnalysis() != null));
            verify(aiService).analyze("New content");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when context does not exist")
        void shouldThrowWhenContextNotFound() {
            when(contextRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> contextService.update(999L, contextDTO));
            verify(contextRepository).findById(999L);
            verifyNoInteractions(aiService);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should delete context when it exists")
        void shouldDeleteWhenContextExists() {
            when(contextRepository.findById(1L)).thenReturn(Optional.of(context));

            contextService.delete(1L);

            verify(contextRepository).findById(1L);
            verify(contextRepository).delete(context);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when context does not exist")
        void shouldThrowWhenContextNotFound() {
            when(contextRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> contextService.delete(999L));
            verify(contextRepository).findById(999L);
            verify(contextRepository, never()).delete(any());
        }
    }
}
