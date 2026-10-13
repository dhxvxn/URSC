package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.forces.drag.SolfsmyDtcProvider;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/** Verifies the SOLFSMY/DTCFILE parser, the JB2008 tabular lags, and linear interpolation. */
class SolfsmyDtcProviderTest {

    // F10 column = 100,200,300,400 on DOY 100..103 of 2020 (entries at 12 UT)
    private static final String SOLFSMY =
            "# header\n"
          + "  2020 100 2458950.0 100.0 110.0 50.0 55.0 60.0 65.0 70.0 75.0 1B11\n"
          + "  2020 101 2458951.0 200.0 110.0 50.0 55.0 60.0 65.0 71.0 75.0 1B11\n"
          + "  2020 102 2458952.0 300.0 110.0 50.0 55.0 60.0 65.0 72.0 75.0 1B11\n"
          + "  2020 103 2458953.0 400.0 110.0 50.0 55.0 60.0 65.0 73.0 75.0 1B11\n";

    // DTC hourly values 100..123 on DOY 100 of 2020
    private static String dtcLine() {
        final StringBuilder sb = new StringBuilder("DTC 2020 100");
        for (int h = 0; h < 24; h++) {
            sb.append(' ').append(100 + h);
        }
        return sb.append('\n').toString();
    }

    private static SolfsmyDtcProvider build() throws Exception {
        return new SolfsmyDtcProvider(
                new ByteArrayInputStream(SOLFSMY.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(dtcLine().getBytes(StandardCharsets.UTF_8)));
    }

    private static AbsoluteDate noonDoy(final int doy) {
        return new AbsoluteDate(2020, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC())
                .shiftedBy((doy - 1) * 86400.0);
    }

    @Test
    void f10OneDayLagOnEntry() throws Exception {
        final SolfsmyDtcProvider p = build();
        // query at 12 UT on DOY 102 -> 1-day lag -> 12 UT DOY 101 -> exactly 200
        assertEquals(200.0, p.getF10(noonDoy(102)), 1.0e-9);
    }

    @Test
    void f10OneDayLagInterpolated() throws Exception {
        final SolfsmyDtcProvider p = build();
        // query at 00 UT DOY 102 -> lag -> 00 UT DOY 101, halfway between DOY100(100) and DOY101(200) noons
        final AbsoluteDate midnight102 = noonDoy(102).shiftedBy(-12 * 3600.0);
        assertEquals(150.0, p.getF10(midnight102), 1.0e-6);
    }

    @Test
    void y10FiveDayLag() throws Exception {
        final SolfsmyDtcProvider p = build();
        // Y10 has a 5-day lag; query 12 UT DOY 106 -> 12 UT DOY 101 -> 71.0
        assertEquals(71.0, p.getY10(noonDoy(106)), 1.0e-9);
    }

    @Test
    void dtcHourlyInterpolation() throws Exception {
        final SolfsmyDtcProvider p = build();
        final AbsoluteDate base = new AbsoluteDate(2020, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC())
                .shiftedBy((100 - 1) * 86400.0);
        // exactly hour 5 -> 105
        assertEquals(105.0, p.getDSTDTC(base.shiftedBy(5 * 3600.0)), 1.0e-9);
        // hour 5.5 -> halfway 105..106 -> 105.5
        assertEquals(105.5, p.getDSTDTC(base.shiftedBy(5.5 * 3600.0)), 1.0e-6);
    }
}
