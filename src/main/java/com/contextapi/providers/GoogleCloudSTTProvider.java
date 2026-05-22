package com.contextapi.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
public class GoogleCloudSTTProvider implements SpeechToTextProvider {

    private static final String STT_ENDPOINT = "https://speech.googleapis.com/v1/speech:recognize";

    private final WebClient webClient;
    private final String apiKey;
    private final String languageCode;
    private final String encoding;

    public GoogleCloudSTTProvider(WebClient webClient, String apiKey,
                                   String languageCode, String encoding) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.languageCode = languageCode;
        this.encoding = encoding;
    }

    @Override
    public String transcribe(byte[] audioData, String audioFormat) {
        if (!isConfigured()) {
            log.warn("Google Cloud STT not configured, skipping transcription");
            return null;
        }

        try {
            String base64Audio = Base64.getEncoder().encodeToString(audioData);

            Map<String, Object> config = Map.of(
                    "encoding", encoding,
                    "languageCode", languageCode,
                    "model", "default",
                    "enableAutomaticPunctuation", true
            );

            Map<String, Object> audio = Map.of("content", base64Audio);

            Map<String, Object> requestBody = Map.of(
                    "config", config,
                    "audio", audio
            );

            Map<?, ?> response = webClient.post()
                    .uri(STT_ENDPOINT + "?key=" + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(err -> {
                        log.error("Google Cloud STT API error: {}", err.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response != null && response.containsKey("results")) {
                List<?> results = (List<?>) response.get("results");
                if (results != null && !results.isEmpty()) {
                    Map<?, ?> firstResult = (Map<?, ?>) results.get(0);
                    List<?> alternatives = (List<?>) firstResult.get("alternatives");
                    if (alternatives != null && !alternatives.isEmpty()) {
                        Map<?, ?> bestAlternative = (Map<?, ?>) alternatives.get(0);
                        String transcript = (String) bestAlternative.get("transcript");
                        if (transcript != null && !transcript.isBlank()) {
                            log.debug("STT transcript: {}", transcript);
                            return transcript;
                        }
                    }
                }
            }

            log.warn("Google Cloud STT returned no results");
            return null;
        } catch (Exception e) {
            log.error("Failed to transcribe audio with Google Cloud STT: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getProviderName() {
        return "GoogleCloudSTT";
    }
}
