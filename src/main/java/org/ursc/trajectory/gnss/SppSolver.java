package org.ursc.trajectory.gnss;

import java.util.List;

import org.ursc.trajectory.math.LinearSolver;
import org.ursc.trajectory.math.Vector3D;

/**
 * Single Point Positioning (SPP): estimates a receiver's Earth-fixed position and
 * clock offset at one epoch from pseudorange observations to four or more GPS
 * satellites, by iterated weighted least squares (Gauss-Newton).
 *
 * <p>Each pseudorange is modelled as
 * {@code P_i = ||r_rcv - r_sat_i|| + c*dt_rcv - c*dt_sat_i (+ atmosphere + noise)}.
 * The satellite position and clock offset are supplied in {@link SatelliteRange}
 * (resolved from ephemeris beforehand), so the unknowns are the three receiver
 * coordinates and the receiver clock offset scaled to a range, {@code cb = c*dt_rcv}.
 * Writing the satellite-clock-corrected observation {@code y_i = P_i + c*dt_sat_i},
 * the model is {@code y_i = ||r_rcv - r_sat_i|| + cb}, whose partials with respect to
 * {@code [x, y, z, cb]} form the row {@code [e_x, e_y, e_z, 1]}, where {@code e} is
 * the unit vector from the satellite to the receiver.</p>
 *
 * <p>Atmospheric effects are expected to be handled before the solve (for a
 * dual-frequency spaceborne receiver the ionosphere-free pseudorange combination
 * removes the first-order ionosphere, and there is no troposphere above the neutral
 * atmosphere), so this class is a pure geometric estimator and carries no force or
 * atmosphere model.</p>
 */
public final class SppSolver {

    /** Minimum satellites for a 4-parameter solve. */
    public static final int MIN_SATELLITES = 4;

    private final int maxIterations;
    private final double positionTolerance;

    /** Solver with sensible defaults (10 iterations, 1e-4 m position convergence). */
    public SppSolver() {
        this(10, 1.0e-4);
    }

    /**
     * @param maxIterations     maximum Gauss-Newton iterations
     * @param positionTolerance convergence threshold on the position step norm (m)
     */
    public SppSolver(final int maxIterations, final double positionTolerance) {
        this.maxIterations = maxIterations;
        this.positionTolerance = positionTolerance;
    }

    /** Solve starting from the Earth centre (robust default for a first fix). */
    public SppSolution solve(final List<SatelliteRange> observations) {
        return solve(observations, Vector3D.ZERO);
    }

    /**
     * Solve for receiver position and clock offset.
     *
     * @param observations four or more satellite ranges for the epoch
     * @param initialGuess a-priori receiver position (m); the previous epoch's fix,
     *                     or {@link Vector3D#ZERO} to start at the Earth centre
     * @return the estimated position, clock offset, and fit diagnostics
     * @throws IllegalArgumentException if fewer than {@link #MIN_SATELLITES} are given
     */
    public SppSolution solve(final List<SatelliteRange> observations, final Vector3D initialGuess) {
        final int m = observations.size();
        if (m < MIN_SATELLITES) {
            throw new IllegalArgumentException(
                    "SPP needs at least " + MIN_SATELLITES + " satellites, got " + m);
        }

        // unknowns: [x, y, z, cb] with cb = c * receiver clock offset (metres)
        final double[] x = {initialGuess.getX(), initialGuess.getY(), initialGuess.getZ(), 0.0};
        boolean converged = false;
        int iter = 0;

        while (iter < maxIterations) {
            iter++;
            // weighted normal equations: N = A^T W A (4x4), g = A^T W r (4)
            final double[][] n = new double[4][4];
            final double[] g = new double[4];
            final Vector3D rcv = new Vector3D(x[0], x[1], x[2]);

            for (final SatelliteRange obs : observations) {
                final Vector3D los = rcv.subtract(obs.getSatPosition()); // r_rcv - r_sat
                final double range = los.getNorm();
                final double[] row = {
                    los.getX() / range, los.getY() / range, los.getZ() / range, 1.0,
                };
                final double predicted = range + x[3];
                final double corrected = obs.getPseudorange()
                        + GpsConstants.SPEED_OF_LIGHT * obs.getSatClockBias();
                final double resid = corrected - predicted;
                final double w = obs.getWeight();
                for (int a = 0; a < 4; a++) {
                    for (int b = 0; b < 4; b++) {
                        n[a][b] += w * row[a] * row[b];
                    }
                    g[a] += w * row[a] * resid;
                }
            }

            final double[] delta = LinearSolver.solve(n, g);
            for (int a = 0; a < 4; a++) {
                x[a] += delta[a];
            }
            final double posStep = Math.sqrt(
                    delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2]);
            if (posStep < positionTolerance) {
                converged = true;
                break;
            }
        }

        return finish(observations, x, iter, converged);
    }

    /** Build residuals, RMS, and DOP at the converged solution. */
    private SppSolution finish(final List<SatelliteRange> observations, final double[] x,
                               final int iterations, final boolean converged) {
        final int m = observations.size();
        final Vector3D rcv = new Vector3D(x[0], x[1], x[2]);
        final double[] residuals = new double[m];
        final double[][] ata = new double[4][4]; // unweighted geometry matrix for DOP
        double sumSq = 0.0;

        for (int i = 0; i < m; i++) {
            final SatelliteRange obs = observations.get(i);
            final Vector3D los = rcv.subtract(obs.getSatPosition());
            final double range = los.getNorm();
            final double[] row = {
                los.getX() / range, los.getY() / range, los.getZ() / range, 1.0,
            };
            final double predicted = range + x[3];
            final double corrected = obs.getPseudorange()
                    + GpsConstants.SPEED_OF_LIGHT * obs.getSatClockBias();
            final double resid = corrected - predicted;
            residuals[i] = resid;
            sumSq += resid * resid;
            for (int a = 0; a < 4; a++) {
                for (int b = 0; b < 4; b++) {
                    ata[a][b] += row[a] * row[b];
                }
            }
        }

        final double postFitRms = Math.sqrt(sumSq / m);
        double gdop = Double.NaN;
        double pdop = Double.NaN;
        double tdop = Double.NaN;
        try {
            final double[][] q = LinearSolver.invert(ata);
            final double pos = q[0][0] + q[1][1] + q[2][2];
            gdop = Math.sqrt(pos + q[3][3]);
            pdop = Math.sqrt(pos);
            tdop = Math.sqrt(q[3][3]);
        } catch (final IllegalArgumentException singular) {
            // leave DOP as NaN when the geometry matrix is not invertible
        }

        return new SppSolution(rcv, x[3], m, iterations, converged, postFitRms,
                gdop, pdop, tdop, residuals);
    }
}
