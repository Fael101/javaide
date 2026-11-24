package com.duy.ide.ai;

/**
 * A chat-completion backend.
 * <p>
 * Implementations are blocking: {@link #stream} performs network I/O and returns only when the
 * answer is complete, cancelled, or failed. Callers must invoke it off the main thread.
 * Implementations must not throw; every failure is reported through
 * {@link ChatCallback#onError(AiError)}.
 */
public interface AiProvider {

    /** Stable identifier, for example {@code "openai-compatible"}. */
    String id();

    /**
     * Runs one request to completion.
     *
     * @param request  the conversation to send
     * @param callback receives deltas and the terminal event
     * @param cancel   optional cancellation signal; {@code null} means "cannot be cancelled"
     */
    void stream(ChatRequest request, ChatCallback callback, CancelSignal cancel);
}
