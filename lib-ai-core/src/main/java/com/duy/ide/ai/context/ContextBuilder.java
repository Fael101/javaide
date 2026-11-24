package com.duy.ide.ai.context;

import com.duy.ide.ai.ChatMessage;
import com.duy.ide.ai.prompt.TokenBudget;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link ProjectContext} into the messages sent to the model.
 * <p>
 * Context is added in priority order and stops when the budget is spent, so a very large file
 * can never push out the user's question. The order reflects what a model actually needs:
 * the file under the cursor first, then the selection, then build errors, then the other open
 * tabs, and only then the project tree.
 */
public final class ContextBuilder {

    private static final int OVERHEAD_TOKENS = 120;
    private static final int MAX_DIAGNOSTICS = 40;
    private static final int MAX_TREE_ENTRIES = 300;

    private final int budgetTokens;

    public ContextBuilder(int budgetTokens) {
        this.budgetTokens = budgetTokens < 1000 ? 1000 : budgetTokens;
    }

    /** What to include. All default to true; the chat panel exposes the first two as toggles. */
    public static final class Options {

        private boolean includeCurrentFile = true;
        private boolean includeSelection = true;
        private boolean includeOpenFiles = true;
        private boolean includeTree = true;
        private boolean includeDiagnostics = true;

        public Options currentFile(boolean value) {
            this.includeCurrentFile = value;
            return this;
        }

        public Options selection(boolean value) {
            this.includeSelection = value;
            return this;
        }

        public Options openFiles(boolean value) {
            this.includeOpenFiles = value;
            return this;
        }

        public Options tree(boolean value) {
            this.includeTree = value;
            return this;
        }

        public Options diagnostics(boolean value) {
            this.includeDiagnostics = value;
            return this;
        }
    }

    public List<ChatMessage> build(ProjectContext context, String systemPrompt,
                                   String userPrompt, Options options) {
        Options opts = options == null ? new Options() : options;
        String system = systemPrompt == null ? "" : systemPrompt;
        String prompt = userPrompt == null ? "" : userPrompt;

        int remaining = budgetTokens
                - OVERHEAD_TOKENS
                - TokenBudget.estimate(system)
                - TokenBudget.estimate(prompt);
        if (remaining < 0) {
            remaining = 0;
        }

        List<String> sections = new ArrayList<String>();

        if (opts.includeCurrentFile
                && context.getCurrentFileContent() != null
                && context.getCurrentFileContent().length() > 0) {
            int allowance = remaining > 0 ? Math.max(400, (remaining * 3) / 5) : 0;
            if (allowance > 0) {
                String body = TokenBudget.truncate(context.getCurrentFileContent(), allowance);
                sections.add(fileBlock(context.getCurrentFilePath(), body, true));
                remaining -= TokenBudget.estimate(body) + 20;
            }
        }

        if (opts.includeSelection && context.hasSelection()
                && context.getCurrentFileContent() != null) {
            String selected = sliceLines(context.getCurrentFileContent(),
                    context.getSelectionStartLine(), context.getSelectionEndLine());
            if (selected.length() > 0) {
                int allowance = remaining > 0 ? Math.max(200, remaining / 3) : 0;
                if (allowance > 0) {
                    String body = TokenBudget.truncate(selected, allowance);
                    sections.add(selectionBlock(context.getCurrentFilePath(),
                            context.getSelectionStartLine(),
                            context.getSelectionEndLine(), body));
                    remaining -= TokenBudget.estimate(body) + 20;
                }
            }
        }

        if (opts.includeDiagnostics && !context.getDiagnostics().isEmpty()) {
            int allowance = remaining > 0 ? Math.min(remaining / 2, 1500) : 0;
            if (allowance > 0) {
                String body = diagnosticsText(context.getDiagnostics(), allowance);
                if (body.length() > 0) {
                    sections.add("<build-errors>\n" + body + "\n</build-errors>");
                    remaining -= TokenBudget.estimate(body) + 20;
                }
            }
        }

        if (opts.includeOpenFiles && !context.getOpenFiles().isEmpty()) {
            String current = context.getCurrentFilePath();
            for (ProjectContext.OpenFile file : context.getOpenFiles()) {
                if (remaining < 400) {
                    break;
                }
                if (current != null && current.equals(file.getPath())) {
                    continue;
                }
                int allowance = Math.max(200, remaining / 3);
                String body = TokenBudget.truncate(file.getContent(), allowance);
                sections.add(fileBlock(file.getPath(), body, false));
                remaining -= TokenBudget.estimate(body) + 20;
            }
        }

        if (opts.includeTree && !context.getProjectFiles().isEmpty() && remaining > 200) {
            StringBuilder tree = new StringBuilder();
            int count = 0;
            for (String path : context.getProjectFiles()) {
                if (count >= MAX_TREE_ENTRIES) {
                    tree.append("... (").append(context.getProjectFiles().size() - count)
                            .append(" more files)\n");
                    break;
                }
                tree.append(path).append('\n');
                count++;
            }
            sections.add("<project-files>\n" + tree.toString().trim() + "\n</project-files>");
        }

        StringBuilder user = new StringBuilder();
        for (String section : sections) {
            user.append(section).append("\n\n");
        }
        user.append("---\n").append(prompt);

        List<ChatMessage> messages = new ArrayList<ChatMessage>(2);
        if (system.length() > 0) {
            messages.add(ChatMessage.system(system));
        }
        messages.add(ChatMessage.user(user.toString()));
        return messages;
    }

    private static String fileBlock(String path, String body, boolean active) {
        return "<file path=\"" + escapeAttribute(path) + "\" active=\"" + active + "\">\n"
                + body + "\n</file>";
    }

    private static String selectionBlock(String path, int start, int end, String body) {
        return "<selection path=\"" + escapeAttribute(path) + "\" lines=\"" + start + "-" + end
                + "\">\n" + body + "\n</selection>";
    }

    private static String diagnosticsText(List<ProjectContext.Diagnostic> diagnostics,
                                          int maxTokens) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (ProjectContext.Diagnostic diagnostic : diagnostics) {
            if (count >= MAX_DIAGNOSTICS) {
                sb.append("... (").append(diagnostics.size() - count).append(" more)\n");
                break;
            }
            sb.append(diagnostic.format()).append('\n');
            count++;
            if (TokenBudget.estimate(sb.toString()) > maxTokens) {
                break;
            }
        }
        return sb.toString().trim();
    }

    /** 1-based inclusive line range, clamped to the text. */
    static String sliceLines(String text, int startLine, int endLine) {
        if (text == null || startLine < 1 || endLine < startLine) {
            return "";
        }
        String[] lines = text.split("\n", -1);
        int from = Math.min(startLine - 1, lines.length);
        int to = Math.min(endLine, lines.length);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private static String escapeAttribute(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('"', '\'').replace('\n', ' ');
    }
}
