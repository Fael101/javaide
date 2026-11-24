package com.duy.ide.ai.diff;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LineDiffTest {

    private static String applyAll(String original, List<Hunk> hunks) {
        boolean[] accepted = new boolean[hunks.size()];
        for (int i = 0; i < accepted.length; i++) {
            accepted[i] = true;
        }
        return PatchApplier.apply(original, hunks, accepted);
    }

    @Test
    public void identicalTextProducesNoChanges() {
        List<Hunk> hunks = LineDiff.diff("a\nb\nc\n", "a\nb\nc\n");
        for (Hunk hunk : hunks) {
            assertFalse(hunk.isChange());
        }
        assertEquals("a\nb\nc\n", applyAll("a\nb\nc\n", hunks));
    }

    @Test
    public void replacesOneLine() {
        String before = "a\nb\nc\n";
        String after = "a\nB\nc\n";
        List<Hunk> hunks = LineDiff.diff(before, after);
        assertEquals(after, applyAll(before, hunks));
        assertEquals(1, countChanges(hunks));
        Hunk change = firstChange(hunks);
        assertEquals(1, change.getOldStart());
        assertEquals("b", change.getOldLines().get(0));
        assertEquals("B", change.getNewLines().get(0));
    }

    @Test
    public void insertsLines() {
        String before = "a\nc\n";
        String after = "a\nb1\nb2\nc\n";
        List<Hunk> hunks = LineDiff.diff(before, after);
        assertEquals(after, applyAll(before, hunks));
        Hunk change = firstChange(hunks);
        assertTrue(change.isInsertOnly());
        assertEquals(2, change.getNewCount());
    }

    @Test
    public void deletesLines() {
        String before = "a\nb1\nb2\nc\n";
        String after = "a\nc\n";
        List<Hunk> hunks = LineDiff.diff(before, after);
        assertEquals(after, applyAll(before, hunks));
        Hunk change = firstChange(hunks);
        assertTrue(change.isDeleteOnly());
        assertEquals(2, change.getOldCount());
    }

    @Test
    public void preservesTrailingNewline() {
        String before = "a\nb\n";
        String after = "a\nb\nc\n";
        assertEquals(after, applyAll(before, LineDiff.diff(before, after)));
        assertEquals(before, applyAll(after, LineDiff.diff(after, before)));
    }

    @Test
    public void roundTripsManyEdits() {
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            before.append("line ").append(i).append('\n');
            after.append(i % 7 == 0 ? "changed " + i : "line " + i).append('\n');
            if (i % 11 == 0) {
                after.append("inserted at ").append(i).append('\n');
            }
        }
        assertEquals(after.toString(), applyAll(before.toString(), LineDiff.diff(before.toString(), after.toString())));
        assertEquals(before.toString(), applyAll(after.toString(), LineDiff.diff(after.toString(), before.toString())));
    }

    @Test
    public void crlfLinesDiffCorrectly() {
        String before = "a\r\nb\r\n";
        String after = "a\r\nB\r\n";
        assertEquals(after, applyAll(before, LineDiff.diff(before, after)));
    }

    @Test
    public void hugeEditFallsBackToSingleReplacement() {
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            before.append("old ").append(i).append('\n');
            after.append("new ").append(i).append('\n');
        }
        List<Hunk> hunks = LineDiff.diff(before.toString(), after.toString());
        assertEquals(1, countChanges(hunks));
        assertEquals(after.toString(), applyAll(before.toString(), hunks));
    }

    private static int countChanges(List<Hunk> hunks) {
        int count = 0;
        for (Hunk hunk : hunks) {
            if (hunk.isChange()) {
                count++;
            }
        }
        return count;
    }

    private static Hunk firstChange(List<Hunk> hunks) {
        for (Hunk hunk : hunks) {
            if (hunk.isChange()) {
                return hunk;
            }
        }
        throw new AssertionError("no change hunk");
    }
}
