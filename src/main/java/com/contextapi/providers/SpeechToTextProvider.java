package com.contextapi.providers;

public interface SpeechToTextProvider {

    String transcribe(byte[] audioData, String audioFormat);

    boolean isConfigured();

    String getProviderName();
}
