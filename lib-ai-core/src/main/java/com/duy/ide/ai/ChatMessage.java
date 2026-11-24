package com.duy.ide.ai;

/** One turn of a conversation. Immutable. */
public final class ChatMessage {

    public enum Role {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant"),
        TOOL("tool");

        private final String wire;

        Role(String wire) {
            this.wire = wire;
        }

        /** The value used in OpenAI-compatible JSON. */
        public String wire() {
            return wire;
        }
    }

    private final Role role;
    private final String content;

    public ChatMessage(Role role, String content) {
        if (role == null) {
            throw new IllegalArgumentException("role == null");
        }
        this.role = role;
        this.content = content == null ? "" : content;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return role.wire() + ": " + content;
    }
}
