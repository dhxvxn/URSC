package org.ursc.trajectory.estimation;

import java.util.List;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Batch least-squares orbit determination. Estimates a parameter vector
 * (initial position/velocity, and optionally a drag coefficient) by fitting the
 * propagated trajectory to position observations, via Gauss-Newton iteration with
 * parameter scaling and step-halving backtracking.
 *
 * <p>The design matrix (partials of computed position w.r.t. the parameters) is
 * formed by finite differences, so it works with any force model and either
 * propagation engine — no analytical variational equations required.</p>
 */
public final class BatchLeastSquares {

    /** Builds a seeded propagator from a parameter vector [rx,ry,rz,vx,vy,vz,(Cd)]. */
    public interface PropagatorFactory {
        SampledPropagator build(double[] params);
    }

    /** Outcome of an estimation run. */
    public static final class Result {
        public final double[] params;
        public final double preFitRms;   // m, per-point position RMS before the fit
        public final double postFitRms;  // m, after the fit
        public final int iterations;
        public final boolean converged;

        Result(final double[] params, final double preFitRms, final double postFitRms,
               final int iterations, final boolean converged) {
            this.params = params;
            this.preFitRms = preFitRms;
            this.postFitRms = postFitRms;
            this.iterations = iterations;
            this.converged = converged;
        }
    }

    private final PropagatorFactory factory;
    private final List<PositionObservation> obs;
    private final double[] scale;     // finite-difference step & column scaling per parameter
    private final AbsoluteDate epoch;
    private final double obsStep;

    public BatchLeastSquares(final PropagatorFactory factory, final List<PositionObservation> obs,
                             final double[] scale) {
        if (obs.size() < 2) {
            throw new IllegalArgumentException("need at least two observations");
        }
        this.factory = factory;
        this.obs = obs;
        this.scale = scale.clone();
        this.epoch = obs.get(0).getDate();
        this.obsStep = obs.get(1).getDate().durationFrom(epoch);
    }

    public Result estimate(final double[] guess, final int maxIterations) {
        double[] params = guess.clone();
        final int p = params.length;
        final int m = obs.size();

        Vector3D[] comp = computed(params);
        double rms = rms(comp);
        final double preRms = rms;
        boolean converged = false;
        int iter = 0;

        for (; iter < maxIterations; iter++) {
            // residuals r = observed - computed
            final double[] r = new double[3 * m];
            for (int i = 0; i < m; i++) {
                final Vector3D d = obs.get(i).getPosition().subtract(comp[i]);
                r[3 * i] = d.getX();
                r[3 * i + 1] = d.getY();
                r[3 * i + 2] = d.getZ();
            }

            // scaled design matrix columns J~_j = (d comp / d p_j) * scale_j
            final double[][] jt = new double[p][3 * m]; // transposed: jt[j] is column j
            for (int j = 0; j < p; j++) {
                final double[] pert = params.clone();
                pert[j] += scale[j];
                final Vector3D[] cj = computed(pert);
                for (int i = 0; i < m; i++) {
                    final Vector3D dd = cj[i].subtract(comp[i]); // * (1/scale) * scale = identity scaling
                    jt[j][3 * i] = dd.getX();
                    jt[j][3 * i + 1] = dd.getY();
                    jt[j][3 * i + 2] = dd.getZ();
                }
            }

            // normal equations N dtilde = g  (N = J~^T J~, g = J~^T r)
            final double[][] n = new double[p][p];
            final double[] g = new double[p];
            for (int a = 0; a < p; a++) {
                for (int b = a; b < p; b++) {
                    double sum = 0.0;
                    for (int k = 0; k < 3 * m; k++) {
                        sum += jt[a][k] * jt[b][k];
                    }
                    n[a][b] = sum;
                    n[b][a] = sum;
                }
                double gg = 0.0;
                for (int k = 0; k < 3 * m; k++) {
                    gg += jt[a][k] * r[k];
                }
                g[a] = gg;
            }
            // tiny Levenberg-Marquardt damping for stability
            for (int a = 0; a < p; a++) {
                n[a][a] += 1.0e-9 * n[a][a] + 1.0e-30;
            }

            final double[] dtilde = solve(n, g);

            // step with backtracking; dp_j = dtilde_j * scale_j
            double step = 1.0;
            double[] best = params;
            double bestRms = rms;
            Vector3D[] bestComp = comp;
            for (int bt = 0; bt < 6; bt++) {
                final double[] trial = params.clone();
                for (int j = 0; j < p; j++) {
                    trial[j] += step * dtilde[j] * scale[j];
                }
                final Vector3D[] trialComp = computed(trial);
                final double trialRms = rms(trialComp);
                if (trialRms < bestRms) {
                    best = trial;
                    bestRms = trialRms;
                    bestComp = trialComp;
                    break;
                }
                step *= 0.5;
            }

            final double improvement = (rms - bestRms) / rms;
            params = best;
            comp = bestComp;
            rms = bestRms;
            if (improvement < 1.0e-4) {
                converged = true;
                iter++;
                break;
            }
        }
        return new Result(params, preRms, rms, iter, converged);
    }

    private Vector3D[] computed(final double[] params) {
        final SampledPropagator prop = factory.build(params);
        final EphemerisCollector col = new EphemerisCollector();
        prop.setStepHandler(obsStep, col);
        prop.propagate(epoch.shiftedBy((obs.size() - 1) * obsStep));
        final List<SpacecraftState> states = col.getStates();
        final int m = obs.size();
        if (states.size() < m) {
            throw new IllegalStateException("propagator produced "
                    + states.size() + " samples, expected " + m);
        }
        final Vector3D[] pos = new Vector3D[m];
        for (int i = 0; i < m; i++) {
            pos[i] = states.get(i).getPosition();
        }
        return pos;
    }

    private double rms(final Vector3D[] comp) {
        double sumSq = 0.0;
        for (int i = 0; i < obs.size(); i++) {
            sumSq += obs.get(i).getPosition().subtract(comp[i]).getNormSq();
        }
        return Math.sqrt(sumSq / obs.size());
    }

    /** Gaussian elimination with partial pivoting; solves A x = b (A is P x P). */
    static double[] solve(final double[][] aIn, final double[] bIn) {
        final int n = bIn.length;
        final double[][] a = new double[n][n];
        final double[] b = bIn.clone();
        for (int i = 0; i < n; i++) {
            a[i] = aIn[i].clone();
        }
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) {
                    piv = r;
                }
            }
            final double[] tmp = a[col];
            a[col] = a[piv];
            a[piv] = tmp;
            final double tb = b[col];
            b[col] = b[piv];
            b[piv] = tb;

            final double d = a[col][col];
            for (int r = col + 1; r < n; r++) {
                final double f = a[r][col] / d;
                for (int c = col; c < n; c++) {
                    a[r][c] -= f * a[col][c];
                }
                b[r] -= f * b[col];
            }
        }
        final double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = b[i];
            for (int c = i + 1; c < n; c++) {
                s -= a[i][c] * x[c];
            }
            x[i] = s / a[i][i];
        }
        return x;
    }
}
