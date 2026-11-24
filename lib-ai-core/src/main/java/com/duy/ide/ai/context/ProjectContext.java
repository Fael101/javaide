package com.duy.ide.ai.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A snapshot of what the user is looking at, captured on the UI thread and handed to a worker.
 * <p>
 * Deliberately dumb data: no file I/O happens here, so building a snapshot is cheap and cannot
 * fail. Whoever captures it decides what to include.
 */
public final class ProjectContext {

    /** A file currently open in the editor. */
    public static final class OpenFile {

        private final String path;
        private final String content;

        public OpenFile(String path, String content) {
            this.path = path;
            this.content = content == null ? "" : content;
        }

        public String getPath() {
            return path;
        }

        public String getContent() {
            return content;
        }
    }

    /** One compiler or aapt message. */
    public static final class Diagnostic {

        private final String path;
        private final int line;
        private final String severity;
        private final String message;

        public Diagnostic(String path, int line, String severity, String message) {
            this.path = path;
            this.line = line;
            this.severity = severity == null ? "error" : severity;
            this.message = message == null ? "" : message;
        }

        public String getPath() {
            return path;
        }

        public int getLine() {
            return line;
        }

        public String getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }

        public String format() {
            StringBuilder sb = new StringBuilder();
            if (path != null && path.length() > 0) {
                sb.append(path);
                if (line > 0) {
                    sb.append(':').append(line);
                }
                sb.append(": ");
            }
            sb.append(severity).append(": ").append(message);
            return sb.toString();
        }
    }

    private final String projectType;
    private final String currentFilePath;
    private final String currentFileContent;
    private final int selectionStartLine;
    private final int selectionEndLine;
    private final List<OpenFile> openFiles;
    private final List<String> projectFiles;
    private final List<Diagnostic> diagnostics;

    private ProjectContext(Builder builder) {
        this.projectType = builder.projectType;
        this.currentFilePath = builder.currentFilePath;
        this.currentFileContent = builder.currentFileContent;
        this.selectionStartLine = builder.selectionStartLine;
        this.selectionEndLine = builder.selectionEndLine;
        this.openFiles = Collections.unmodifiableList(new ArrayList<OpenFile>(builder.openFiles));
        this.projectFiles = Collections.unmodifiableList(new ArrayList<String>(builder.projectFiles));
        this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(builder.diagnostics));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** "java" or "android"; used only for wording. */
    public String getProjectType() {
        return projectType;
    }

    public String getCurrentFilePath() {
        return currentFilePath;
    }

    public String getCurrentFileContent() {
        return currentFileContent;
    }

    /** 1-based first line of the selection, or -1 when there is no selection. */
    public int getSelectionStartLine() {
        return selectionStartLine;
    }

    /** 1-based last line of the selection, inclusive. */
    public int getSelectionEndLine() {
        return selectionEndLine;
    }

    public boolean hasSelection() {
        return selectionStartLine > 0 && selectionEndLine >= selectionStartLine;
    }

    public List<OpenFile> getOpenFiles() {
        return openFiles;
    }

    public List<String> getProjectFiles() {
        return projectFiles;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public static final class Builder {

        private String projectType = "java";
        private String currentFilePath;
        private String currentFileContent;
        private int selectionStartLine = -1;
        private int selectionEndLine = -1;
        private final List<OpenFile> openFiles = new ArrayList<OpenFile>();
        private final List<String> projectFiles = new ArrayList<String>();
        private final List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();

        public Builder projectType(String value) {
            this.projectType = value;
            return this;
        }

        public Builder currentFile(String path, String content) {
            this.currentFilePath = path;
            this.currentFileContent = content;
            return this;
        }

        public Builder selection(int startLine, int endLine) {
            this.selectionStartLine = startLine;
            this.selectionEndLine = endLine;
            return this;
        }

        public Builder openFile(String path, String content) {
            openFiles.add(new OpenFile(path, content));
            return this;
        }

        public Builder projectFile(String path) {
            projectFiles.add(path);
            return this;
        }

        public Builder diagnostic(Diagnostic diagnostic) {
            diagnostics.add(diagnostic);
            return this;
        }

        public Builder diagnostic(String path, int line, String severity, String message) {
            return diagnostic(new Diagnostic(path, line, severity, message));
        }

        public ProjectContext build() {
            return new ProjectContext(this);
        }
    }
}
