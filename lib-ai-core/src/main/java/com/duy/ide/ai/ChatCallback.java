package com.duy.ide.ai;

/**
 * Receives a response as it arrives. Exactly one of {@link #onDone} or {@link #onError} is
 * called, and never both.
 * <p>
 * Callbacks are invoked on the thread that called {@link AiProvider#stream}. Android callers
 * must therefore start the call on a worker thread and marshal UI updates themselves.
 */
public interface ChatCallback {

    /** Connection established; the first byte is expected soon. */
    void onStart();

    /** A fragment of the answer. Called zero or more times. */
    void onDelta(String text);

    /** The answer is complete. {@code fullText} is everything previously passed to onDelta. */
    void onDone(String fullText, Usage usage);

    /** The call failed. No further callbacks follow. */
    void onError(AiError error);
}
