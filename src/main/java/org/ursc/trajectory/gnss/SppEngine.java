package org.ursc.trajectory.gnss;

import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Drives Single Point Positioning over a run of observation epochs: for each epoch
 * it resolves every satellite's transmit-time geometry and clock from the broadcast
 * ephemeris, assembles the {@link SatelliteRange}s, and calls the {@link SppSolver}.
 *
 * <p>Per satellite the signal travel time {@code tau} is estimated (from the
 * pseudorange, then refined against the current receiver estimate), the satellite
 * position is computed at the transmit epoch {@code t_rx - tau} and rotated into the
 * reception-epoch Earth-fixed frame (the Sagnac / Earth-rotation-during-transit
 * correction), and the satellite clock offset is evaluated at transmit time. An
 * outer loop repeats this with the refined receiver position so the travel-time
 * geometry is self-consistent. Each epoch's fix seeds the next epoch's guess.</p>
 */
public final class SppEngine {

    private static final double C = GpsConstants.SPEED_OF_LIGHT;
    private static final double WE = GpsConstants.EARTH_ROTATION_RATE;

    /** One epoch's result: the tag and the fix (null if the epoch had too few satellites). */
    public static final class Fix {
        private final AbsoluteDate date;
        private final SppSolution solution;

        Fix(final AbsoluteDate date, final SppSolution solution) {
            this.date = date;
            this.solution = solution;
        }

        public AbsoluteDate getDate() {
            return date;
        }

        /** @return the SPP solution, or {@code null} if the epoch was not solvable. */
        public SppSolution getSolution() {
            return solution;
        }
    }

    private final SppSolver solver;
    private final int outerIterations;

    public SppEngine() {
        this(new SppSolver(), 3);
    }

    /**
     * @param solver          the per-epoch least-squares solver
     * @param outerIterations travel-time/geometry refinement passes (2-3 is ample)
     */
    public SppEngine(final SppSolver solver, final int outerIterations) {
        this.solver = solver;
        this.outerIterations = outerIterations;
    }

    /**
     * Satellite Earth-fixed position at signal reception, i.e. the position at the
     * transmit epoch {@code tRx - tau} rotated into the reception-epoch ECEF frame.
     *
     * @param eph satellite broadcast ephemeris
     * @param tRx reception epoch (GPS)
     * @param tau signal travel time (s)
     * @return the satellite ECEF position (m) in the reception-epoch frame
     */
    public static Vector3D satellitePositionAtReception(final GpsBroadcastEphemeris eph,
            final AbsoluteDate tRx, final double tau) {
        final Vector3D satTx = eph.positionEcef(tRx.shiftedBy(-tau));
        final double theta = WE * tau; // Earth rotation during transit
        final double cos = Math.cos(theta);
        final double sin = Math.sin(theta);
        return new Vector3D(
                cos * satTx.getX() + sin * satTx.getY(),
                -sin * satTx.getX() + cos * satTx.getY(),
                satTx.getZ());
    }

    /**
     * Solve one observation epoch.
     *
     * @param epoch the observation epoch
     * @param nav   the broadcast navigation data
     * @param guess a-priori receiver position (previous fix, or {@link Vector3D#ZERO})
     * @return the fix, or {@code null} if fewer than four satellites were usable
     */
    public SppSolution solveEpoch(final RinexObsReader.Epoch epoch, final RinexNavReader.Data nav,
                                  final Vector3D guess) {
        Vector3D rcv = guess;
        SppSolution solution = null;

        for (int outer = 0; outer < outerIterations; outer++) {
            final List<SatelliteRange> ranges = new ArrayList<>();
            for (final RinexObsReader.SatRecord sr : epoch.getSatellites()) {
                final double p = sr.ionoFreePseudorange();
                if (Double.isNaN(p)) {
                    continue;
                }
                final GpsBroadcastEphemeris eph = nav.ephemerisFor(sr.getPrn(), epoch.getDate());
                if (eph == null) {
                    continue;
                }
                double tau = p / C;
                if (!Vector3D.ZERO.equals(rcv)) {
                    // refine travel time against the current receiver estimate
                    final Vector3D s0 = satellitePositionAtReception(eph, epoch.getDate(), tau);
                    tau = rcv.distance(s0) / C;
                }
                final Vector3D satRx = satellitePositionAtReception(eph, epoch.getDate(), tau);
                final double dts = eph.clockBias(epoch.getDate().shiftedBy(-tau));
                ranges.add(new SatelliteRange(sr.getPrn(), satRx, p, dts));
            }
            if (ranges.size() < SppSolver.MIN_SATELLITES) {
                return null;
            }
            solution = solver.solve(ranges, rcv);
            rcv = solution.getPosition();
        }
        return solution;
    }

    /**
     * Solve every epoch in an observation run, carrying each converged fix forward as
     * the next epoch's a-priori guess.
     *
     * @return one {@link Fix} per epoch, in order (a Fix may hold a {@code null} solution)
     */
    public List<Fix> run(final RinexObsReader.Data obs, final RinexNavReader.Data nav) {
        final List<Fix> fixes = new ArrayList<>();
        Vector3D guess = Vector3D.ZERO;
        for (final RinexObsReader.Epoch epoch : obs.getEpochs()) {
            final SppSolution solution = solveEpoch(epoch, nav, guess);
            fixes.add(new Fix(epoch.getDate(), solution));
            if (solution != null && solution.isConverged()) {
                guess = solution.getPosition();
            }
        }
        return fixes;
    }
}
