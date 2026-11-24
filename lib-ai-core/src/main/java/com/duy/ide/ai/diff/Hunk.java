package com.duy.ide.ai.diff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One contiguous region of a line diff.
 * <p>
 * Equal hunks exist so a diff can be rendered as a complete document; only hunks where
 * {@link #isChange()} is true can be accepted or rejected individually.
 */
public final class Hunk {

    private final int oldStart;
    private final int newStart;
    private final List<String> oldLines;
    private final List<String> newLines;

    private Hunk(int oldStart, List<String> oldLines, int newStart, List<String> newLines) {
        this.oldStart = oldStart;
        this.newStart = newStart;
        this.oldLines = Collections.unmodifiableList(new ArrayList<String>(oldLines));
        this.newLines = Collections.unmodifiableList(new ArrayList<String>(newLines));
    }

    public static Hunk equal(int oldStart, int newStart, List<String> lines) {
        return new Hunk(oldStart, lines, newStart, lines);
    }

    public static Hunk change(int oldStart, List<String> oldLines, int newStart,
                              List<String> newLines) {
        return new Hunk(oldStart, oldLines, newStart, newLines);
    }

    /** 0-based first line in the original document. */
    public int getOldStart() {
        return oldStart;
    }

    /** 0-based first line in the resulting document. */
    public int getNewStart() {
        return newStart;
    }

    public List<String> getOldLines() {
        return oldLines;
    }

    public List<String> getNewLines() {
        return newLines;
    }

    public int getOldCount() {
        return oldLines.size();
    }

    public int getNewCount() {
        return newLines.size();
    }

    public boolean isChange() {
        return !oldLines.equals(newLines);
    }

    /** True when lines were removed and none added. */
    public boolean isDeleteOnly() {
        return newLines.isEmpty() && !oldLines.isEmpty();
    }

    /** True when lines were added and none removed. */
    public boolean isInsertOnly() {
        return oldLines.isEmpty() && !newLines.isEmpty();
    }

    @Override
    public String toString() {
        return "Hunk{old=" + oldStart + "+" + oldLines.size()
                + ", new=" + newStart + "+" + newLines.size() + '}';
    }
}
