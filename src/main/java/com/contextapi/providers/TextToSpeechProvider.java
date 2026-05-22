package com.contextapi.providers;

public interface TextToSpeechProvider {

    byte[] synthesize(String text);

    String getAudioFormat();

    boolean isConfigured();

    String getProviderName();
}
