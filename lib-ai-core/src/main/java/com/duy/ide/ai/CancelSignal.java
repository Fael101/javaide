package com.duy.ide.ai;

/**
 * Cooperative cancellation for one provider call.
 * <p>
 * {@link #cancel()} is safe to call from any thread. A provider registers a hook with
 * {@link #setOnCancel(Runnable)} that closes the network socket, so cancelling actually stops
 * token generation instead of merely being noticed later.
 */
public final class CancelSignal {

    private volatile boolean cancelled;
    private volatile Runnable onCancel;

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
        Runnable hook = onCancel;
        if (hook != null) {
            runQuietly(hook);
        }
    }

    /**
     * Registers the socket-closing hook. If cancellation already happened, the hook runs
     * immediately, so a cancel between request start and hook registration is not lost.
     */
    public void setOnCancel(Runnable hook) {
        this.onCancel = hook;
        if (cancelled && hook != null) {
            runQuietly(hook);
        }
    }

    private static void runQuietly(Runnable hook) {
        try {
            hook.run();
        } catch (RuntimeException ignored) {
            // A failing hook must not mask the cancellation itself.
        }
    }
}
