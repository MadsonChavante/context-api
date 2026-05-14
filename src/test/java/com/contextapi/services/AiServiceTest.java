package com.contextapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiService")
class AiServiceTest {

    private static final String VALID_KEY = "valid-key";
    private static final String MODEL     = "llama-3.1-8b-instant";

    // WebClient chain mocks
    @Mock private WebClient                       webClient;
    @Mock private WebClient.RequestBodyUriSpec    uriSpec;
    @Mock private WebClient.RequestBodySpec       bodySpec;
    @SuppressWarnings("rawtypes")
    @Mock private WebClient.RequestHeadersSpec    headersSpec;
    @Mock private WebClient.ResponseSpec          responseSpec;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds an AiService with the shared WebClient mock and the given API key. */
    private AiService buildService(String apiKey) {
        return new AiService(webClient, apiKey, MODEL);
    }

    /** Stubs the full WebClient call chain to return the given response map. */
    @SuppressWarnings("unchecked")
    private void stubWebClientReturning(Map<String, Object> response) {
        lenient().when(webClient.post()).thenReturn(uriSpec);
        lenient().when(uriSpec.header(anyString(), anyString())).thenReturn(bodySpec);
        lenient().when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        lenient().when(headersSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(response));
    }

    /** Stubs the full WebClient call chain to throw the given exception. */
    @SuppressWarnings("unchecked")
    private void stubWebClientThrowing(RuntimeException ex) {
        lenient().when(webClient.post()).thenReturn(uriSpec);
        lenient().when(uriSpec.header(anyString(), anyString())).thenReturn(bodySpec);
        lenient().when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        lenient().when(headersSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.bodyToMono(Map.class)).thenThrow(ex);
    }

    /** Builds a valid Groq-like response map with the given analysis text. */
    private Map<String, Object> groqResponse(String analysisText) {
        return Map.of("choices", List.of(Map.of("message", Map.of("content", analysisText))));
    }

    // -------------------------------------------------------------------------
    // analyze — API key absent
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("analyze — API key absent")
    class AnalyzeApiKeyAbsent {

        @Test
        @DisplayName("should return null and skip HTTP call when api key is blank")
        void shouldReturnNullWhenApiKeyIsBlank() {
            AiService service = buildService("");

            String result = service.analyze("some content");

            assertNull(result);
            verify(webClient, never()).post();
        }

        @Test
        @DisplayName("should return null and skip HTTP call when api key is null")
        void shouldReturnNullWhenApiKeyIsNull() {
            AiService service = buildService(null);

            String result = service.analyze("some content");

            assertNull(result);
            verify(webClient, never()).post();
        }
    }

    // -------------------------------------------------------------------------
    // analyze — successful response
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("analyze — successful response")
    class AnalyzeSuccess {

        @Test
        @DisplayName("should return the AI-generated analysis text")
        void shouldReturnAnalysisText() {
            stubWebClientReturning(groqResponse("Resumo da IA"));
            AiService service = buildService(VALID_KEY);

            String result = service.analyze("how to say hello in English?");

            assertEquals("Resumo da IA", result);
        }

        @Test
        @DisplayName("should call webClient.post() when api key is present")
        void shouldCallPostWhenApiKeyPresent() {
            stubWebClientReturning(groqResponse("ok"));
            AiService service = buildService(VALID_KEY);

            service.analyze("translate: apple");

            verify(webClient).post();
        }
    }

    // -------------------------------------------------------------------------
    // analyze — error handling
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("analyze — error handling")
    class AnalyzeErrorHandling {

        @Test
        @DisplayName("should return null when WebClient throws a runtime exception")
        void shouldReturnNullOnWebClientException() {
            stubWebClientThrowing(new RuntimeException("connection refused"));
            AiService service = buildService(VALID_KEY);

            String result = service.analyze("some content");

            assertNull(result);
        }

        @Test
        @DisplayName("should return null when Groq response has empty choices list")
        void shouldReturnNullWhenChoicesIsEmpty() {
            stubWebClientReturning(Map.of("choices", List.of()));
            AiService service = buildService(VALID_KEY);

            String result = service.analyze("content");

            assertNull(result);
        }
    }
}
