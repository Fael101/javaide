package com.duy.ide.ai.diff;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PatchApplierTest {

    private static final String BEFORE = "one\ntwo\nthree\n";
    private static final String AFTER = "one\nTWO\nthree\nfour\n";

    @Test
    public void rejectingEverythingReturnsOriginal() {
        List<Hunk> hunks = LineDiff.diff(BEFORE, AFTER);
        boolean[] accepted = new boolean[hunks.size()];
        assertEquals(BEFORE, PatchApplier.apply(BEFORE, hunks, accepted));
    }

    @Test
    public void nullFlagsAcceptEverything() {
        List<Hunk> hunks = LineDiff.diff(BEFORE, AFTER);
        assertEquals(AFTER, PatchApplier.apply(BEFORE, hunks, null));
    }

    @Test
    public void acceptingOnlyTheFirstChangeKeepsTheRest() {
        List<Hunk> hunks = LineDiff.diff(BEFORE, AFTER);
        boolean[] accepted = new boolean[hunks.size()];
        for (int i = 0; i < hunks.size(); i++) {
            if (hunks.get(i).isChange()) {
                accepted[i] = true;
                break;
            }
        }
        String result = PatchApplier.apply(BEFORE, hunks, accepted);
        assertEquals("one\nTWO\nthree\n", result);
    }

    @Test
    public void acceptsOnlyTrailingInsertion() {
        List<Hunk> hunks = LineDiff.diff(BEFORE, AFTER);
        boolean[] accepted = new boolean[hunks.size()];
        for (int i = hunks.size() - 1; i >= 0; i--) {
            if (hunks.get(i).isChange()) {
                accepted[i] = true;
                break;
            }
        }
        String result = PatchApplier.apply(BEFORE, hunks, accepted);
        assertEquals("one\ntwo\nthree\nfour\n", result);
    }
}
