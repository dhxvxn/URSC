package org.ursc.trajectory.app.term;

import java.util.Locale;

/**
 * Renders a time series (or several overlaid series) as a text plot sized to the
 * terminal. Dependency-free; used for altitude/element decay plots and comparison
 * overlays.
 */
public final class AsciiChart {

    private AsciiChart() {
    }

    /** Terminal width from {@code $COLUMNS}, or 100 if unknown. */
    public static int terminalWidth() {
        final String cols = System.getenv("COLUMNS");
        if (cols != null) {
            try {
                return Math.max(40, Integer.parseInt(cols.trim()));
            } catch (final NumberFormatException ignored) {
                // fall through
            }
        }
        return 100;
    }

    /** Single-series convenience. */
    public static String render(final double[] x, final double[] y, final int width,
                                final int height, final String title,
                                final String yLabel, final String xLabel) {
        return render(x, new double[][] {y}, new char[] {'*'}, width, height, title, yLabel, xLabel);
    }

    /**
     * Render one or more y-series sharing the same x axis.
     *
     * @param x      x values (shared)
     * @param series one or more y arrays, each the same length as x
     * @param marks  plot character per series
     * @param width  total line width (including the ~10-char y-axis gutter)
     * @param height number of plot rows
     */
    public static String render(final double[] x, final double[][] series, final char[] marks,
                                final int width, final int height, final String title,
                                final String yLabel, final String xLabel) {
        final int gutter = 11;
        final int plotW = Math.max(10, width - gutter);
        final int plotH = Math.max(3, height);

        double xmin = Double.POSITIVE_INFINITY;
        double xmax = Double.NEGATIVE_INFINITY;
        double ymin = Double.POSITIVE_INFINITY;
        double ymax = Double.NEGATIVE_INFINITY;
        for (final double xv : x) {
            xmin = Math.min(xmin, xv);
            xmax = Math.max(xmax, xv);
        }
        for (final double[] y : series) {
            for (final double yv : y) {
                ymin = Math.min(ymin, yv);
                ymax = Math.max(ymax, yv);
            }
        }
        if (xmax <= xmin) {
            xmax = xmin + 1;
        }
        if (ymax <= ymin) {
            ymax = ymin + 1;
        }

        final char[][] grid = new char[plotH][plotW];
        for (final char[] row : grid) {
            java.util.Arrays.fill(row, ' ');
        }
        for (int s = 0; s < series.length; s++) {
            final char mark = marks[s % marks.length];
            final double[] y = series[s];
            for (int i = 0; i < x.length && i < y.length; i++) {
                final int col = (int) Math.round((x[i] - xmin) / (xmax - xmin) * (plotW - 1));
                final int row = (int) Math.round((ymax - y[i]) / (ymax - ymin) * (plotH - 1));
                if (col >= 0 && col < plotW && row >= 0 && row < plotH) {
                    grid[row][col] = mark;
                }
            }
        }

        final StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(Ansi.bold(title)).append('\n');
        }
        for (int r = 0; r < plotH; r++) {
            final String label;
            if (r == 0) {
                label = String.format(Locale.US, "%10.3g", ymax);
            } else if (r == plotH - 1) {
                label = String.format(Locale.US, "%10.3g", ymin);
            } else if (r == plotH / 2) {
                label = String.format(Locale.US, "%10.3g", (ymax + ymin) / 2.0);
            } else {
                label = " ".repeat(10);
            }
            sb.append(label).append(" |");
            sb.append(new String(grid[r]));
            sb.append('\n');
        }
        // x axis
        sb.append(" ".repeat(10)).append(" +").append("-".repeat(plotW)).append('\n');
        final String left = String.format(Locale.US, "%.3g", xmin);
        final String right = String.format(Locale.US, "%.3g", xmax);
        final int pad = Math.max(1, plotW - left.length() - right.length());
        sb.append(" ".repeat(12)).append(left).append(" ".repeat(pad)).append(right).append('\n');
        if (xLabel != null && !xLabel.isBlank()) {
            final int centre = Math.max(0, 12 + (plotW - xLabel.length()) / 2);
            sb.append(" ".repeat(centre)).append(Ansi.dim(xLabel)).append('\n');
        }
        if (yLabel != null && !yLabel.isBlank()) {
            sb.append(Ansi.dim("y: " + yLabel)).append('\n');
        }
        return sb.toString();
    }
}
