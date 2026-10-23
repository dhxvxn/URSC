package org.ursc.trajectory.gnss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.math.Vector3D;

/**
 * Self-consistency tests for {@link SppSolver}: synthesize pseudoranges from a known
 * receiver position and clock against known satellite geometry, then check the solver
 * recovers them.
 */
class SppSolverTest {

    private static final double GPS_RADIUS = 26_560_000.0; // m, nominal GPS orbit radius
    private static final double C = GpsConstants.SPEED_OF_LIGHT;

    /** Eight scattered satellite directions giving well-conditioned geometry. */
    private static List<Vector3D> satellitePositions() {
        final double[][] dirs = {
            {1.0, 0.0, 0.3}, {-1.0, 0.2, 0.4}, {0.2, 1.0, 0.5}, {0.1, -1.0, 0.6},
            {0.7, 0.7, 1.0}, {-0.6, 0.6, 0.9}, {0.8, -0.5, 0.7}, {-0.4, -0.7, 0.8},
        };
        final List<Vector3D> sats = new ArrayList<>();
        for (final double[] d : dirs) {
            sats.add(new Vector3D(d).normalize().scalarMultiply(GPS_RADIUS));
        }
        return sats;
    }

    /** Build the satellite-clock offsets (s), a distinct small value per satellite. */
    private static double[] satelliteClocks(final int n) {
        final double[] dt = new double[n];
        for (int i = 0; i < n; i++) {
            dt[i] = (i - n / 2.0) * 1.0e-6; // +/- a few microseconds
        }
        return dt;
    }

    @Test
    void recoversPositionAndClockNoiseFree() {
        final List<Vector3D> sats = satellitePositions();
        final double[] satDt = satelliteClocks(sats.size());
        final Vector3D rTrue = new Vector3D(2.5e6, -4.5e6, 5.2e6); // ~950 km altitude ECEF
        final double cbTrue = 1234.5; // c * receiver clock offset, metres

        final List<SatelliteRange> obs = new ArrayList<>();
        for (int i = 0; i < sats.size(); i++) {
            final double range = rTrue.distance(sats.get(i));
            final double pr = range + cbTrue - C * satDt[i];
            obs.add(new SatelliteRange(i + 1, sats.get(i), pr, satDt[i]));
        }

        // cold start from the Earth centre
        final SppSolution sol = new SppSolver().solve(obs);

        assertTrue(sol.isConverged(), "solver did not converge");
        assertEquals(sats.size(), sol.getUsedSatellites());
        assertTrue(sol.getPosition().distance(rTrue) < 1.0e-3,
                "position error " + sol.getPosition().distance(rTrue) + " m");
        assertEquals(cbTrue, sol.getClockBias(), 1.0e-3, "clock bias");
        assertTrue(sol.getPostFitRms() < 1.0e-4, "post-fit RMS " + sol.getPostFitRms());
        assertTrue(sol.getPdop() > 0.0 && Double.isFinite(sol.getGdop()), "DOP not finite");
    }

    @Test
    void recoversPositionWithMeasurementNoise() {
        final List<Vector3D> sats = satellitePositions();
        final double[] satDt = satelliteClocks(sats.size());
        final Vector3D rTrue = new Vector3D(2.5e6, -4.5e6, 5.2e6);
        final double cbTrue = -500.0;
        final double sigma = 1.0; // m, pseudorange noise
        final Random rng = new Random(42);

        final List<SatelliteRange> obs = new ArrayList<>();
        for (int i = 0; i < sats.size(); i++) {
            final double range = rTrue.distance(sats.get(i));
            final double pr = range + cbTrue - C * satDt[i] + rng.nextGaussian() * sigma;
            obs.add(new SatelliteRange(i + 1, sats.get(i), pr, satDt[i]));
        }

        final SppSolution sol = new SppSolver().solve(obs, new Vector3D(2.0e6, -4.0e6, 5.0e6));

        assertTrue(sol.isConverged(), "solver did not converge");
        // with ~1 m noise and strong geometry the fix should be within a few metres
        assertTrue(sol.getPosition().distance(rTrue) < 5.0,
                "position error " + sol.getPosition().distance(rTrue) + " m");
        assertTrue(sol.getPostFitRms() < 3.0 * sigma, "post-fit RMS " + sol.getPostFitRms());
    }

    @Test
    void rejectsTooFewSatellites() {
        final List<Vector3D> sats = satellitePositions();
        final List<SatelliteRange> obs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            obs.add(new SatelliteRange(i + 1, sats.get(i), 2.0e7, 0.0));
        }
        assertThrows(IllegalArgumentException.class, () -> new SppSolver().solve(obs));
    }
}
