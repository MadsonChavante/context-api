package com.contextapi.providers;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoOpSpeechToTextProvider implements SpeechToTextProvider {

    @Override
    public String transcribe(byte[] audioData, String audioFormat) {
        log.debug("NoOp STT: received {} bytes of {} audio", audioData.length, audioFormat);
        return null;
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
