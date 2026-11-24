package com.duy.ide.ai.cli;

import com.duy.ide.ai.AiError;
import com.duy.ide.ai.AiSettings;
import com.duy.ide.ai.CancelSignal;
import com.duy.ide.ai.ChatCallbackAdapter;
import com.duy.ide.ai.ChatRequest;
import com.duy.ide.ai.Usage;
import com.duy.ide.ai.prompt.SystemPrompts;
import com.duy.ide.ai.provider.OpenAiCompatibleProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Development smoke test. Lives in the test source set so it never ships inside the APK.
 * <p>
 * The API key is read from an environment variable, never from the command line, so it does not
 * end up in shell history or in a process listing.
 * <pre>
 *   AI_API_KEY=... java -cp ... com.duy.ide.ai.cli.AiCli \
 *       --model openai/gpt-4o-mini "explain this error: ..."
 * </pre>
 */
public final class AiCli {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private AiCli() {
    }

    public static void main(String[] args) {
        AiSettings defaults = new AiSettings();
        String base = defaults.getBaseUrl();
        String model = defaults.getModel();
        String keyEnv = "AI_API_KEY";
        String file = null;
        boolean stream = true;
        StringBuilder prompt = new StringBuilder();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--base".equals(arg) && i + 1 < args.length) {
                base = args[++i];
            } else if ("--model".equals(arg) && i + 1 < args.length) {
                model = args[++i];
            } else if ("--key-env".equals(arg) && i + 1 < args.length) {
                keyEnv = args[++i];
            } else if ("--file".equals(arg) && i + 1 < args.length) {
                file = args[++i];
            } else if ("--no-stream".equals(arg)) {
                stream = false;
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                usage();
                return;
            } else {
                if (prompt.length() > 0) {
                    prompt.append(' ');
                }
                prompt.append(arg);
            }
        }

        if (model == null || model.length() == 0) {
            System.err.println("error: --model is required");
            usage();
            System.exit(2);
        }
        if (prompt.length() == 0) {
            System.err.println("error: a prompt is required");
            usage();
            System.exit(2);
        }

        String key = System.getenv(keyEnv);
        if (key == null || key.length() == 0) {
            System.err.println("warning: environment variable " + keyEnv
                    + " is empty; sending without an Authorization header");
            key = "";
        }

        String userPrompt = prompt.toString();
        if (file != null) {
            String content;
            try {
                content = readFile(new File(file));
            } catch (IOException e) {
                System.err.println("error: cannot read " + file + ": " + e.getMessage());
                System.exit(2);
                return;
            }
            userPrompt = "<file path=\"" + file + "\">\n" + content + "\n</file>\n\n" + userPrompt;
        }

        System.err.println("[using] base=" + base + " model=" + model);

        ChatRequest request = ChatRequest.builder(model)
                .system(SystemPrompts.CHAT)
                .user(userPrompt)
                .stream(stream)
                .build();

        final boolean[] failed = new boolean[1];
        new OpenAiCompatibleProvider(base, key).stream(request,
                new ChatCallbackAdapter() {
                    @Override
                    public void onStart() {
                        System.err.println("[connected]");
                    }

                    @Override
                    public void onDelta(String text) {
                        System.out.print(text);
                        System.out.flush();
                    }

                    @Override
                    public void onDone(String fullText, Usage usage) {
                        System.out.println();
                        System.err.println("[done] " + (usage.isKnown() ? usage.toString() : "usage not reported"));
                    }

                    @Override
                    public void onError(AiError error) {
                        failed[0] = true;
                        System.err.println("[error] " + error.getKind()
                                + " (HTTP " + error.getHttpCode() + "): " + error.getUserMessage());
                    }
                },
                new CancelSignal());

        System.exit(failed[0] ? 1 : 0);
    }

    private static String readFile(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), UTF_8);
        } finally {
            in.close();
        }
    }

    private static void usage() {
        System.err.println("usage: AiCli --model MODEL [--base URL] [--key-env VAR] [--file PATH]"
                + " [--no-stream] PROMPT...");
        System.err.println("  --base     default " + AiSettings.BASE_OPENROUTER);
        System.err.println("  --key-env  environment variable holding the key, default AI_API_KEY");
        System.err.println("  --file     include a file's contents in the request");
    }
}
