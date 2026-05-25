package com.contextapi.voice;

import com.contextapi.providers.AiProvider;
import com.contextapi.providers.SpeechToTextProvider;
import com.contextapi.providers.TextToSpeechProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class VoiceWebSocketConfig implements WebSocketConfigurer {

    private final AiProvider aiProvider;
    private final SpeechToTextProvider sttProvider;
    private final TextToSpeechProvider ttsProvider;

    public VoiceWebSocketConfig(AiProvider aiProvider,
                                 SpeechToTextProvider sttProvider,
                                 TextToSpeechProvider ttsProvider) {
        this.aiProvider = aiProvider;
        this.sttProvider = sttProvider;
        this.ttsProvider = ttsProvider;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceSessionHandler(), "/voice")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024);
        container.setMaxTextMessageBufferSize(2 * 1024 * 1024);
        container.setMaxSessionIdleTimeout(600_000L);
        return container;
    }

    @Bean
    public VoiceSessionService voiceSessionService() {
        return new VoiceSessionService(aiProvider, sttProvider, ttsProvider);
    }

    @Bean
    public VoiceSessionHandler voiceSessionHandler() {
        return new VoiceSessionHandler(voiceSessionService());
    }
}
