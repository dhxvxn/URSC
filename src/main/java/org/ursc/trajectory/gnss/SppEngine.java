package org.ursc.trajectory.gnss;

import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.gnss.products.GnssOrbitClockSource;
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
        return rotateForTransit(eph.positionEcef(tRx.shiftedBy(-tau)), tau);
    }

    /** Rotate a transmit-epoch ECEF position into the reception-epoch frame (Sagnac). */
    static Vector3D rotateForTransit(final Vector3D satTx, final double tau) {
        final double theta = WE * tau; // Earth rotation during transit
        final double cos = Math.cos(theta);
        final double sin = Math.sin(theta);
        return new Vector3D(
                cos * satTx.getX() + sin * satTx.getY(),
                -sin * satTx.getX() + cos * satTx.getY(),
                satTx.getZ());
    }

    /**
     * Solve one observation epoch from broadcast navigation (nearest-Toe message, no age limit).
     *
     * @param epoch the observation epoch
     * @param nav   the broadcast navigation data
     * @param guess a-priori receiver position (previous fix, or {@link Vector3D#ZERO})
     * @return the fix, or {@code null} if fewer than four satellites were usable
     */
    public SppSolution solveEpoch(final RinexObsReader.Epoch epoch, final RinexNavReader.Data nav,
                                  final Vector3D guess) {
        return solveEpoch(epoch, new NearestToeSource(nav), guess);
    }

    /**
     * Solve one observation epoch from any GPS orbit/clock source (IGS Final, Rapid,
     * Ultra-rapid or broadcast). Satellites the source does not cover are skipped.
     *
     * @return the fix, or {@code null} if fewer than four satellites were usable
     */
    public SppSolution solveEpoch(final RinexObsReader.Epoch epoch, final GnssOrbitClockSource source,
                                  final Vector3D guess) {
        Vector3D rcv = guess;
        SppSolution solution = null;
        final AbsoluteDate tRx = epoch.getDate();

        for (int outer = 0; outer < outerIterations; outer++) {
            final List<SatelliteRange> ranges = new ArrayList<>();
            for (final RinexObsReader.SatRecord sr : epoch.getSatellites()) {
                final double p = sr.ionoFreePseudorange();
                if (Double.isNaN(p)) {
                    continue;
                }
                final int prn = sr.getPrn();
                double tau = p / C;
                if (!source.covers(prn, tRx.shiftedBy(-tau))) {
                    continue;
                }
                if (!Vector3D.ZERO.equals(rcv)) {
                    // refine travel time against the current receiver estimate
                    final Vector3D s0 = rotateForTransit(source.positionEcef(prn, tRx.shiftedBy(-tau)), tau);
                    tau = rcv.distance(s0) / C;
                }
                final AbsoluteDate tTx = tRx.shiftedBy(-tau);
                final Vector3D satRx = rotateForTransit(source.positionEcef(prn, tTx), tau);
                final double dts = source.clockBias(prn, tTx);
                ranges.add(new SatelliteRange(prn, satRx, p, dts));
            }
            if (ranges.size() < SppSolver.MIN_SATELLITES) {
                return null;
            }
            solution = solver.solve(ranges, rcv);
            rcv = solution.getPosition();
        }
        return solution;
    }

    /** Broadcast adapter reproducing the historical behaviour: nearest-Toe message, no age limit. */
    private static final class NearestToeSource implements GnssOrbitClockSource {
        private final RinexNavReader.Data nav;

        NearestToeSource(final RinexNavReader.Data nav) {
            this.nav = nav;
        }

        @Override
        public ProductClass productClass() {
            return ProductClass.BROADCAST;
        }

        @Override
        public String description() {
            return "broadcast navigation";
        }

        @Override
        public AbsoluteDate issueTime() {
            return null;
        }

        @Override
        public boolean issueTimeVerified() {
            return false;
        }

        @Override
        public boolean covers(final int prn, final AbsoluteDate t) {
            return nav.ephemerisFor(prn, t) != null;
        }

        @Override
        public Vector3D positionEcef(final int prn, final AbsoluteDate t) {
            return nav.ephemerisFor(prn, t).positionEcef(t);
        }

        @Override
        public double clockBias(final int prn, final AbsoluteDate t) {
            return nav.ephemerisFor(prn, t).clockBias(t);
        }
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
