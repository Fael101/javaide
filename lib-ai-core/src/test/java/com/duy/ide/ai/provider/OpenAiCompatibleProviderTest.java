package com.duy.ide.ai.provider;

import com.duy.ide.ai.AiError;
import com.duy.ide.ai.CancelSignal;
import com.duy.ide.ai.ChatCallbackAdapter;
import com.duy.ide.ai.ChatRequest;
import com.duy.ide.ai.Usage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OpenAiCompatibleProviderTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private HttpServer server;

    @After
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private interface Body {
        void handle(HttpExchange exchange) throws IOException;
    }

    private String serve(final Body body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    body.handle(exchange);
                } finally {
                    exchange.close();
                }
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private static void respond(HttpExchange exchange, int code, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(UTF_8);
        if (contentType != null) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.flush();
        out.close();
    }

    private static String drain(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        input.close();
        return new String(out.toByteArray(), UTF_8);
    }

    private static ChatRequest simpleRequest() {
        return ChatRequest.builder("test/model").system("be terse").user("hello").build();
    }

    private static final class Recorder extends ChatCallbackAdapter {
        final List<String> deltas = new ArrayList<String>();
        String done;
        AiError error;
        Usage usage;
        int starts;

        @Override
        public void onStart() {
            starts++;
        }

        @Override
        public void onDelta(String text) {
            deltas.add(text);
        }

        @Override
        public void onDone(String fullText, Usage usage) {
            this.done = fullText;
            this.usage = usage;
        }

        @Override
        public void onError(AiError error) {
            this.error = error;
        }

        String streamed() {
            StringBuilder sb = new StringBuilder();
            for (String delta : deltas) {
                sb.append(delta);
            }
            return sb.toString();
        }
    }

    @Test
    public void streamsDeltasAndUsage() throws Exception {
        final String sse =
                ": OPENROUTER PROCESSING\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{}}],\"usage\":{\"prompt_tokens\":11,"
                        + "\"completion_tokens\":2}}\n\n"
                        + "data: [DONE]\n\n";
        String base = serve(new Body() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                drain(exchange.getRequestBody());
                respond(exchange, 200, "text/event-stream", sse);
            }
        });

        Recorder recorder = new Recorder();
        new OpenAiCompatibleProvider(base, "sk-test")
                .stream(simpleRequest(), recorder, new CancelSignal());

        assertNull(recorder.error);
        assertEquals(1, recorder.starts);
        assertEquals("Hello", recorder.done);
        assertEquals("Hello", recorder.streamed());
        assertEquals(11, recorder.usage.getPromptTokens());
        assertEquals(2, recorder.usage.getCompletionTokens());
    }

    @Test
    public void readsNonStreamingResponse() throws Exception {
        final String json = "{\"choices\":[{\"message\":{\"content\":\"Hi there\"}}],"
                + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3}}";
        String base = serve(new Body() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                drain(exchange.getRequestBody());
                respond(exchange, 200, "application/json", json);
            }
        });

        Recorder recorder = new Recorder();
        ChatRequest request = ChatRequest.builder("test/model").user("hello").stream(false).build();
        new OpenAiCompatibleProvider(base, "sk-test").stream(request, recorder, new CancelSignal());

        assertNull(recorder.error);
        assertEquals("Hi there", recorder.done);
        assertEquals(1, recorder.deltas.size());
        assertEquals(5, recorder.usage.getPromptTokens());
    }

    @Test
    public void sendsModelStreamFlagAndBearerToken() throws Exception {
        final AtomicReference<String> requestBody = new AtomicReference<String>();
        final AtomicReference<String> authorization = new AtomicReference<String>();
        String base = serve(new Body() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requestBody.set(drain(exchange.getRequestBody()));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                respond(exchange, 200, "text/event-stream", "data: [DONE]\n\n");
            }
        });

        new OpenAiCompatibleProvider(base, "sk-abc")
                .stream(simpleRequest(), new Recorder(), new CancelSignal());

        String sent = requestBody.get();
        assertNotNull(sent);
        assertTrue(sent.contains("\"model\":\"test/model\""));
        assertTrue(sent.contains("\"stream\":true"));
        assertTrue(sent.contains("\"role\":\"system\""));
        assertTrue(sent.contains("\"role\":\"user\""));
        assertEquals("Bearer sk-abc", authorization.get());
    }

    @Test
    public void maps401ToAuthError() throws Exception {
        String base = serve(new Body() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                drain(exchange.getRequestBody());
                respond(exchange, 401, "application/json",
                        "{\"error\":{\"message\":\"Invalid API key\"}}");
            }
        });

        Recorder recorder = new Recorder();
        new OpenAiCompatibleProvider(base, "bad")
                .stream(simpleRequest(), recorder, new CancelSignal());

        assertNotNull(recorder.error);
        assertEquals(AiError.Kind.AUTH, recorder.error.getKind());
        assertEquals(401, recorder.error.getHttpCode());
        assertTrue(recorder.error.getMessage().contains("Invalid API key"));
        assertFalse(recorder.error.isRetryable());
        assertNull(recorder.done);
    }

    @Test
    public void maps402ToQuotaError() throws Exception {
        String base = serve(new Body() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                drain(exchange.getRequestBody());
                respond(exchange, 402, "application/json",
                        "{\"error\":{\"message\":\"Insufficient credits\"}}");
            }
        });

        Recorder recorder = new Recorder();
        new OpenAiCompatibleProvider(base, "k")
                .stream(simpleRequest(), recorder, new CancelSignal());

        assertEquals(AiError.Kind.QUOTA, recorder.error.getKind());
    }

    @Test
    public void retriesOnceAfterRateLimit() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        String base = serve(new Body() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                drain(exchange.getRequestBody());
                if (calls.incrementAndGet() == 1) {
                    exchange.getResponseHeaders().set("Retry-After", "0");
                    respond(exchange, 429, "application/json",
                            "{\"error\":{\"message\":\"slow down\"}}");
                } else {
                    respond(exchange, 200, "text/event-stream",
                            "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n"
                                    + "data: [DONE]\n\n");
                }
            }
        });

        Recorder recorder = new Recorder();
        new OpenAiCompatibleProvider(base, "k")
                .stream(simpleRequest(), recorder, new CancelSignal());

        assertEquals(2, calls.get());
        assertNull(recorder.error);
        assertEquals("ok", recorder.done);
    }

    @Test
    public void reportsParseFailureOnGarbageStream() throws Exception {
        String base = serve(new Body() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                drain(exchange.getRequestBody());
                respond(exchange, 200, "text/event-stream", "data: not-json\n\n");
            }
        });

        Recorder recorder = new Recorder();
        new OpenAiCompatibleProvider(base, "k")
                .stream(simpleRequest(), recorder, new CancelSignal());

        assertNotNull(recorder.error);
        assertEquals(AiError.Kind.PARSE, recorder.error.getKind());
    }

    @Test
    public void precancelledSignalSendsNothing() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        String base = serve(new Body() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                calls.incrementAndGet();
                drain(exchange.getRequestBody());
                respond(exchange, 200, "text/event-stream", "data: [DONE]\n\n");
            }
        });

        CancelSignal signal = new CancelSignal();
        signal.cancel();
        Recorder recorder = new Recorder();
        new OpenAiCompatibleProvider(base, "k").stream(simpleRequest(), recorder, signal);

        assertEquals(0, calls.get());
        assertEquals(AiError.Kind.CANCELLED, recorder.error.getKind());
    }

    @Test
    public void treatsErrorMessageInsideStreamAsFailure() throws Exception {
        String base = serve(new Body() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                drain(exchange.getRequestBody());
                respond(exchange, 200, "text/event-stream",
                        "data: {\"error\":{\"message\":\"model overloaded\",\"code\":503}}\n\n");
            }
        });

        Recorder recorder = new Recorder();
        new OpenAiCompatibleProvider(base, "k")
                .stream(simpleRequest(), recorder, new CancelSignal());

        assertNotNull(recorder.error);
        assertEquals(AiError.Kind.SERVER, recorder.error.getKind());
        assertTrue(recorder.error.getMessage().contains("model overloaded"));
    }

    @Test
    public void requestsUsageInStreamMode() throws Exception {
        final AtomicReference<String> sent = new AtomicReference<String>();
        String base = serve(new Body() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                sent.set(drain(exchange.getRequestBody()));
                respond(exchange, 200, "text/event-stream", "data: [DONE]\n\n");
            }
        });

        new OpenAiCompatibleProvider(base, "k")
                .stream(simpleRequest(), new Recorder(), new CancelSignal());

        assertTrue(sent.get().contains("\"stream_options\":{\"include_usage\":true}"));
    }

    @Test
    public void dropsStreamOptionsWhenServerRejectsIt() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        final List<String> bodies = new ArrayList<String>();
        String base = serve(new Body() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                bodies.add(drain(exchange.getRequestBody()));
                if (calls.incrementAndGet() == 1) {
                    respond(exchange, 400, "application/json",
                            "{\"error\":{\"message\":\"Unrecognized argument: stream_options\"}}");
                } else {
                    respond(exchange, 200, "text/event-stream",
                            "data: {\"choices\":[{\"delta\":{\"content\":\"fine\"}}]}\n\n"
                                    + "data: [DONE]\n\n");
                }
            }
        });

        Recorder recorder = new Recorder();
        new OpenAiCompatibleProvider(base, "k")
                .stream(simpleRequest(), recorder, new CancelSignal());

        assertNull(recorder.error);
        assertEquals("fine", recorder.done);
        assertEquals(2, calls.get());
        assertTrue(bodies.get(0).contains("stream_options"));
        assertFalse(bodies.get(1).contains("stream_options"));
    }
}
