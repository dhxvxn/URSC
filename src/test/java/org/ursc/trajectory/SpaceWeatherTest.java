package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.forces.drag.ConstantSpaceWeather;
import org.ursc.trajectory.forces.drag.CssiSpaceWeatherProvider;
import org.ursc.trajectory.forces.drag.CssiTxtSpaceWeatherProvider;
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

    // Minimal AGI/Orekit CSSI text format (same column layout as SpaceWeather-All-v1.2.txt):
    // yy mm dd BSRN ND Kp(8) KpSum ap(8) ApAvg Cp C9 ISN F10.7adj Q Ctr81adj Lst81adj F10.7obs Ctr81obs Lst81obs
    private static final String TXT =
            "DATATYPE CssiSpaceWeather\nVERSION 1.2\n# header comment\n"
            + "NUM_OBSERVED_POINTS 2\nBEGIN OBSERVED\n"
            + "2024 10 01 2607 2 13 20 13 7 10 20 13 10 107 5 7 5 3 4 7 5 4 5 0.2 1 213 245.1 0 215.6 224.0 244.6 215.3 219.2\n"
            + "2024 10 02 2607 3 20 17 17 13 20 7 13 10 117 7 6 6 5 7 3 5 4 7 0.2 1 211 274.8 0 214.9 224.3 274.4 214.7 219.7\n"
            + "END OBSERVED\n";

    @Test
    void readsOrekitCssiTxtFormat() throws Exception {
        final CssiTxtSpaceWeatherProvider p = new CssiTxtSpaceWeatherProvider(
                new ByteArrayInputStream(TXT.getBytes(StandardCharsets.UTF_8)),
                new ConstantSpaceWeather(150.0, 150.0, 4.0));
        final AbsoluteDate oct2 = new AbsoluteDate(2024, 10, 2, 0, 0, 0.0, TimeScalesFactory.getUTC());
        // daily F10.7 = previous day's observed flux (Oct 1 obs = 244.6)
        assertEquals(244.6, p.getDailyF107(oct2), 1e-9);
        // average = this day's observed 81-day centred flux; Ap = this day's average
        assertEquals(214.7, p.getAverageF107(oct2), 1e-9);
        assertEquals(7.0, p.getDailyAp(oct2), 1e-9);
        // outside the file -> fallback
        final AbsoluteDate far = new AbsoluteDate(2030, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        assertEquals(150.0, p.getAverageF107(far), 1e-9);
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
