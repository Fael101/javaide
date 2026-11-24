package com.duy.ide.ai;

import com.duy.ide.ai.prompt.SystemPrompts;

/**
 * Everything needed to make a request, minus the conversation itself.
 * <p>
 * This is plain data: it has no Android dependencies so it can be unit-tested and shared. The
 * app layer is responsible for persisting it and for protecting {@link #getApiKey()}.
 */
public final class AiSettings {

    public static final String BASE_OPENROUTER = "https://openrouter.ai/api/v1";
    public static final String BASE_OPENAI = "https://api.openai.com/v1";
    public static final String BASE_GROQ = "https://api.groq.com/openai/v1";
    public static final String BASE_DEEPSEEK = "https://api.deepseek.com/v1";
    public static final String BASE_OLLAMA = "http://127.0.0.1:11434/v1";
    public static final String BASE_NVIDIA = "https://integrate.api.nvidia.com/v1";

    /** Fast general model on NVIDIA NIM; about three seconds warm. */
    public static final String MODEL_NVIDIA_DEFAULT = "nvidia/nemotron-3.5-lightning-30b-a3b";

    private String providerId = "nvidia";
    private String baseUrl = BASE_NVIDIA;
    private String apiKey = "";
    private String model = MODEL_NVIDIA_DEFAULT;
    private String systemPrompt = SystemPrompts.CHAT;
    private double temperature = 0.2d;
    private int maxTokens = 4096;
    private boolean stream = true;
    private boolean apiKeyRequired = true;
    private int contextBudgetTokens = 12000;

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId == null ? "" : providerId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model == null ? "" : model.trim();
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    /** False for local endpoints such as Ollama, which accept any key. */
    public boolean isApiKeyRequired() {
        return apiKeyRequired;
    }

    public void setApiKeyRequired(boolean apiKeyRequired) {
        this.apiKeyRequired = apiKeyRequired;
    }

    public int getContextBudgetTokens() {
        return contextBudgetTokens;
    }

    public void setContextBudgetTokens(int contextBudgetTokens) {
        this.contextBudgetTokens = contextBudgetTokens;
    }

    /** True when a request could be attempted with these settings. */
    public boolean isConfigured() {
        if (baseUrl.length() == 0 || model.length() == 0) {
            return false;
        }
        return apiKey.length() > 0 || !apiKeyRequired;
    }

    /** Builds the request object for a prepared conversation. */
    public ChatRequest.Builder newRequest() {
        return ChatRequest.builder(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .stream(stream);
    }

    /**
     * Never includes the key. Safe to log, and it is what the settings screen shows in
     * diagnostics.
     */
    @Override
    public String toString() {
        return "AiSettings{provider=" + providerId
                + ", baseUrl=" + baseUrl
                + ", model=" + model
                + ", apiKey=" + (apiKey.length() == 0 ? "<empty>" : "***")
                + ", stream=" + stream
                + '}';
    }
}
