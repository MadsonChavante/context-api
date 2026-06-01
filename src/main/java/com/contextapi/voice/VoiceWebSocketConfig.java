package com.contextapi.voice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class VoiceWebSocketConfig implements WebSocketConfigurer {

    private final VoiceSessionService voiceSessionService;

    public VoiceWebSocketConfig(VoiceSessionService voiceSessionService) {
        this.voiceSessionService = voiceSessionService;
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
    public VoiceSessionHandler voiceSessionHandler() {
        return new VoiceSessionHandler(voiceSessionService);
    }
}
