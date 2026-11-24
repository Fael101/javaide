package com.duy.ide.ai.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * Line-level diff, used to show what the assistant proposes before anything is written.
 * <p>
 * The algorithm is a plain longest-common-subsequence table over the region between the common
 * prefix and suffix. That is simple and produces readable hunks, but its memory is quadratic, so
 * very large edits fall back to one replacement hunk instead of exhausting the heap. Degrading
 * to a coarse diff is better than crashing inside an editor on a phone.
 */
public final class LineDiff {

    /** Above this many table cells, use the coarse fallback. 4M cells is about 16 MB. */
    private static final int MAX_CELLS = 4000000;

    private LineDiff() {
    }

    /**
     * Splits on {@code \n} keeping a trailing empty element, so joining with {@code \n}
     * reproduces the input byte for byte. Carriage returns are left in place: a CRLF file then
     * diffs correctly because every line keeps its {@code \r}.
     */
    public static String[] splitLines(String text) {
        if (text == null) {
            return new String[0];
        }
        return text.split("\n", -1);
    }

    public static List<Hunk> diff(String oldText, String newText) {
        String[] a = splitLines(oldText);
        String[] b = splitLines(newText);

        int start = 0;
        while (start < a.length && start < b.length && a[start].equals(b[start])) {
            start++;
        }
        int endA = a.length;
        int endB = b.length;
        while (endA > start && endB > start && a[endA - 1].equals(b[endB - 1])) {
            endA--;
            endB--;
        }

        List<Hunk> hunks = new ArrayList<Hunk>();
        if (start > 0) {
            hunks.add(Hunk.equal(0, 0, slice(a, 0, start)));
        }

        int middleOld = endA - start;
        int middleNew = endB - start;
        if (middleOld > 0 || middleNew > 0) {
            if ((long) middleOld * (long) middleNew > MAX_CELLS) {
                hunks.add(Hunk.change(start, slice(a, start, endA),
                        start, slice(b, start, endB)));
            } else {
                int[][] table = lcsTable(a, b, start, endA, start, endB);
                hunks.addAll(walk(a, b, start, endA, start, endB, table));
            }
        }

        if (endA < a.length) {
            hunks.add(Hunk.equal(endA, endB, slice(a, endA, a.length)));
        }
        return hunks;
    }

    private static int[][] lcsTable(String[] a, String[] b, int a0, int a1, int b0, int b1) {
        int n = a1 - a0;
        int m = b1 - b0;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a[a0 + i].equals(b[b0 + j])) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        return dp;
    }

    private static List<Hunk> walk(String[] a, String[] b, int a0, int a1, int b0, int b1,
                                   int[][] dp) {
        int n = a1 - a0;
        int m = b1 - b0;
        List<Hunk> out = new ArrayList<Hunk>();
        List<String> deleted = new ArrayList<String>();
        List<String> inserted = new ArrayList<String>();
        int oldStart = 0;
        int newStart = 0;
        int i = 0;
        int j = 0;

        while (i < n || j < m) {
            boolean bothAvailable = i < n && j < m;
            if (bothAvailable && a[a0 + i].equals(b[b0 + j])) {
                if (!deleted.isEmpty() || !inserted.isEmpty()) {
                    out.add(Hunk.change(oldStart, deleted, newStart, inserted));
                    deleted = new ArrayList<String>();
                    inserted = new ArrayList<String>();
                }
                int runOld = a0 + i;
                int runNew = b0 + j;
                List<String> equal = new ArrayList<String>();
                while (i < n && j < m && a[a0 + i].equals(b[b0 + j])) {
                    equal.add(a[a0 + i]);
                    i++;
                    j++;
                }
                out.add(Hunk.equal(runOld, runNew, equal));
            } else {
                if (deleted.isEmpty() && inserted.isEmpty()) {
                    oldStart = a0 + i;
                    newStart = b0 + j;
                }
                boolean preferDelete = i < n && (j >= m || dp[i + 1][j] >= dp[i][j + 1]);
                if (preferDelete) {
                    deleted.add(a[a0 + i]);
                    i++;
                } else {
                    inserted.add(b[b0 + j]);
                    j++;
                }
            }
        }

        if (!deleted.isEmpty() || !inserted.isEmpty()) {
            out.add(Hunk.change(oldStart, deleted, newStart, inserted));
        }
        return out;
    }

    private static List<String> slice(String[] a, int from, int to) {
        List<String> out = new ArrayList<String>(Math.max(0, to - from));
        for (int i = from; i < to; i++) {
            out.add(a[i]);
        }
        return out;
    }
}
