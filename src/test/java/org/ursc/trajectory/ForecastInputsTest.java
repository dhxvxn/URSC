package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.forces.drag.ForecastJb2008Inputs;
import org.ursc.trajectory.forces.drag.SolfsmyDtcProvider;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/** Verifies forecast-mode drivers: observed before the anchor, persisted solar + scenario storm
 *  after it. */
class ForecastInputsTest {

    private static final String SOLFSMY =
            "  2020 100 2458950.0 100.0 110.0 50.0 55.0 60.0 65.0 70.0 75.0 1B11\n"
          + "  2020 101 2458951.0 200.0 110.0 50.0 55.0 60.0 65.0 71.0 75.0 1B11\n"
          + "  2020 102 2458952.0 300.0 110.0 50.0 55.0 60.0 65.0 72.0 75.0 1B11\n"
          + "  2020 103 2458953.0 400.0 110.0 50.0 55.0 60.0 65.0 73.0 75.0 1B11\n";

    private static String dtcLine() {
        final StringBuilder sb = new StringBuilder("DTC 2020 100");
        for (int h = 0; h < 24; h++) {
            sb.append(' ').append(40);
        }
        return sb.append('\n').toString();
    }

    private static AbsoluteDate noonDoy(final int doy) {
        return new AbsoluteDate(2020, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC())
                .shiftedBy((doy - 1) * 86400.0);
    }

    @Test
    void persistsSolarAndAppliesScenarioStorm() throws Exception {
        final SolfsmyDtcProvider obs = new SolfsmyDtcProvider(
                new ByteArrayInputStream(SOLFSMY.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayInputStream(dtcLine().getBytes(StandardCharsets.UTF_8)));

        final AbsoluteDate anchor = noonDoy(101);
        final double stormDtc = 250.0;
        final ForecastJb2008Inputs fc = new ForecastJb2008Inputs(obs, anchor, stormDtc);

        // before the anchor: identical to observed
        assertEquals(obs.getF10(noonDoy(100)), fc.getF10(noonDoy(100)), 1.0e-9);

        // after the anchor: solar persisted at the anchor value (1-day lag -> DOY100 = 100)
        assertEquals(100.0, fc.getF10(noonDoy(103)), 1.0e-9);
        assertEquals(obs.getF10(anchor), fc.getF10(noonDoy(103)), 1.0e-9);

        // after the anchor: DTC follows the scenario, not the file
        assertEquals(stormDtc, fc.getDSTDTC(noonDoy(103)), 1.0e-9);
        // before the anchor: DTC still observed (40)
        assertEquals(40.0, fc.getDSTDTC(noonDoy(100).shiftedBy(3600.0)), 1.0e-9);
    }
}
