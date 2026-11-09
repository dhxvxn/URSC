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
        /**
         * Formal parameter covariance (parameter units squared), or {@code null} if not computed.
         * Covariance-weighted fits: {@code (J^T W J)^-1}. Unit-weighted fits: scaled by the
         * a-posteriori variance factor.
         */
        public final double[][] covariance;
        /** A-posteriori variance factor: weighted residual sum of squares / (3m - p). */
        public final double varianceFactor;
        /** Whether the observations carried covariances (covariance-weighted fit). */
        public final boolean weighted;
        /** Post-fit position residuals (observed - computed), one per observation. */
        public final Vector3D[] residuals;

        Result(final double[] params, final double preFitRms, final double postFitRms,
               final int iterations, final boolean converged) {
            this(params, preFitRms, postFitRms, iterations, converged, null, Double.NaN, false, null);
        }

        Result(final double[] params, final double preFitRms, final double postFitRms,
               final int iterations, final boolean converged, final double[][] covariance,
               final double varianceFactor, final boolean weighted, final Vector3D[] residuals) {
            this.params = params;
            this.preFitRms = preFitRms;
            this.postFitRms = postFitRms;
            this.iterations = iterations;
            this.converged = converged;
            this.covariance = covariance;
            this.varianceFactor = varianceFactor;
            this.weighted = weighted;
            this.residuals = residuals;
        }
    }

    private final PropagatorFactory factory;
    private final List<PositionObservation> obs;
    private final double[] scale;     // finite-difference step & column scaling per parameter
    private final AbsoluteDate epoch;
    private final double obsStep;
    /** Sample index of each observation on the {@code obsStep} grid. */
    private final int[] gridIndex;
    /** Whitening matrices W_i = L_i^-1 (C_i = L_i L_i^T), or null for unit weighting. */
    private final double[][][] whiten;

    public BatchLeastSquares(final PropagatorFactory factory, final List<PositionObservation> obs,
                             final double[] scale) {
        if (obs.size() < 2) {
            throw new IllegalArgumentException("need at least two observations");
        }
        this.factory = factory;
        this.obs = obs;
        this.scale = scale.clone();
        this.epoch = obs.get(0).getDate();
        // the observations may have gaps, but must lie on a common grid (e.g. SPP epochs)
        double step = Double.POSITIVE_INFINITY;
        for (int i = 1; i < obs.size(); i++) {
            final double d = obs.get(i).getDate().durationFrom(obs.get(i - 1).getDate());
            if (d <= 0.0) {
                throw new IllegalArgumentException("observations must be strictly time-ordered");
            }
            step = Math.min(step, d);
        }
        this.obsStep = step;
        this.gridIndex = new int[obs.size()];
        for (int i = 0; i < obs.size(); i++) {
            final double k = obs.get(i).getDate().durationFrom(epoch) / step;
            final long r = Math.round(k);
            if (Math.abs(k - r) > 1.0e-6) {
                throw new IllegalArgumentException("observation " + i + " is not on the common "
                        + step + " s grid");
            }
            gridIndex[i] = (int) r;
        }
        boolean anyCov = false;
        boolean allCov = true;
        for (final PositionObservation o : obs) {
            final boolean has = o.getCovariance() != null;
            anyCov |= has;
            allCov &= has;
        }
        if (anyCov && !allCov) {
            throw new IllegalArgumentException("either all or none of the observations must carry covariance");
        }
        if (allCov) {
            whiten = new double[obs.size()][][];
            for (int i = 0; i < obs.size(); i++) {
                whiten[i] = whitening(obs.get(i).getCovariance());
            }
        } else {
            whiten = null;
        }
    }

    /** Inverse of the lower Cholesky factor of a 3x3 SPD matrix. */
    static double[][] whitening(final double[][] c) {
        final double l00 = Math.sqrt(c[0][0]);
        final double l10 = c[1][0] / l00;
        final double l20 = c[2][0] / l00;
        final double l11 = Math.sqrt(c[1][1] - l10 * l10);
        final double l21 = (c[2][1] - l20 * l10) / l11;
        final double l22 = Math.sqrt(c[2][2] - l20 * l20 - l21 * l21);
        if (!(l00 > 0) || !(l11 > 0) || !(l22 > 0)) {
            throw new IllegalArgumentException("observation covariance is not positive definite");
        }
        // invert lower-triangular L
        final double i00 = 1.0 / l00;
        final double i11 = 1.0 / l11;
        final double i22 = 1.0 / l22;
        final double i10 = -l10 * i00 * i11;
        final double i21 = -l21 * i11 * i22;
        final double i20 = -(l20 * i00 + l21 * i10) * i22;
        return new double[][] {{i00, 0, 0}, {i10, i11, 0}, {i20, i21, i22}};
    }

    /** Whitened 3-vector W_i d (identity when unweighted). */
    private double[] w(final int i, final Vector3D d) {
        if (whiten == null) {
            return new double[] {d.getX(), d.getY(), d.getZ()};
        }
        final double[][] m = whiten[i];
        return new double[] {
            m[0][0] * d.getX(),
            m[1][0] * d.getX() + m[1][1] * d.getY(),
            m[2][0] * d.getX() + m[2][1] * d.getY() + m[2][2] * d.getZ(),
        };
    }

    /** Weighted cost per observation, sqrt(sum |W r|^2 / m); equals the position RMS when unweighted. */
    private double cost(final Vector3D[] comp) {
        if (whiten == null) {
            return rms(comp);
        }
        double s = 0.0;
        for (int i = 0; i < obs.size(); i++) {
            final double[] r = w(i, obs.get(i).getPosition().subtract(comp[i]));
            s += r[0] * r[0] + r[1] * r[1] + r[2] * r[2];
        }
        return Math.sqrt(s / obs.size());
    }

    public Result estimate(final double[] guess, final int maxIterations) {
        double[] params = guess.clone();
        final int p = params.length;
        final int m = obs.size();

        Vector3D[] comp = computed(params);
        double rms = cost(comp);
        final double preRms = rms(comp);
        boolean converged = false;
        int iter = 0;

        for (; iter < maxIterations; iter++) {
            // residuals r = observed - computed
            final double[] r = new double[3 * m];
            for (int i = 0; i < m; i++) {
                final double[] d = w(i, obs.get(i).getPosition().subtract(comp[i]));
                r[3 * i] = d[0];
                r[3 * i + 1] = d[1];
                r[3 * i + 2] = d[2];
            }

            // scaled design matrix columns J~_j = (d comp / d p_j) * scale_j
            final double[][] jt = new double[p][3 * m]; // transposed: jt[j] is column j
            for (int j = 0; j < p; j++) {
                final double[] pert = params.clone();
                pert[j] += scale[j];
                final Vector3D[] cj = computed(pert);
                for (int i = 0; i < m; i++) {
                    final double[] dd = w(i, cj[i].subtract(comp[i])); // * (1/scale) * scale = identity scaling
                    jt[j][3 * i] = dd[0];
                    jt[j][3 * i + 1] = dd[1];
                    jt[j][3 * i + 2] = dd[2];
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
                final double trialRms = cost(trialComp);
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
        return new Result(params, preRms, rms(comp), iter, converged, null, Double.NaN,
                whiten != null, residuals(comp));
    }

    private Vector3D[] residuals(final Vector3D[] comp) {
        final Vector3D[] out = new Vector3D[obs.size()];
        for (int i = 0; i < obs.size(); i++) {
            out[i] = obs.get(i).getPosition().subtract(comp[i]);
        }
        return out;
    }

    /**
     * Estimate, then evaluate the formal parameter covariance at the solution (one extra
     * Jacobian, i.e. p+1 propagations).
     */
    public Result estimateWithCovariance(final double[] guess, final int maxIterations) {
        final Result r = estimate(guess, maxIterations);
        final int p = r.params.length;
        final int m = obs.size();
        final Vector3D[] comp = computed(r.params);
        final double[][] jt = new double[p][3 * m];
        for (int j = 0; j < p; j++) {
            final double[] pert = r.params.clone();
            pert[j] += scale[j];
            final Vector3D[] cj = computed(pert);
            for (int i = 0; i < m; i++) {
                final double[] dd = w(i, cj[i].subtract(comp[i]));
                jt[j][3 * i] = dd[0];
                jt[j][3 * i + 1] = dd[1];
                jt[j][3 * i + 2] = dd[2];
            }
        }
        final double[][] n = new double[p][p];
        for (int a = 0; a < p; a++) {
            for (int b = a; b < p; b++) {
                double sum = 0.0;
                for (int k = 0; k < 3 * m; k++) {
                    sum += jt[a][k] * jt[b][k];
                }
                n[a][b] = sum;
                n[b][a] = sum;
            }
        }
        double wssr = 0.0;
        for (int i = 0; i < m; i++) {
            final double[] d = w(i, obs.get(i).getPosition().subtract(comp[i]));
            wssr += d[0] * d[0] + d[1] * d[1] + d[2] * d[2];
        }
        final int dof = Math.max(1, 3 * m - p);
        final double vf = wssr / dof;
        double[][] cov;
        try {
            cov = org.ursc.trajectory.math.LinearSolver.invert(n);
        } catch (final IllegalArgumentException singular) {
            cov = null;
        }
        if (cov != null) {
            final double f = whiten == null ? vf : 1.0;
            for (int a = 0; a < p; a++) {
                for (int b = 0; b < p; b++) {
                    cov[a][b] *= f * scale[a] * scale[b];
                }
            }
        }
        return new Result(r.params, r.preFitRms, r.postFitRms, r.iterations, r.converged, cov, vf,
                whiten != null, r.residuals);
    }

    private Vector3D[] computed(final double[] params) {
        final SampledPropagator prop = factory.build(params);
        final EphemerisCollector col = new EphemerisCollector();
        prop.setStepHandler(obsStep, col);
        final int last = gridIndex[gridIndex.length - 1];
        prop.propagate(epoch.shiftedBy(last * obsStep));
        final List<SpacecraftState> states = col.getStates();
        final int m = obs.size();
        if (states.size() <= last) {
            throw new IllegalStateException("propagator produced "
                    + states.size() + " samples, expected " + (last + 1));
        }
        final Vector3D[] pos = new Vector3D[m];
        for (int i = 0; i < m; i++) {
            pos[i] = states.get(gridIndex[i]).getPosition();
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
