package com.contextapi.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

@Slf4j
public class GoogleCloudTTSProvider implements TextToSpeechProvider {

    private static final String TTS_ENDPOINT = "https://texttospeech.googleapis.com/v1/text:synthesize";

    private final WebClient webClient;
    private final String apiKey;
    private final String languageCode;
    private final String voiceName;
    private final String speakingRate;

    public GoogleCloudTTSProvider(WebClient webClient, String apiKey,
                                   String languageCode, String voiceName,
                                   String speakingRate) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.languageCode = languageCode;
        this.voiceName = voiceName;
        this.speakingRate = speakingRate;
    }

    @Override
    public byte[] synthesize(String text) {
        if (!isConfigured()) {
            log.warn("Google Cloud TTS not configured, skipping synthesis");
            return null;
        }

        try {
            Map<String, Object> voiceSelection = Map.of(
                    "languageCode", languageCode,
                    "name", voiceName
            );

            Map<String, Object> audioConfig = Map.of(
                    "audioEncoding", "MP3",
                    "speakingRate", Double.parseDouble(speakingRate),
                    "pitch", 0.0
            );

            Map<String, Object> input = Map.of("text", text);

            Map<String, Object> requestBody = Map.of(
                    "input", input,
                    "voice", voiceSelection,
                    "audioConfig", audioConfig
            );

            Map<?, ?> response = webClient.post()
                    .uri(TTS_ENDPOINT + "?key=" + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(err -> {
                        log.error("Google Cloud TTS API error: {}", err.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response != null && response.containsKey("audioContent")) {
                String audioBase64 = (String) response.get("audioContent");
                if (audioBase64 != null && !audioBase64.isBlank()) {
                    log.debug("TTS synthesized {} chars -> {} bytes of audio",
                            text.length(), audioBase64.length());
                    return Base64.getDecoder().decode(audioBase64);
                }
            }

            log.warn("Google Cloud TTS returned no audio content");
            return null;
        } catch (Exception e) {
            log.error("Failed to synthesize speech with Google Cloud TTS: {}", e.getMessage(), e);
            return null;
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
        return "GoogleCloudTTS";
    }
}
