package com.duy.ide.ai.sse;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Minimal, spec-correct reader for {@code text/event-stream} responses.
 * <p>
 * Each call to {@link #next()} returns the data of one event, with multiple {@code data:} lines
 * joined by a newline as the format requires. Comment lines (those starting with {@code :}) are
 * discarded, which matters in practice: OpenRouter sends {@code : OPENROUTER PROCESSING}
 * keepalives in the middle of a stream, and treating those as JSON would fail.
 * <p>
 * The reader is line based, so it is unaffected by events split across network reads; a line is
 * only delivered once its terminating newline has arrived.
 */
public final class SseReader implements Closeable {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final BufferedReader reader;
    private boolean finished;

    public SseReader(InputStream input) {
        if (input == null) {
            throw new IllegalArgumentException("input == null");
        }
        this.reader = new BufferedReader(new InputStreamReader(input, UTF_8));
    }

    /**
     * Returns the next event's data, or {@code null} once the stream is exhausted.
     * An event with an empty {@code data} field yields an empty string.
     */
    public String next() throws IOException {
        if (finished) {
            return null;
        }
        StringBuilder data = null;
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                finished = true;
                // A final event is allowed to end without the blank separator line.
                return data == null ? null : data.toString();
            }
            if (line.length() == 0) {
                if (data != null) {
                    return data.toString();
                }
                continue;
            }
            if (line.charAt(0) == ':') {
                continue;
            }
            String field;
            String value;
            int colon = line.indexOf(':');
            if (colon < 0) {
                field = line;
                value = "";
            } else {
                field = line.substring(0, colon);
                value = line.substring(colon + 1);
                if (value.length() > 0 && value.charAt(0) == ' ') {
                    value = value.substring(1);
                }
            }
            if ("data".equals(field)) {
                if (data == null) {
                    data = new StringBuilder();
                } else {
                    data.append('\n');
                }
                data.append(value);
            }
            // "event", "id" and "retry" fields are ignored: no provider we target needs them.
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
