package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.forces.drag.ConstantSpaceWeather;
import org.ursc.trajectory.forces.drag.CssiSpaceWeatherProvider;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/** Parsing and lookup of a CelesTrak-format space-weather CSV. */
class SpaceWeatherTest {

    // Minimal CelesTrak SW-All.csv header subset plus two days.
    private static final String CSV =
            "DATE,AP_AVG,F10.7_OBS,F10.7_OBS_CENTER81\n"
            + "2024-01-01,7,130.5,140.2\n"
            + "2024-01-02,12,135.0,141.0\n";

    private CssiSpaceWeatherProvider provider() throws Exception {
        return new CssiSpaceWeatherProvider(
                new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)),
                new ConstantSpaceWeather(150.0, 150.0, 4.0));
    }

    @Test
    void readsDailyAndAverageAndAp() throws Exception {
        final CssiSpaceWeatherProvider p = provider();
        final AbsoluteDate jan2 = new AbsoluteDate(2024, 1, 2, 0, 0, 0.0, TimeScalesFactory.getUTC());
        // daily F10.7 is the PREVIOUS day's observed flux
        assertEquals(130.5, p.getDailyF107(jan2), 1e-9);
        // average is this day's 81-day centred flux; Ap is this day's average
        assertEquals(141.0, p.getAverageF107(jan2), 1e-9);
        assertEquals(12.0, p.getDailyAp(jan2), 1e-9);
    }

    @Test
    void fallsBackOutsideTheFile() throws Exception {
        final CssiSpaceWeatherProvider p = provider();
        final AbsoluteDate far = new AbsoluteDate(2030, 6, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        assertEquals(150.0, p.getDailyF107(far), 1e-9);
        assertEquals(150.0, p.getAverageF107(far), 1e-9);
        assertEquals(4.0, p.getDailyAp(far), 1e-9);
    }
}
