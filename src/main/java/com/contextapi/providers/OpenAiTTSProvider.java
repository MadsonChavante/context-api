package com.contextapi.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
public class OpenAiTTSProvider implements TextToSpeechProvider {

    private static final String TTS_ENDPOINT = "https://api.openai.com/v1/audio/speech";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final String voice;

    public OpenAiTTSProvider(WebClient webClient, String apiKey, String model, String voice) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.model = model;
        this.voice = voice;
    }

    @Override
    public byte[] synthesize(String text) {
        if (!isConfigured()) {
            log.warn("OpenAI TTS not configured, skipping synthesis");
            return new byte[0];
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", text,
                    "voice", voice,
                    "response_format", "mp3"
            );

            byte[] audio = webClient.post()
                    .uri(TTS_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .onErrorResume(err -> {
                        log.error("OpenAI TTS API error: {}", err.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (audio != null && audio.length > 0) {
                log.debug("OpenAI TTS synthesized {} chars -> {} bytes of audio", text.length(), audio.length);
                return audio;
            }

            log.warn("OpenAI TTS returned no audio content");
            return new byte[0];
        } catch (Exception e) {
            log.error("Failed to synthesize speech with OpenAI TTS: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    @Override
    public String getAudioFormat() {
        return "mp3";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getProviderName() {
        return "OpenAI TTS";
    }
}