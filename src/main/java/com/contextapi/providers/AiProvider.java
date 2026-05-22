package com.contextapi.providers;

public interface AiProvider {

    String complete(String prompt, int maxTokens, double temperature);

    boolean isConfigured();

    String getProviderName();
}
