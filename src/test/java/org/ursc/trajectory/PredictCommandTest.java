package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.command.PredictCommand;

/**
 * Smoke test for the {@code predict} command: a single typed-in state (UTC + six Cartesian
 * elements) propagates end to end with a selected subset of force models and returns success.
 * Exercises the input parsing, ITRF->GCRF seed construction, force-toggle wiring and propagation
 * without needing the large EOP/space-weather/truth files.
 */
class PredictCommandTest {

    @Test
    void predictsFromTypedStateWithForceToggles() throws Exception {
        // the real Sentinel-3A Oct-7 2024 ITRF state, a short horizon, a minimal force subset
        final Args args = new Args(new String[] {
            "--utc", "2024-10-07T21:59:42",
            "--pos", "482304.989602,1077999.632437,-7091304.589548",
            "--vel", "7497.731211,-93.447784,495.577989",
            "--frame", "itrf",
            "--days", "0.02",
            "--gravity-degree", "6",
            "--no-drag", "--no-srp", "--no-albedo",
            "--no-solid-tides", "--no-ocean-tides", "--no-relativity",
        });
        assertEquals(0, new PredictCommand().run(args));
    }
}
