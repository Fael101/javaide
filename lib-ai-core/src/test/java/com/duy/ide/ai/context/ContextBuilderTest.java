package com.duy.ide.ai.context;

import com.duy.ide.ai.ChatMessage;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContextBuilderTest {

    private static ProjectContext sample() {
        return ProjectContext.builder()
                .projectType("android")
                .currentFile("app/src/main/java/MainActivity.java", "line1\nline2\nline3\n")
                .selection(2, 2)
                .openFile("app/src/main/java/Other.java", "class Other {}\n")
                .projectFile("app/src/main/AndroidManifest.xml")
                .projectFile("app/src/main/java/MainActivity.java")
                .diagnostic("MainActivity.java", 12, "error", "cannot find symbol")
                .build();
    }

    @Test
    public void includesCurrentFileAndPrompt() {
        List<ChatMessage> messages = new ContextBuilder(4000)
                .build(sample(), "sys", "explain this", new ContextBuilder.Options());
        assertEquals(2, messages.size());
        assertEquals(ChatMessage.Role.SYSTEM, messages.get(0).getRole());
        String user = messages.get(1).getContent();
        assertTrue(user.contains("MainActivity.java"));
        assertTrue(user.contains("line1"));
        assertTrue(user.contains("explain this"));
        assertTrue(user.endsWith("explain this"));
    }

    @Test
    public void rendersSelectionAndDiagnostics() {
        List<ChatMessage> messages = new ContextBuilder(4000)
                .build(sample(), "sys", "fix it", new ContextBuilder.Options());
        String user = messages.get(1).getContent();
        assertTrue(user.contains("<selection"));
        assertTrue(user.contains("<build-errors>"));
        assertTrue(user.contains("cannot find symbol"));
    }

    @Test
    public void omitsOpenFilesWhenDisabled() {
        List<ChatMessage> messages = new ContextBuilder(4000)
                .build(sample(), "sys", "hi", new ContextBuilder.Options().openFiles(false));
        assertFalse(messages.get(1).getContent().contains("Other.java"));
    }

    @Test
    public void doesNotRepeatCurrentFileAsOpenFile() {
        ProjectContext context = ProjectContext.builder()
                .currentFile("A.java", "class A {}\n")
                .openFile("A.java", "class A {}\n")
                .build();
        String user = new ContextBuilder(4000)
                .build(context, "sys", "hi", new ContextBuilder.Options())
                .get(1).getContent();
        assertEquals(1, countOccurrences(user, "class A {}"));
    }

    @Test
    public void includesProjectTree() {
        String user = new ContextBuilder(4000)
                .build(sample(), "sys", "hi", new ContextBuilder.Options())
                .get(1).getContent();
        assertTrue(user.contains("<project-files>"));
        assertTrue(user.contains("AndroidManifest.xml"));
    }

    @Test
    public void trimsLargeFileToStayWithinBudget() {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 40000; i++) {
            huge.append("private int field").append(i).append(";\n");
        }
        ProjectContext context = ProjectContext.builder()
                .currentFile("Big.java", huge.toString())
                .build();
        String user = new ContextBuilder(2000)
                .build(context, "sys", "what is this", new ContextBuilder.Options())
                .get(1).getContent();
        assertTrue(user.length() < 12000);
        assertTrue(user.contains("characters omitted"));
        assertTrue(user.endsWith("what is this"));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
