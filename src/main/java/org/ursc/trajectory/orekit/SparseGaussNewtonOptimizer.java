package org.ursc.trajectory.orekit;

import org.hipparchus.exception.MathIllegalStateException;
import org.hipparchus.linear.Array2DRowRealMatrix;
import org.hipparchus.linear.ArrayRealVector;
import org.hipparchus.linear.CholeskyDecomposition;
import org.hipparchus.linear.DecompositionSolver;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.linear.RealVector;
import org.hipparchus.optim.ConvergenceChecker;
import org.hipparchus.optim.LocalizedOptimFormats;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LeastSquaresOptimizer;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LeastSquaresProblem;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LeastSquaresProblem.Evaluation;
import org.hipparchus.util.Incrementor;

/**
 * Gauss-Newton optimizer for Orekit's {@code BatchLSEstimator} that forms the normal equations
 * from the non-zero entries of each Jacobian row. In carrier-phase orbit determination every
 * measurement depends on the six orbit parameters, one receiver clock and at most one ambiguity,
 * so the rows are almost empty while the parameter count is in the hundreds; the dense
 * {@code J^T J} product of the generic optimizer would dominate the run time.
 * Same algorithm and convergence test as Hipparchus' Gauss-Newton optimizer; the covariance
 * reported by the optimum is {@code (J^T J)^-1} from the same normal matrix.
 */
public final class SparseGaussNewtonOptimizer implements LeastSquaresOptimizer {

    @Override
    public Optimum optimize(final LeastSquaresProblem lsp) {
        final Incrementor evaluations = lsp.getEvaluationCounter();
        final Incrementor iterations = lsp.getIterationCounter();
        final ConvergenceChecker<Evaluation> checker = lsp.getConvergenceChecker();
        if (checker == null) {
            throw new NullPointerException("convergence checker");
        }
        RealVector point = lsp.getStart();
        Evaluation current = null;
        while (true) {
            iterations.increment();
            final Evaluation previous = current;
            evaluations.increment();
            current = lsp.evaluate(point);
            final RealVector residuals = current.getResiduals();
            final RealMatrix jacobian = current.getJacobian();
            point = current.getPoint();
            final Normal ne = normal(jacobian, residuals);
            if (previous != null && checker.converged(iterations.getCount(), previous, current)) {
                return new SparseOptimum(current, ne, evaluations.getCount(), iterations.getCount());
            }
            final RealVector dx = ne.solver().solve(new ArrayRealVector(ne.g, false));
            point = point.add(dx);
        }
    }

    /** Normal matrix N = J^T J and gradient g = J^T r from the non-zero entries of J. */
    private static final class Normal {
        final double[][] n;
        final double[] g;
        private DecompositionSolver solver;

        Normal(final int p) {
            n = new double[p][p];
            g = new double[p];
        }

        DecompositionSolver solver() {
            if (solver == null) {
                try {
                    solver = new CholeskyDecomposition(new Array2DRowRealMatrix(n, false), 1.0e-12, 1.0e-14).getSolver();
                } catch (final RuntimeException e) {
                    throw new MathIllegalStateException(LocalizedOptimFormats.UNABLE_TO_SOLVE_SINGULAR_PROBLEM, 1.0e-14);
                }
            }
            return solver;
        }
    }

    private static Normal normal(final RealMatrix j, final RealVector r) {
        final int m = j.getRowDimension();
        final int p = j.getColumnDimension();
        final Normal ne = new Normal(p);
        final int[] idx = new int[p];
        final double[] val = new double[p];
        for (int i = 0; i < m; i++) {
            final double[] row = j.getRow(i);
            int k = 0;
            for (int c = 0; c < p; c++) {
                if (row[c] != 0.0) {
                    idx[k] = c;
                    val[k++] = row[c];
                }
            }
            final double ri = r.getEntry(i);
            for (int a = 0; a < k; a++) {
                final int ia = idx[a];
                final double va = val[a];
                ne.g[ia] += va * ri;
                final double[] na = ne.n[ia];
                for (int b = 0; b < k; b++) {
                    na[idx[b]] += va * val[b];
                }
            }
        }
        return ne;
    }

    /** Optimum whose covariance comes from the sparse normal matrix. */
    private static final class SparseOptimum implements Optimum {
        private final Evaluation e;
        private final Normal ne;
        private final int evaluations;
        private final int iterations;

        SparseOptimum(final Evaluation e, final Normal ne, final int evaluations, final int iterations) {
            this.e = e;
            this.ne = ne;
            this.evaluations = evaluations;
            this.iterations = iterations;
        }

        @Override
        public int getEvaluations() {
            return evaluations;
        }

        @Override
        public int getIterations() {
            return iterations;
        }

        @Override
        public RealMatrix getCovariances(final double threshold) {
            return ne.solver().getInverse();
        }

        @Override
        public RealVector getSigma(final double covarianceSingularityThreshold) {
            final RealMatrix c = getCovariances(covarianceSingularityThreshold);
            final RealVector s = new ArrayRealVector(c.getRowDimension());
            for (int i = 0; i < s.getDimension(); i++) {
                s.setEntry(i, Math.sqrt(c.getEntry(i, i)));
            }
            return s;
        }

        @Override
        public double getRMS() {
            return e.getRMS();
        }

        @Override
        public RealMatrix getJacobian() {
            return e.getJacobian();
        }

        @Override
        public double getCost() {
            return e.getCost();
        }

        @Override
        public double getChiSquare() {
            return e.getChiSquare();
        }

        @Override
        public double getReducedChiSquare(final int n) {
            return e.getReducedChiSquare(n);
        }

        @Override
        public RealVector getResiduals() {
            return e.getResiduals();
        }

        @Override
        public RealVector getPoint() {
            return e.getPoint();
        }
    }
}
