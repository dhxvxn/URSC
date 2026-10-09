package org.ursc.trajectory.forces.gravity;

/**
 * Container for an Earth gravity field: its reference GM and radius together with
 * the <em>un-normalised</em> spherical-harmonic coefficients C(n,m) and S(n,m).
 *
 * <p>Coefficient arrays are indexed {@code [n][m]} with {@code 0 <= m <= n <=
 * maxDegree}. C(0,0) is 1 (the point-mass term). Normalised coefficients read
 * from a field file are de-normalised on load by
 * {@link GravityFieldFactory}.</p>
 */
public final class GravityField {

    private final double mu;
    private final double referenceRadius;
    private final int maxDegree;
    private final int maxOrder;
    private final double[][] c;
    private final double[][] s;

    public GravityField(final double mu, final double referenceRadius,
                        final int maxDegree, final int maxOrder,
                        final double[][] c, final double[][] s) {
        this.mu = mu;
        this.referenceRadius = referenceRadius;
        this.maxDegree = maxDegree;
        this.maxOrder = maxOrder;
        this.c = c;
        this.s = s;
    }

    public double getMu() {
        return mu;
    }

    public double getReferenceRadius() {
        return referenceRadius;
    }

    public int getMaxDegree() {
        return maxDegree;
    }

    public int getMaxOrder() {
        return maxOrder;
    }

    public double getC(final int n, final int m) {
        return c[n][m];
    }

    public double getS(final int n, final int m) {
        return s[n][m];
    }
}
