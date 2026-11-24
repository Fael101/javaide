package com.duy.ide.ai.diff;

import java.util.List;

/** Rebuilds a document from a diff, honouring which change hunks the user accepted. */
public final class PatchApplier {

    private PatchApplier() {
    }

    /**
     * @param original the text the diff was computed from
     * @param hunks    the result of {@link LineDiff#diff}
     * @param accepted one flag per hunk; {@code null} accepts everything. Flags on equal hunks
     *                 are ignored, because there is nothing to change.
     * @return the original text with the accepted hunks applied
     */
    public static String apply(String original, List<Hunk> hunks, boolean[] accepted) {
        String[] lines = LineDiff.splitLines(original);
        StringBuilder out = new StringBuilder();
        boolean first = true;
        int position = 0;

        for (int index = 0; index < hunks.size(); index++) {
            Hunk hunk = hunks.get(index);
            int start = hunk.getOldStart();
            if (start < position) {
                start = position;
            }
            while (position < start && position < lines.length) {
                first = append(out, lines[position], first);
                position++;
            }
            boolean useNew = hunk.isChange()
                    && (accepted == null || (index < accepted.length && accepted[index]));
            List<String> emitted = useNew ? hunk.getNewLines() : hunk.getOldLines();
            for (String line : emitted) {
                first = append(out, line, first);
            }
            int next = hunk.getOldStart() + hunk.getOldLines().size();
            position = next > position ? next : position;
        }

        while (position < lines.length) {
            first = append(out, lines[position], first);
            position++;
        }
        return out.toString();
    }

    private static boolean append(StringBuilder out, String line, boolean first) {
        if (!first) {
            out.append('\n');
        }
        out.append(line);
        return false;
    }
}
