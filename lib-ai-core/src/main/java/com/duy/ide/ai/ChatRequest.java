package com.duy.ide.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An immutable chat-completion request, built through {@link Builder}. */
public final class ChatRequest {

    private final String model;
    private final List<ChatMessage> messages;
    private final double temperature;
    private final int maxTokens;
    private final boolean stream;

    private ChatRequest(Builder b) {
        this.model = b.model;
        this.messages = Collections.unmodifiableList(new ArrayList<ChatMessage>(b.messages));
        this.temperature = b.temperature;
        this.maxTokens = b.maxTokens;
        this.stream = b.stream;
    }

    public static Builder builder(String model) {
        return new Builder(model);
    }

    public String getModel() {
        return model;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public boolean isStream() {
        return stream;
    }

    public static final class Builder {

        private final String model;
        private final List<ChatMessage> messages = new ArrayList<ChatMessage>();
        private double temperature = 0.2d;
        private int maxTokens = 4096;
        private boolean stream = true;

        private Builder(String model) {
            if (model == null || model.length() == 0) {
                throw new IllegalArgumentException("model must not be empty");
            }
            this.model = model;
        }

        public Builder message(ChatMessage message) {
            if (message == null) {
                throw new IllegalArgumentException("message == null");
            }
            messages.add(message);
            return this;
        }

        public Builder system(String content) {
            return message(ChatMessage.system(content));
        }

        public Builder user(String content) {
            return message(ChatMessage.user(content));
        }

        public Builder assistant(String content) {
            return message(ChatMessage.assistant(content));
        }

        public Builder temperature(double value) {
            this.temperature = value;
            return this;
        }

        public Builder maxTokens(int value) {
            this.maxTokens = value;
            return this;
        }

        public Builder stream(boolean value) {
            this.stream = value;
            return this;
        }

        public ChatRequest build() {
            if (messages.isEmpty()) {
                throw new IllegalStateException("request has no messages");
            }
            return new ChatRequest(this);
        }
    }
}
