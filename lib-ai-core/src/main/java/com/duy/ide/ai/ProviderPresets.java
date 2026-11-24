package com.duy.ide.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Known endpoints, so the settings screen can offer a dropdown instead of asking the user to
 * type a base URL. Every entry is the same wire format; only the URL, the key and the default
 * model differ.
 */
public final class ProviderPresets {

    public static final class Preset {

        private final String id;
        private final String label;
        private final String baseUrl;
        private final String defaultModel;
        private final boolean apiKeyRequired;

        Preset(String id, String label, String baseUrl, String defaultModel,
               boolean apiKeyRequired) {
            this.id = id;
            this.label = label;
            this.baseUrl = baseUrl;
            this.defaultModel = defaultModel;
            this.apiKeyRequired = apiKeyRequired;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public boolean isApiKeyRequired() {
            return apiKeyRequired;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final List<Preset> PRESETS;

    static {
        List<Preset> presets = new ArrayList<Preset>();
        presets.add(new Preset("nvidia", "NVIDIA NIM", AiSettings.BASE_NVIDIA,
                AiSettings.MODEL_NVIDIA_DEFAULT, true));
        presets.add(new Preset("openrouter", "OpenRouter", AiSettings.BASE_OPENROUTER,
                "openai/gpt-4o-mini", true));
        presets.add(new Preset("openai", "OpenAI", AiSettings.BASE_OPENAI,
                "gpt-4o-mini", true));
        presets.add(new Preset("groq", "Groq", AiSettings.BASE_GROQ,
                "llama-3.3-70b-versatile", true));
        presets.add(new Preset("deepseek", "DeepSeek", AiSettings.BASE_DEEPSEEK,
                "deepseek-chat", true));
        presets.add(new Preset("ollama", "Ollama (local)", AiSettings.BASE_OLLAMA,
                "llama3", false));
        PRESETS = Collections.unmodifiableList(presets);
    }

    private ProviderPresets() {
    }

    public static List<Preset> all() {
        return PRESETS;
    }

    public static Preset byId(String id) {
        if (id == null) {
            return null;
        }
        for (Preset preset : PRESETS) {
            if (preset.getId().equals(id)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * Applies a preset's endpoint and key requirement. The model is only replaced when it is
     * empty, so switching provider does not silently overwrite a model the user typed.
     */
    public static void apply(AiSettings settings, Preset preset) {
        if (settings == null || preset == null) {
            return;
        }
        settings.setProviderId(preset.getId());
        settings.setBaseUrl(preset.getBaseUrl());
        settings.setApiKeyRequired(preset.isApiKeyRequired());
        if (settings.getModel() == null || settings.getModel().length() == 0) {
            settings.setModel(preset.getDefaultModel());
        }
    }
}
