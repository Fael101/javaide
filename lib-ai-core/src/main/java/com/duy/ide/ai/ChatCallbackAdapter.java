package com.duy.ide.ai;

/** Convenience base class for callers that only care about a subset of the callbacks. */
public abstract class ChatCallbackAdapter implements ChatCallback {

    @Override
    public void onStart() {
    }

    @Override
    public void onDelta(String text) {
    }

    @Override
    public void onDone(String fullText, Usage usage) {
    }

    @Override
    public void onError(AiError error) {
    }
}
