package com.contextapi.config;

import com.contextapi.providers.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class VoiceProviderConfig {

    @Value("${google.cloud.api.key:}")
    private String googleApiKey;

    @Value("${google.cloud.stt.language:en-US}")
    private String sttLanguage;

    @Value("${google.cloud.stt.encoding:WEBM_OPUS}")
    private String sttEncoding;

    @Value("${google.cloud.tts.language:en-US}")
    private String ttsLanguage;

    @Value("${google.cloud.tts.voice:en-US-Wavenet-D}")
    private String ttsVoice;

    @Value("${google.cloud.tts.speaking-rate:1.0}")
    private String speakingRate;

    @Bean
    public SpeechToTextProvider speechToTextProvider(WebClient webClient) {
        if (googleApiKey != null && !googleApiKey.isBlank()) {
            return new GoogleCloudSTTProvider(webClient, googleApiKey, sttLanguage, sttEncoding);
        }
        return new NoOpSpeechToTextProvider();
    }

    @Bean
    public TextToSpeechProvider textToSpeechProvider(WebClient webClient) {
        if (googleApiKey != null && !googleApiKey.isBlank()) {
            return new GoogleCloudTTSProvider(webClient, googleApiKey, ttsLanguage, ttsVoice, speakingRate);
        }
        return new NoOpTextToSpeechProvider();
    }
}
