package com.contextapi.providers;

public interface AiProvider {

    String complete(String prompt, int maxTokens, double temperature);

    String complete(String prompt, int maxTokens, double temperature, boolean jsonMode);

    boolean isConfigured();

    String getProviderName();
}
