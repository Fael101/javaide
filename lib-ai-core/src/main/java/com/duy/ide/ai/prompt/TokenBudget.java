package com.duy.ide.ai.prompt;

/**
 * Rough token accounting without a tokenizer.
 * <p>
 * Four characters per token is a deliberate overestimate for English prose and a slight
 * underestimate for dense code. Being approximately right is enough: the budget exists to stop
 * a large file from crowding out the user's actual question, not to predict billing exactly.
 * A real tokenizer would add megabytes of vocabulary data to the APK for no practical gain.
 */
public final class TokenBudget {

    private static final int CHARS_PER_TOKEN = 4;

    private TokenBudget() {
    }

    public static int estimate(String text) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    /**
     * Trims text to at most {@code maxTokens}, keeping the beginning and the end and marking the
     * gap. The tail matters: closing braces, the end of a method, and the last few fields are
     * usually what a model needs to understand the file's shape.
     */
    public static String truncate(String text, int maxTokens) {
        if (text == null) {
            return "";
        }
        if (maxTokens <= 0) {
            return "";
        }
        int maxChars = maxTokens * CHARS_PER_TOKEN;
        if (text.length() <= maxChars) {
            return text;
        }
        int head = (maxChars * 2) / 3;
        int tail = maxChars - head;
        int omitted = text.length() - head - tail;
        String tailText = tail > 0 ? text.substring(text.length() - tail) : "";
        return text.substring(0, head)
                + "\n... [" + omitted + " characters omitted] ...\n"
                + tailText;
    }
}
