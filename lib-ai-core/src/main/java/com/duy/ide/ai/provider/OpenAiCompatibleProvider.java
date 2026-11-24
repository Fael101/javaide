package com.duy.ide.ai.provider;

import com.duy.ide.ai.AiError;
import com.duy.ide.ai.AiProvider;
import com.duy.ide.ai.CancelSignal;
import com.duy.ide.ai.ChatCallback;
import com.duy.ide.ai.ChatMessage;
import com.duy.ide.ai.ChatRequest;
import com.duy.ide.ai.Usage;
import com.duy.ide.ai.sse.SseReader;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Client for the OpenAI chat-completions wire format.
 * <p>
 * This one class covers every hosted and local service that speaks the same protocol: OpenAI,
 * OpenRouter, Groq, DeepSeek, Together, Fireworks, vLLM, Ollama and LM Studio. Only the base URL
 * and the model slug change, which is why the settings screen exposes both as free text.
 * <p>
 * Blocking. Call from a worker thread.
 */
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 180000;
    private static final int MAX_RETRIES = 2;
    private static final int ERROR_BODY_LIMIT = 4096;

    private final String baseUrl;
    private final String apiKey;
    private final String referer;
    private final String title;
    private final Map<String, String> extraHeaders;

    /**
     * Ask the server to report token usage on the final stream chunk. Supported by OpenAI,
     * OpenRouter, Groq, DeepSeek and NVIDIA; turned off automatically if a server rejects it.
     */
    private volatile boolean includeUsage = true;

    public OpenAiCompatibleProvider(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, null, null, null);
    }

    /**
     * @param referer      optional HTTP-Referer, used by OpenRouter for attribution
     * @param title        optional X-Title, used by OpenRouter for attribution
     * @param extraHeaders optional additional headers, or null
     */
    public OpenAiCompatibleProvider(String baseUrl, String apiKey, String referer, String title,
                                    Map<String, String> extraHeaders) {
        if (baseUrl == null) {
            throw new IllegalArgumentException("baseUrl == null");
        }
        this.baseUrl = trimTrailingSlash(baseUrl.trim());
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.referer = referer;
        this.title = title;
        this.extraHeaders = extraHeaders;
    }

    /** Disables the usage request for servers that reject {@code stream_options}. */
    public void setIncludeUsage(boolean includeUsage) {
        this.includeUsage = includeUsage;
    }

    @Override
    public String id() {
        return "openai-compatible";
    }

    @Override
    public void stream(ChatRequest request, ChatCallback callback, CancelSignal cancel) {
        CancelSignal signal = cancel == null ? new CancelSignal() : cancel;
        int attempt = 0;
        boolean usageFallbackTried = false;
        while (true) {
            if (signal.isCancelled()) {
                callback.onError(new AiError(AiError.Kind.CANCELLED, "Cancelled"));
                return;
            }
            try {
                execute(request, callback, signal);
                return;
            } catch (AiError error) {
                if (error.getKind() == AiError.Kind.CANCELLED) {
                    callback.onError(error);
                    return;
                }
                if (!usageFallbackTried && includeUsage
                        && error.getKind() == AiError.Kind.BAD_REQUEST
                        && mentionsStreamOptions(error.getMessage())) {
                    // A strict server rejected stream_options. Drop it and try once more rather
                    // than failing a call that would otherwise work.
                    includeUsage = false;
                    usageFallbackTried = true;
                    continue;
                }
                if (error.isRetryable() && attempt < MAX_RETRIES) {
                    attempt++;
                    if (!pause(backoffMillis(attempt, error), signal)) {
                        callback.onError(new AiError(AiError.Kind.CANCELLED, "Cancelled"));
                        return;
                    }
                    continue;
                }
                callback.onError(error);
                return;
            }
        }
    }

    private void execute(ChatRequest request, ChatCallback callback, CancelSignal signal)
            throws AiError {
        HttpURLConnection connection = null;
        try {
            connection = open(request);
            final HttpURLConnection cancelTarget = connection;
            signal.setOnCancel(new Runnable() {
                @Override
                public void run() {
                    try {
                        cancelTarget.disconnect();
                    } catch (RuntimeException ignored) {
                        // Already closed.
                    }
                }
            });

            writeBody(connection, buildRequestBody(request));

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw httpError(connection, code);
            }

            callback.onStart();
            if (request.isStream()) {
                readStream(connection, callback, signal);
            } else {
                readWhole(connection, callback);
            }
        } catch (AiError error) {
            throw error;
        } catch (SocketTimeoutException e) {
            throw new AiError(AiError.Kind.TIMEOUT, 0, "Request timed out", -1, e);
        } catch (IOException e) {
            if (signal.isCancelled()) {
                throw new AiError(AiError.Kind.CANCELLED, "Cancelled");
            }
            String message = e.getMessage();
            throw new AiError(AiError.Kind.NETWORK, 0,
                    message == null ? e.getClass().getSimpleName() : message, -1, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection open(ChatRequest request) throws IOException {
        URL url = new URL(endpoint());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept",
                request.isStream() ? "text/event-stream" : "application/json");
        if (apiKey.length() > 0) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        if (referer != null && referer.length() > 0) {
            connection.setRequestProperty("HTTP-Referer", referer);
        }
        if (title != null && title.length() > 0) {
            connection.setRequestProperty("X-Title", title);
        }
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return connection;
    }

    private static void writeBody(HttpURLConnection connection, byte[] body) throws IOException {
        // Deliberately no setFixedLengthStreamingMode(). In streaming mode the JDK refuses to
        // expose the response body of a 401 ("cannot retry due to server authentication, in
        // streaming mode"), which is precisely the body that says the key is wrong. Request
        // bodies here are small, so buffering them costs nothing.
        OutputStream out = connection.getOutputStream();
        try {
            out.write(body);
            out.flush();
        } finally {
            out.close();
        }
    }

    private void readStream(HttpURLConnection connection, ChatCallback callback,
                            CancelSignal signal) throws AiError {
        StringBuilder collected = new StringBuilder();
        Usage usage = Usage.UNKNOWN;
        SseReader reader = null;
        try {
            reader = new SseReader(connection.getInputStream());
            String payload;
            while ((payload = reader.next()) != null) {
                if (signal.isCancelled()) {
                    throw new AiError(AiError.Kind.CANCELLED, "Cancelled");
                }
                String trimmed = payload.trim();
                if (trimmed.length() == 0) {
                    continue;
                }
                if ("[DONE]".equals(trimmed)) {
                    break;
                }
                JsonObject chunk = parseObject(trimmed);
                if (chunk.has("error")) {
                    throw errorFrom(chunk.get("error"), 0);
                }
                Usage reported = usageOf(chunk);
                if (reported != null) {
                    usage = reported;
                }
                String delta = deltaText(chunk);
                if (delta != null && delta.length() > 0) {
                    collected.append(delta);
                    callback.onDelta(delta);
                }
            }
        } catch (IOException e) {
            if (signal.isCancelled()) {
                throw new AiError(AiError.Kind.CANCELLED, "Cancelled");
            }
            String message = e.getMessage();
            throw new AiError(AiError.Kind.NETWORK, 0,
                    "Stream interrupted: " + (message == null ? e.getClass().getSimpleName() : message),
                    -1, e);
        } finally {
            closeQuietly(reader);
        }
        callback.onDone(collected.toString(), usage);
    }

    private void readWhole(HttpURLConnection connection, ChatCallback callback) throws AiError {
        String body;
        try {
            body = readAll(connection.getInputStream());
        } catch (IOException e) {
            String message = e.getMessage();
            throw new AiError(AiError.Kind.NETWORK, 0,
                    message == null ? e.getClass().getSimpleName() : message, -1, e);
        }
        JsonObject root = parseObject(body);
        if (root.has("error")) {
            throw errorFrom(root.get("error"), 0);
        }
        String text = messageText(root);
        callback.onDelta(text);
        Usage usage = usageOf(root);
        callback.onDone(text, usage == null ? Usage.UNKNOWN : usage);
    }

    private byte[] buildRequestBody(ChatRequest request) {
        JsonObject root = new JsonObject();
        root.addProperty("model", request.getModel());
        root.addProperty("stream", request.isStream());
        if (request.isStream() && includeUsage) {
            JsonObject streamOptions = new JsonObject();
            streamOptions.addProperty("include_usage", true);
            root.add("stream_options", streamOptions);
        }
        root.addProperty("temperature", request.getTemperature());
        if (request.getMaxTokens() > 0) {
            root.addProperty("max_tokens", request.getMaxTokens());
        }
        JsonArray messages = new JsonArray();
        for (ChatMessage message : request.getMessages()) {
            JsonObject item = new JsonObject();
            item.addProperty("role", message.getRole().wire());
            item.addProperty("content", message.getContent());
            messages.add(item);
        }
        root.add("messages", messages);
        return root.toString().getBytes(UTF_8);
    }

    private String endpoint() {
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        return baseUrl + "/chat/completions";
    }

    private static String deltaText(JsonObject chunk) {
        JsonObject choice = firstChoice(chunk);
        if (choice == null || !choice.has("delta")) {
            return null;
        }
        JsonElement delta = choice.get("delta");
        if (delta == null || delta.isJsonNull() || !delta.isJsonObject()) {
            return null;
        }
        return textOf(delta.getAsJsonObject().get("content"));
    }

    private static String messageText(JsonObject root) throws AiError {
        JsonObject choice = firstChoice(root);
        if (choice == null || !choice.has("message")) {
            return "";
        }
        JsonElement message = choice.get("message");
        if (message == null || message.isJsonNull() || !message.isJsonObject()) {
            return "";
        }
        String text = textOf(message.getAsJsonObject().get("content"));
        return text == null ? "" : text;
    }

    private static JsonObject firstChoice(JsonObject root) {
        if (!root.has("choices") || !root.get("choices").isJsonArray()) {
            return null;
        }
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices.size() == 0 || !choices.get(0).isJsonObject()) {
            return null;
        }
        return choices.get(0).getAsJsonObject();
    }

    /**
     * Content may be a plain string, or an array of parts for multimodal providers. Both shapes
     * occur in the wild on OpenRouter.
     */
    private static String textOf(JsonElement content) {
        if (content == null || content.isJsonNull()) {
            return null;
        }
        if (content.isJsonPrimitive()) {
            return content.getAsString();
        }
        if (content.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            JsonArray parts = content.getAsJsonArray();
            for (int i = 0; i < parts.size(); i++) {
                JsonElement part = parts.get(i);
                if (part != null && part.isJsonObject()) {
                    JsonElement text = part.getAsJsonObject().get("text");
                    if (text != null && text.isJsonPrimitive()) {
                        sb.append(text.getAsString());
                    }
                }
            }
            return sb.toString();
        }
        return null;
    }

    private static Usage usageOf(JsonObject root) {
        if (root == null || !root.has("usage") || !root.get("usage").isJsonObject()) {
            return null;
        }
        JsonObject usage = root.getAsJsonObject("usage");
        int prompt = intOr(usage, "prompt_tokens", -1);
        int completion = intOr(usage, "completion_tokens", -1);
        if (prompt < 0 && completion < 0) {
            return null;
        }
        return new Usage(prompt, completion);
    }

    private static int intOr(JsonObject object, String name, int fallback) {
        if (object == null || !object.has(name)) {
            return fallback;
        }
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static JsonObject parseObject(String json) throws AiError {
        try {
            JsonElement element = new JsonParser().parse(json);
            if (element == null || !element.isJsonObject()) {
                throw new AiError(AiError.Kind.PARSE, "Expected a JSON object");
            }
            return element.getAsJsonObject();
        } catch (AiError e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiError(AiError.Kind.PARSE, 0,
                    "Malformed JSON: " + abbreviate(json, 200), -1, e);
        }
    }

    private AiError httpError(HttpURLConnection connection, int code) {
        String body = null;
        try {
            InputStream stream = connection.getErrorStream();
            if (stream != null) {
                body = readAll(stream);
            }
        } catch (IOException ignored) {
            // The status code alone still identifies the failure.
        }
        int retryAfter = -1;
        String header = connection.getHeaderField("Retry-After");
        if (header != null) {
            try {
                retryAfter = Integer.parseInt(header.trim());
            } catch (NumberFormatException ignored) {
                retryAfter = -1;
            }
        }
        AiError.Kind kind;
        if (code == 401 || code == 403) {
            kind = AiError.Kind.AUTH;
        } else if (code == 402) {
            kind = AiError.Kind.QUOTA;
        } else if (code == 429) {
            kind = AiError.Kind.RATE_LIMIT;
        } else if (code >= 500) {
            kind = AiError.Kind.SERVER;
        } else {
            kind = AiError.Kind.BAD_REQUEST;
        }
        String message = extractErrorMessage(body);
        if (message == null || message.length() == 0) {
            message = "HTTP " + code;
        }
        return new AiError(kind, code, message, retryAfter, null);
    }

    private static AiError errorFrom(JsonElement errorElement, int code) {
        String message = "Provider reported an error";
        if (errorElement != null && errorElement.isJsonObject()) {
            JsonObject object = errorElement.getAsJsonObject();
            String text = stringOrNull(object.get("message"));
            if (text != null) {
                message = text;
            }
            int reported = intOr(object, "code", 0);
            if (reported > 0) {
                code = reported;
            }
        } else if (errorElement != null && errorElement.isJsonPrimitive()) {
            message = errorElement.getAsString();
        }
        AiError.Kind kind = code == 401 || code == 403 ? AiError.Kind.AUTH
                : code == 402 ? AiError.Kind.QUOTA
                : code == 429 ? AiError.Kind.RATE_LIMIT
                : code >= 500 ? AiError.Kind.SERVER
                : AiError.Kind.BAD_REQUEST;
        return new AiError(kind, code, message);
    }

    private static String extractErrorMessage(String body) {
        if (body == null || body.length() == 0) {
            return null;
        }
        try {
            JsonElement parsed = new JsonParser().parse(body);
            if (parsed != null && parsed.isJsonObject()) {
                JsonObject object = parsed.getAsJsonObject();
                JsonElement error = object.get("error");
                if (error != null && error.isJsonObject()) {
                    String message = stringOrNull(error.getAsJsonObject().get("message"));
                    if (message != null) {
                        return message;
                    }
                }
                if (error != null && error.isJsonPrimitive()) {
                    return error.getAsString();
                }
                String message = stringOrNull(object.get("message"));
                if (message != null) {
                    return message;
                }
            }
        } catch (RuntimeException ignored) {
            // Not JSON; fall through to the raw text.
        }
        return abbreviate(body, 300);
    }

    private static String stringOrNull(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        try {
            while ((read = input.read(buffer)) != -1) {
                total += read;
                out.write(buffer, 0, read);
                if (total > ERROR_BODY_LIMIT) {
                    // Error bodies are small; never buffer an unbounded stream.
                    break;
                }
            }
        } finally {
            closeQuietly(input);
        }
        return new String(out.toByteArray(), UTF_8);
    }

    private static boolean mentionsStreamOptions(String message) {
        return message != null && message.indexOf("stream_options") >= 0;
    }

    private static long backoffMillis(int attempt, AiError error) {
        int requested = error.getRetryAfterSeconds();
        if (requested >= 0) {
            return requested * 1000L;
        }
        return 1000L * attempt;
    }

    /** Sleeps in slices so cancellation is noticed promptly. Returns false if cancelled. */
    private static boolean pause(long millis, CancelSignal signal) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (signal.isCancelled()) {
                return false;
            }
            long remaining = deadline - System.currentTimeMillis();
            long slice = remaining > 100L ? 100L : remaining;
            if (slice <= 0L) {
                break;
            }
            try {
                Thread.sleep(slice);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !signal.isCancelled();
    }

    private static void closeQuietly(final SseReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (IOException ignored) {
            // Nothing useful to do.
        }
    }

    private static void closeQuietly(final InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // Nothing useful to do.
        }
    }

    private static String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String abbreviate(String text, int limit) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace('\n', ' ').replace('\r', ' ');
        if (singleLine.length() <= limit) {
            return singleLine;
        }
        return singleLine.substring(0, limit) + "...";
    }
}
