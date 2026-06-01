package com.contextapi.providers;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoOpTextToSpeechProvider implements TextToSpeechProvider {

    @Override
    public byte[] synthesize(String text) {
        log.debug("NoOp TTS: would synthesize '{}'", text.length() > 50 ? text.substring(0, 50) + "..." : text);
        return new byte[0];
    }

    @Override
    public String getAudioFormat() {
        return "mp3";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String getProviderName() {
        return "NoOp";
    }
}
