package org.ursc.trajectory.app.term;

import java.util.ArrayList;
import java.util.List;

/** A simple fixed-width text table with a header row, for terminal summaries. */
public final class Table {

    private final String[] headers;
    private final List<String[]> rows = new ArrayList<>();

    public Table(final String... headers) {
        this.headers = headers;
    }

    public Table addRow(final String... cells) {
        if (cells.length != headers.length) {
            throw new IllegalArgumentException("expected " + headers.length + " cells, got " + cells.length);
        }
        rows.add(cells);
        return this;
    }

    public String render() {
        final int cols = headers.length;
        final int[] width = new int[cols];
        for (int c = 0; c < cols; c++) {
            width[c] = headers[c].length();
        }
        for (final String[] row : rows) {
            for (int c = 0; c < cols; c++) {
                width[c] = Math.max(width[c], row[c] == null ? 0 : row[c].length());
            }
        }

        final StringBuilder sb = new StringBuilder();
        appendRow(sb, headers, width, true);
        // separator
        for (int c = 0; c < cols; c++) {
            sb.append("-".repeat(width[c] + 2));
            if (c < cols - 1) {
                sb.append('+');
            }
        }
        sb.append('\n');
        for (final String[] row : rows) {
            appendRow(sb, row, width, false);
        }
        return sb.toString();
    }

    private void appendRow(final StringBuilder sb, final String[] cells, final int[] width,
                           final boolean header) {
        for (int c = 0; c < cells.length; c++) {
            final String cell = cells[c] == null ? "" : cells[c];
            final String padded = " " + cell + " ".repeat(width[c] - cell.length()) + " ";
            sb.append(header ? Ansi.bold(padded) : padded);
            if (c < cells.length - 1) {
                sb.append('|');
            }
        }
        sb.append('\n');
    }
}
