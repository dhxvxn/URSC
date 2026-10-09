package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.term.AsciiChart;
import org.ursc.trajectory.app.term.Table;

/** Unit tests for the zero-dependency CLI support classes. */
class CliSupportTest {

    @Test
    void argsParsePositionalsOptionsFlags() {
        final Args a = new Args(new String[] {
                "scenario.properties", "--plot", "alt", "--monte-carlo=20", "--quiet", "--f107-range", "70,220"
        });
        assertEquals("scenario.properties", a.positional(0));
        assertEquals("alt", a.get("plot", "none"));
        assertEquals(20, a.getInt("monte-carlo", 0));
        assertTrue(a.has("quiet"));
        final List<String> range = a.getList("f107-range");
        assertEquals(2, range.size());
        assertEquals("70", range.get(0));
        assertEquals("220", range.get(1));
    }

    @Test
    void argsDefaultsWhenAbsent() {
        final Args a = new Args(new String[] {"file"});
        assertEquals(5.0, a.getDouble("missing", 5.0), 0.0);
        assertFalse(a.has("nope"));
        assertEquals("def", a.get("x", "def"));
    }

    @Test
    void asciiChartRendersRequestedHeight() {
        final double[] x = {0, 1, 2, 3, 4};
        final double[] y = {10, 8, 6, 4, 2};
        final String chart = AsciiChart.render(x, y, 60, 10, "title", "alt", "t");
        final long rows = chart.lines().count();
        // >= plot height; it is non-empty and contains the title
        assertTrue(rows >= 10, "expected at least the plot rows");
        assertTrue(chart.contains("title"));
    }

    @Test
    void tableRendersHeaderAndRows() {
        final String out = new Table("A", "B").addRow("1", "2").addRow("30", "40").render();
        assertTrue(out.contains("A"));
        assertTrue(out.contains("30"));
        // header + separator + 2 rows
        assertEquals(4, out.lines().count());
    }
}
