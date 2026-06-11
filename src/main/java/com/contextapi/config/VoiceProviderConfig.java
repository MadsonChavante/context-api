package com.contextapi.config;

import com.contextapi.providers.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class VoiceProviderConfig {

    @Value("${voice.stt.provider:google}")
    private String sttProvider;

    @Value("${voice.tts.provider:google}")
    private String ttsProvider;

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

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @Value("${openai.stt.model:whisper-1}")
    private String openaiSttModel;

    @Value("${openai.tts.model:tts-1}")
    private String openaiTtsModel;

    @Value("${openai.tts.voice:alloy}")
    private String openaiTtsVoice;

    @Value("${openai.stt.language:en}")
    private String openaiSttLanguage;

    @Value("${openrouter.api.key:}")
    private String openrouterApiKey;

    @Value("${openrouter.tts.model:x-ai/grok-voice-tts-1.0}")
    private String openrouterTtsModel;

    @Value("${openrouter.tts.voice:rex}")
    private String openrouterTtsVoice;

    @Bean
    public SpeechToTextProvider speechToTextProvider(WebClient webClient) {
        if ("openai".equalsIgnoreCase(sttProvider) && openaiApiKey != null && !openaiApiKey.isBlank()) {
            return new OpenAiSTTProvider(webClient, openaiApiKey, openaiSttModel, openaiSttLanguage);
        } else if (googleApiKey != null && !googleApiKey.isBlank()) {
            return new GoogleCloudSTTProvider(webClient, googleApiKey, sttLanguage, sttEncoding);
        }

        return new NoOpSpeechToTextProvider();
    }

    @Bean
    public TextToSpeechProvider textToSpeechProvider(WebClient webClient) {
        if ("openai".equalsIgnoreCase(ttsProvider) && openaiApiKey != null && !openaiApiKey.isBlank()) {
            return new OpenAiTTSProvider(webClient, openaiApiKey, openaiTtsModel, openaiTtsVoice);
        } else if ("openrouter".equalsIgnoreCase(ttsProvider) && openrouterApiKey != null && !openrouterApiKey.isBlank()) {
            return new OpenRouterTTSProvider(webClient, openrouterApiKey, openrouterTtsModel, openrouterTtsVoice);
        } else if (googleApiKey != null && !googleApiKey.isBlank()) {
            return new GoogleCloudTTSProvider(webClient, googleApiKey, ttsLanguage, ttsVoice, speakingRate);
        }

        return new NoOpTextToSpeechProvider();
    }
}

