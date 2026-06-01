package com.contextapi.services;

import com.contextapi.providers.AiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiService")
class AiServiceTest {

    @Mock
    private AiProvider aiProvider;

    private AiService buildService() {
        return new AiService(aiProvider);
    }

    @Nested
    @DisplayName("analyze — provider not configured")
    class AnalyzeProviderNotConfigured {

        @Test
        @DisplayName("should return null when provider is not configured")
        void shouldReturnNullWhenProviderNotConfigured() {
            when(aiProvider.isConfigured()).thenReturn(false);
            AiService service = buildService();

            String result = service.analyze("Analyze this content: %s", "some content");

            assertNull(result);
            verify(aiProvider).isConfigured();
            verify(aiProvider, never()).complete(anyString(), anyInt(), anyDouble());
        }
    }

    @Nested
    @DisplayName("analyze — successful response")
    class AnalyzeSuccess {

        @Test
        @DisplayName("should return the AI-generated analysis text")
        void shouldReturnAnalysisText() {
            when(aiProvider.isConfigured()).thenReturn(true);
            when(aiProvider.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("Resumo da IA");
            AiService service = buildService();

            String result = service.analyze("Analyze this content: %s", "how to say hello in English?");

            assertEquals("Resumo da IA", result);
            verify(aiProvider).isConfigured();
            verify(aiProvider).complete(anyString(), anyInt(), anyDouble());
        }

        @Test
        @DisplayName("should sanitize content before sending to provider")
        void shouldSanitizeContent() {
            when(aiProvider.isConfigured()).thenReturn(true);
            when(aiProvider.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("Resposta");
            AiService service = buildService();

            String result = service.analyze("Analyze this content: %s", "content with\n\nmultiple\n\n\nlinebreaks");

            assertNotNull(result);
            verify(aiProvider).complete(anyString(), anyInt(), anyDouble());
        }

        @Test
        @DisplayName("should truncate content longer than 1000 characters")
        void shouldTruncateContent() {
            when(aiProvider.isConfigured()).thenReturn(true);
            when(aiProvider.complete(anyString(), anyInt(), anyDouble()))
                    .thenReturn("Resposta");
            AiService service = buildService();
            String longContent = "a".repeat(2000);

            String result = service.analyze("Analyze this content: %s", longContent);

            assertNotNull(result);
            verify(aiProvider).complete(anyString(), anyInt(), anyDouble());
        }
    }

    @Nested
    @DisplayName("complete — provider not configured")
    class CompleteProviderNotConfigured {

        @Test
        @DisplayName("should return null when provider is not configured")
        void shouldReturnNullWhenProviderNotConfigured() {
            when(aiProvider.isConfigured()).thenReturn(false);
            when(aiProvider.getProviderName()).thenReturn("Test Provider");
            AiService service = buildService();

            String result = service.complete("some prompt", 100, 0.5);

            assertNull(result);
            verify(aiProvider).isConfigured();
            verify(aiProvider, never()).complete(anyString(), anyInt(), anyDouble());
        }
    }

    @Nested
    @DisplayName("complete — successful response")
    class CompleteSuccess {

        @Test
        @DisplayName("should delegate to provider with exact parameters")
        void shouldDelegateToProvider() {
            when(aiProvider.isConfigured()).thenReturn(true);
            when(aiProvider.complete("test prompt", 200, 0.8))
                    .thenReturn("Provider response");
            AiService service = buildService();

            String result = service.complete("test prompt", 200, 0.8);

            assertEquals("Provider response", result);
            verify(aiProvider).complete("test prompt", 200, 0.8);
        }
    }

    @Nested
    @DisplayName("getProviderName")
    class GetProviderName {

        @Test
        @DisplayName("should return the provider's name")
        void shouldReturnProviderName() {
            when(aiProvider.getProviderName()).thenReturn("Groq");
            AiService service = buildService();

            String name = service.getProviderName();

            assertEquals("Groq", name);
            verify(aiProvider).getProviderName();
        }
    }
}

