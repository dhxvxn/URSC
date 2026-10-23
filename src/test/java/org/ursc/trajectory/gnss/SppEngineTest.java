package org.ursc.trajectory.gnss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Closed-loop test for {@link SppEngine}: build known satellite ephemerides and a
 * known receiver, synthesize the pseudoranges that the engine's own transmit-time /
 * Sagnac / clock model implies, and confirm the engine recovers the receiver
 * position and clock. (Absolute sign conventions are validated separately against
 * the POEORB truth on real data.)
 */
class SppEngineTest {

    private static final double C = GpsConstants.SPEED_OF_LIGHT;
    private static final AbsoluteDate TRX =
            new AbsoluteDate(2024, 10, 16, 0, 0, 0.0, TimeScalesFactory.getGPS());
    private static final double SQRT_A = 5153.65;

    /** Circular-orbit GPS ephemeris with the given node and mean anomaly. */
    private static GpsBroadcastEphemeris sat(final int prn, final double omega0, final double m0) {
        return new GpsBroadcastEphemeris(prn, TRX, TRX, 0.0, 1.0e-4, 0.0, 0.0,
                SQRT_A, 0.0, m0, 0.0,
                omega0, 0.96, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    @Test
    void recoversKnownReceiver() {
        // six satellites spread around the orbital sphere for good geometry
        final List<GpsBroadcastEphemeris> sats = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sats.add(sat(i + 1, i, (i * 1.3) % 6.283));
        }

        final Vector3D rTrue = new Vector3D(2.5e6, -4.5e6, 5.2e6);
        final double cbTrue = 1234.5;

        // synthesize the pseudoranges consistent with the engine's geometry model
        final Map<String, Integer> codeIdx = new LinkedHashMap<>();
        codeIdx.put("C1W", 0);
        codeIdx.put("C2W", 1);
        final Map<Integer, List<GpsBroadcastEphemeris>> navMap = new LinkedHashMap<>();
        final List<RinexObsReader.SatRecord> records = new ArrayList<>();

        for (final GpsBroadcastEphemeris eph : sats) {
            double tau = 0.07;
            for (int k = 0; k < 6; k++) {
                final Vector3D s = SppEngine.satellitePositionAtReception(eph, TRX, tau);
                tau = rTrue.distance(s) / C;
            }
            final double dts = eph.clockBias(TRX.shiftedBy(-tau));
            final double p = C * tau + cbTrue - C * dts;
            // iono-free(P,P) == P, so set both frequency codes to the target pseudorange
            records.add(new RinexObsReader.SatRecord(eph.getPrn(), codeIdx, new double[] {p, p}));
            navMap.put(eph.getPrn(), List.of(eph));
        }

        final RinexObsReader.Epoch epoch = new RinexObsReader.Epoch(TRX, 0.0, records);
        final RinexNavReader.Data nav = new RinexNavReader.Data(navMap);

        final SppSolution sol = new SppEngine().solveEpoch(epoch, nav, Vector3D.ZERO);

        assertNotNull(sol, "epoch should be solvable with six satellites");
        assertTrue(sol.isConverged(), "engine did not converge");
        assertEquals(6, sol.getUsedSatellites());
        assertTrue(sol.getPosition().distance(rTrue) < 1.0e-3,
                "position error " + sol.getPosition().distance(rTrue) + " m");
        assertEquals(cbTrue, sol.getClockBias(), 1.0e-3, "receiver clock bias");
        assertTrue(sol.getPostFitRms() < 1.0e-3, "post-fit RMS " + sol.getPostFitRms());
    }
}
