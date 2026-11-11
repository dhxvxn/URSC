package org.ursc.trajectory.gnss.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.gnss.GpsBroadcastEphemeris;
import org.ursc.trajectory.pipeline.TimeUtil;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Consecutive daily broadcast files must act as one source: just after midnight the new day's
 * fresh messages are used, not the previous day's last messages extrapolated for hours.
 */
class BroadcastMergeTest {

    @Test
    void consecutiveDailyFilesUseTheNearestMessage() throws Exception {
        final Path dir = Paths.get("data/gnss/navigation/2024");
        final Path day1 = dir.resolve("BRD400DLR_S_20242460000_01D_MN.rnx.gz");
        final Path day2 = dir.resolve("BRD400DLR_S_20242470000_01D_MN.rnx.gz");
        assumeTrue(Files.isRegularFile(day1) && Files.isRegularFile(day2), "repository navigation data not present");

        final ProductLoader.Loaded both = ProductLoader.load(List.of(day1, day2), Map.of());
        assertEquals(1, both.sources.size(), "all broadcast files form one source");
        final BroadcastSource src = (BroadcastSource) both.sources.get(0);

        final AbsoluteDate t = TimeUtil.parseUtc("2024-09-03T00:30:00");
        int near = 0;
        int n = 0;
        for (final int prn : src.data().prns()) {
            final GpsBroadcastEphemeris e = src.data().ephemerisFor(prn, t);
            if (e == null) {
                continue;
            }
            n++;
            if (Math.abs(t.durationFrom(e.getToe())) <= 3600.0 + 60.0) {
                near++;
            }
        }
        assertTrue(n > 20, "expected the GPS constellation, got " + n);
        assertTrue(near >= 0.9 * n, near + " of " + n + " satellites use a message within an hour of the epoch");
    }
}
