package com.contextapi.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
public class OpenAiSTTProvider implements SpeechToTextProvider {

    private static final String STT_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public OpenAiSTTProvider(WebClient webClient, String apiKey, String model) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String transcribe(byte[] audioData, String audioFormat) {
        if (!isConfigured()) {
            log.warn("OpenAI STT not configured, skipping transcription");
            return null;
        }

        try {
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("file", new ByteArrayResource(audioData) {
                @Override
                public String getFilename() {
                    return "audio." + (audioFormat != null ? audioFormat : "webm");
                }
            });
            bodyBuilder.part("model", model);

            Map<?, ?> response = webClient.post()
                    .uri(STT_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(err -> {
                        log.error("OpenAI STT API error: {}", err.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response != null && response.containsKey("text")) {
                String transcript = (String) response.get("text");
                if (transcript != null && !transcript.isBlank()) {
                    log.debug("OpenAI STT transcript: {}", transcript);
                    return transcript;
                }
            }

            log.warn("OpenAI STT returned no text");
            return null;
        } catch (Exception e) {
            log.error("Failed to transcribe audio with OpenAI STT: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getProviderName() {
        return "OpenAI STT";
    }
}