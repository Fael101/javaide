package com.duy.ide.ai;

/**
 * A failure that can be shown to the user without leaking provider internals.
 * <p>
 * Providers map HTTP status codes and transport failures onto {@link Kind}, so the UI can react
 * (prompt for a key, offer retry) without parsing strings.
 */
public class AiError extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        /** Key missing, invalid, or lacking permission. */
        AUTH,
        /** Account has no remaining credit or quota. */
        QUOTA,
        /** Too many requests; retryable. */
        RATE_LIMIT,
        /** Malformed request, unknown model, unsupported parameter. */
        BAD_REQUEST,
        /** Provider-side failure; retryable. */
        SERVER,
        /** Could not reach the host. */
        NETWORK,
        /** Connection established but no data in time. */
        TIMEOUT,
        /** Response was not in the expected shape. */
        PARSE,
        /** Cancelled by the caller. */
        CANCELLED,
        UNKNOWN
    }

    private final Kind kind;
    private final int httpCode;
    private final int retryAfterSeconds;

    public AiError(Kind kind, String message) {
        this(kind, 0, message, -1, null);
    }

    public AiError(Kind kind, int httpCode, String message) {
        this(kind, httpCode, message, -1, null);
    }

    public AiError(Kind kind, int httpCode, String message, int retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.kind = kind == null ? Kind.UNKNOWN : kind;
        this.httpCode = httpCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Kind getKind() {
        return kind;
    }

    public int getHttpCode() {
        return httpCode;
    }

    /** Seconds requested by a Retry-After header, or -1 when absent. */
    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public boolean isRetryable() {
        return kind == Kind.RATE_LIMIT
                || kind == Kind.SERVER
                || kind == Kind.NETWORK
                || kind == Kind.TIMEOUT;
    }

    public String getUserMessage() {
        switch (kind) {
            case AUTH:
                return "The API key was rejected. Check the key in AI settings.";
            case QUOTA:
                return "The provider reports no remaining credit or quota.";
            case RATE_LIMIT:
                return "Rate limited by the provider. Wait a moment and try again.";
            case BAD_REQUEST:
                return "The provider rejected the request: " + getMessage();
            case SERVER:
                return "The provider had a server error. Try again shortly.";
            case NETWORK:
                return "Could not reach the provider. Check the connection and the base URL.";
            case TIMEOUT:
                return "The request timed out.";
            case PARSE:
                return "The provider returned a response this client could not read.";
            case CANCELLED:
                return "Cancelled.";
            default:
                return getMessage();
        }
    }
}
