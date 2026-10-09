package org.ursc.trajectory.forces.gravity;

import org.ursc.trajectory.math.Vector3D;

/**
 * Body-frame gradient of a spherical-harmonic geopotential, shared by the main
 * gravity model and the tide models (which pass their &Delta;C/&Delta;S
 * coefficients with C(0,0)=0 so only the perturbation is evaluated).
 *
 * <p>Implements the Cunningham/Gottlieb recursion for the un-normalised potential
 * derivatives exactly as given by Montenbruck &amp; Gill, <i>Satellite Orbits</i>
 * (section 3.2.4, eqs 3.29-3.33).</p>
 */
public final class GravitationalGradient {

    private GravitationalGradient() {
    }

    /**
     * Acceleration in the body-fixed frame for a position expressed in that frame.
     *
     * @param field   coefficient container (provides C/S, GM and reference radius)
     * @param degree  maximum degree to evaluate
     * @param order   maximum order to evaluate
     * @param rBody   position in the body-fixed frame (m)
     * @return acceleration in the body-fixed frame (m/s^2)
     */
    public static Vector3D acceleration(final GravityField field, final int degree,
                                        final int order, final Vector3D rBody) {
        final double x = rBody.getX();
        final double y = rBody.getY();
        final double z = rBody.getZ();
        final double r = rBody.getNorm();
        final double rSq = r * r;
        final double bigR = field.getReferenceRadius();
        final double factor = bigR / rSq;

        final int nmax = degree + 1; // need derivatives up to degree+1
        final double[][] v = new double[nmax + 1][nmax + 1];
        final double[][] w = new double[nmax + 1][nmax + 1];

        v[0][0] = bigR / r;
        w[0][0] = 0.0;

        for (int m = 0; m <= nmax; m++) {
            if (m > 0) {
                final double coef = 2 * m - 1;
                v[m][m] = coef * (x * factor * v[m - 1][m - 1] - y * factor * w[m - 1][m - 1]);
                w[m][m] = coef * (x * factor * w[m - 1][m - 1] + y * factor * v[m - 1][m - 1]);
            }
            for (int n = m + 1; n <= nmax; n++) {
                final double a1 = (2.0 * n - 1.0) / (n - m);
                final double vPrev2 = (n - 2 >= m) ? v[n - 2][m] : 0.0;
                final double wPrev2 = (n - 2 >= m) ? w[n - 2][m] : 0.0;
                final double a2 = (n + m - 1.0) / (n - m);
                v[n][m] = a1 * z * factor * v[n - 1][m] - a2 * bigR * factor * vPrev2;
                w[n][m] = a1 * z * factor * w[n - 1][m] - a2 * bigR * factor * wPrev2;
            }
        }

        double ax = 0.0;
        double ay = 0.0;
        double az = 0.0;
        for (int n = 0; n <= degree; n++) {
            final int mTop = Math.min(n, order);
            for (int m = 0; m <= mTop; m++) {
                final double cnm = field.getC(n, m);
                final double snm = field.getS(n, m);
                if (m == 0) {
                    ax += -cnm * v[n + 1][1];
                    ay += -cnm * w[n + 1][1];
                } else {
                    final double f1 = 0.5;
                    final double f2 = 0.5 * (n - m + 2) * (n - m + 1);
                    ax += f1 * (-cnm * v[n + 1][m + 1] - snm * w[n + 1][m + 1])
                            + f2 * (cnm * v[n + 1][m - 1] + snm * w[n + 1][m - 1]);
                    ay += f1 * (-cnm * w[n + 1][m + 1] + snm * v[n + 1][m + 1])
                            + f2 * (-cnm * w[n + 1][m - 1] + snm * v[n + 1][m - 1]);
                }
                az += (n - m + 1) * (-cnm * v[n + 1][m] - snm * w[n + 1][m]);
            }
        }

        final double scale = field.getMu() / (bigR * bigR);
        return new Vector3D(scale * ax, scale * ay, scale * az);
    }
}
