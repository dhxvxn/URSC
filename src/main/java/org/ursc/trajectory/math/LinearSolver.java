package org.ursc.trajectory.math;

/**
 * Small dense linear-algebra helpers: solve {@code A x = b} and invert a square
 * matrix, both by Gaussian elimination with partial pivoting.
 *
 * <p>The library deliberately avoids a third-party linear-algebra dependency, and
 * the systems that arise in estimation (normal equations, covariance) are tiny
 * (a handful of unknowns), so a direct dense solver is both sufficient and easy to
 * audit. Mirrors the elimination already used inside
 * {@link org.ursc.trajectory.estimation.BatchLeastSquares}, exposed here for reuse
 * by the GNSS point-positioning solver and any future estimator.</p>
 */
public final class LinearSolver {

    private LinearSolver() {
    }

    /**
     * Solve the {@code n x n} system {@code A x = b}.
     *
     * @param aIn coefficient matrix (not modified)
     * @param bIn right-hand side (not modified)
     * @return the solution vector {@code x}
     * @throws IllegalArgumentException if the matrix is singular to working precision
     */
    public static double[] solve(final double[][] aIn, final double[] bIn) {
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
            if (a[piv][col] == 0.0) {
                throw new IllegalArgumentException("singular matrix at column " + col);
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

    /**
     * Invert an {@code n x n} matrix (Gauss-Jordan elimination with partial pivoting).
     *
     * @param aIn the matrix to invert (not modified)
     * @return a newly allocated {@code n x n} inverse
     * @throws IllegalArgumentException if the matrix is singular to working precision
     */
    public static double[][] invert(final double[][] aIn) {
        final int n = aIn.length;
        final double[][] a = new double[n][n];
        final double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++) {
            a[i] = aIn[i].clone();
            inv[i][i] = 1.0;
        }
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) {
                    piv = r;
                }
            }
            if (a[piv][col] == 0.0) {
                throw new IllegalArgumentException("singular matrix at column " + col);
            }
            double[] tmp = a[col];
            a[col] = a[piv];
            a[piv] = tmp;
            tmp = inv[col];
            inv[col] = inv[piv];
            inv[piv] = tmp;

            final double d = a[col][col];
            for (int c = 0; c < n; c++) {
                a[col][c] /= d;
                inv[col][c] /= d;
            }
            for (int r = 0; r < n; r++) {
                if (r == col) {
                    continue;
                }
                final double f = a[r][col];
                if (f == 0.0) {
                    continue;
                }
                for (int c = 0; c < n; c++) {
                    a[r][c] -= f * a[col][c];
                    inv[r][c] -= f * inv[col][c];
                }
            }
        }
        return inv;
    }
}
