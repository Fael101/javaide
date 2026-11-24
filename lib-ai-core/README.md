# lib-ai-core

The provider-agnostic half of the Java N-IDE assistant: HTTP, streaming, prompting, context
assembly and diffing.

## Why this module has no Android dependencies

Everything here is plain Java 7 against `java.net`, `java.io` and gson. That is deliberate:

* it can be compiled and tested on a desktop or in Termux with no Android SDK;
* the app module stays a thin layer of glue (key storage, settings, UI);
* the risky parts — network handling, SSE parsing, diff correctness — are covered by tests that
  run in a second rather than by manual tapping on a phone.

`java.net.HttpURLConnection` is used instead of OkHttp. The app already has enough dependencies,
and the request shapes here are small and simple. gson 2.8.1 was already a dependency of the app,
so this module adds nothing to the APK.

## Layout

```
com.duy.ide.ai
  AiProvider            provider interface (blocking, callback based)
  ChatMessage/Request   conversation model
  ChatCallback          onStart / onDelta / onDone / onError
  AiError               typed failure: AUTH, QUOTA, RATE_LIMIT, SERVER, NETWORK, ...
  AiSettings            connection settings as plain data (no persistence)
  CancelSignal          cancels a call by closing the socket

  ProviderPresets       known endpoints for the settings dropdown (NVIDIA, OpenRouter,
                        OpenAI, Groq, DeepSeek, Ollama)
  provider/OpenAiCompatibleProvider
                        NVIDIA NIM, OpenAI, OpenRouter, Groq, DeepSeek, Ollama, LM Studio,
                        any OpenAI-compatible base URL
  sse/SseReader         text/event-stream parser
  prompt/SystemPrompts  default system prompts
  prompt/TokenBudget    chars/4 estimate and truncation
  context/              ProjectContext snapshot and ContextBuilder
  diff/                 LineDiff (LCS), Hunk, PatchApplier
```

## Build and test without Gradle

```sh
lib-ai-core/tools/build-and-test.sh
```

The script needs `gson-2.8.1.jar`, `junit-4.12.jar` and `hamcrest-core-1.3.jar` in `~/libs-ai`
(override with `AI_LIBS`). It compiles at `--release 8` because JDK 21 no longer accepts
`-source 7`; the Gradle build targets 1.7 with a JDK 8 toolchain.

## Usage

```java
AiSettings settings = new AiSettings();          // defaults to NVIDIA NIM
settings.setApiKey(key);

ChatRequest request = settings.newRequest()
        .system(SystemPrompts.CHAT)
        .user("Why does this fail to compile?")
        .build();

new OpenAiCompatibleProvider(settings.getBaseUrl(), settings.getApiKey())
        .stream(request, callback, new CancelSignal());
```

The default model is `nvidia/nemotron-3.5-lightning-30b-a3b`: about three seconds warm, and it
respects the Java 7 constraint. `ProviderPresets` lists the alternatives for the settings UI.

`stream` blocks. Call it from a worker thread.

## Decisions worth knowing

**No `setFixedLengthStreamingMode`.** In streaming mode the JDK discards the body of a 401
("cannot retry due to server authentication"), so a wrong key would surface as "HTTP 401"
instead of the provider's message. Request bodies are small, so they are buffered instead.

**Retries.** 429, 5xx, and transport errors retry twice, honouring `Retry-After`. Everything else
fails immediately, because retrying a bad key or an unknown model only wastes time.

**Usage is requested, then dropped if refused.** Streaming requests ask for
`stream_options: {include_usage: true}`, which NVIDIA, OpenAI, OpenRouter, Groq and DeepSeek
honour by sending a final chunk with token counts. If a stricter server answers 400 mentioning
`stream_options`, the provider disables the field and retries once instead of failing the call.

**Diff memory is bounded.** `LineDiff` uses an LCS table and falls back to a single replacement
hunk above four million cells. A coarse diff is better than an out-of-memory crash in an editor.

**Token counts are estimated.** Four characters per token. A real tokenizer would add megabytes
of vocabulary to the APK to improve an approximation used only for trimming context.

## Not here yet

* `AnthropicProvider` — a different wire format (`/v1/messages`, `x-api-key`). Deliberately
  deferred rather than shipped untested.
* Persistence and key protection — that is `AiKeyStore` / `AiSettingsStore` in the app module.
* Agentic tool calling — later, and only with explicit per-action confirmation.
