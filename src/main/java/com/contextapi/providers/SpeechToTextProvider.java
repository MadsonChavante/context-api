package com.contextapi.providers;

import java.io.InputStream;

public interface SpeechToTextProvider {

    String transcribe(byte[] audioData, String audioFormat);

    boolean isConfigured();

    String getProviderName();
}
