package com.contextapi.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
public class OpenAiSTTProvider implements SpeechToTextProvider {

    private static final String STT_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final String languageCode;

    public OpenAiSTTProvider(WebClient webClient, String apiKey, String model, String languageCode) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
        this.languageCode = languageCode;
    }

    @Override
    public String transcribe(byte[] audioData, String audioFormat) {
        if (!isConfigured()) {
            log.warn("OpenAI STT not configured, skipping transcription");
            return null;
        }

        // ═══ DEBUG: salva o áudio em arquivo antes de enviar ═══
        saveAudioToFile(audioData, audioFormat != null ? audioFormat : "webm");

        try {
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("file", new ByteArrayResource(audioData) {
                @Override
                public String getFilename() {
                    return "audio." + (audioFormat != null ? audioFormat : "webm");
                }
            });
            bodyBuilder.part("model", model);
            bodyBuilder.part("language", languageCode); 

            Map<?, ?> response = webClient.post()
                    .uri(STT_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .doOnError(err -> log.error("OpenAI STT API error: {}", err.getMessage()))
                    .onErrorResume(err -> {
                        log.error("OpenAI STT full error", err);
                        return Mono.empty();
                    })
                    .block();

            if (response != null && response.containsKey("text")) {
                String transcript = (String) response.get("text");
                log.debug("OpenAI STT transcript: {}", transcript);
                if (transcript != null && !transcript.isBlank()) {
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

    /**
     * DEBUG: Salva o áudio enviado para a OpenAI em arquivo.
     * Desative depois de resolver o problema.
     */
    private void saveAudioToFile(byte[] audioData, String extension) {
        try {
            Path dir = Paths.get("debug-audio");
            Files.createDirectories(dir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            String filename = "stt-input_" + timestamp + "." + extension;
            Path filePath = dir.resolve(filename);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(audioData);
            }
            log.info("🔊 Audio salvo para debug: {} ({} bytes)", filePath.toAbsolutePath(), audioData.length);
        } catch (IOException e) {
            log.error("Falha ao salvar audio para debug", e);
        }
    }
}