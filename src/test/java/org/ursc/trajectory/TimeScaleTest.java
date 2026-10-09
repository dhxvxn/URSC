package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/** Time-scale offsets, leap seconds and calendar round-trips. */
class TimeScaleTest {

    @Test
    void ttIsAheadOfTaiByConstant() {
        final AbsoluteDate d = new AbsoluteDate(2024, 6, 1, 12, 0, 0.0, TimeScalesFactory.getTAI());
        final double[] tt = d.getComponents(TimeScalesFactory.getTT());
        // TT = TAI + 32.184 s -> 12:00:32.184 (double precision at this epoch is ~0.2 us)
        assertEquals(32.184, tt[5], 1.0e-6);
    }

    @Test
    void modernUtcTaiOffsetIs37Seconds() {
        final AbsoluteDate d = new AbsoluteDate(2024, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        // TAI - UTC = 37 s for dates after 2017-01-01
        final double offset = TimeScalesFactory.getUTC().offsetFromTAI(d);
        assertEquals(-37.0, offset, 1.0e-9);
    }

    @Test
    void calendarRoundTrip() {
        final AbsoluteDate d =
                new AbsoluteDate(2023, 3, 15, 7, 42, 9.5, TimeScalesFactory.getUTC());
        final double[] c = d.getComponents(TimeScalesFactory.getUTC());
        assertEquals(2023, (int) c[0]);
        assertEquals(3, (int) c[1]);
        assertEquals(15, (int) c[2]);
        assertEquals(7, (int) c[3]);
        assertEquals(42, (int) c[4]);
        assertEquals(9.5, c[5], 1.0e-6);
    }

    @Test
    void durationIsLeapSecondFree() {
        final AbsoluteDate a = new AbsoluteDate(2017, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        final AbsoluteDate b = new AbsoluteDate(2017, 1, 1, 1, 0, 0.0, TimeScalesFactory.getUTC());
        assertEquals(3600.0, b.durationFrom(a), 1.0e-9);
    }
}
