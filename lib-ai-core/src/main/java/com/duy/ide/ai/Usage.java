package com.duy.ide.ai;

/** Token accounting reported by a provider, when it reports any. */
public final class Usage {

    public static final Usage UNKNOWN = new Usage(-1, -1);

    private final int promptTokens;
    private final int completionTokens;

    public Usage(int promptTokens, int completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        if (promptTokens < 0 || completionTokens < 0) {
            return -1;
        }
        return promptTokens + completionTokens;
    }

    public boolean isKnown() {
        return promptTokens >= 0 || completionTokens >= 0;
    }

    @Override
    public String toString() {
        return "Usage{prompt=" + promptTokens + ", completion=" + completionTokens + '}';
    }
}
