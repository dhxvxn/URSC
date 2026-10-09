package org.ursc.trajectory.forces.gravity;

import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Earth gravitational attraction from a spherical-harmonic expansion, including
 * the central (point-mass) term. The acceleration is evaluated in the Earth-fixed
 * frame &mdash; where the field is static &mdash; and rotated back to the inertial
 * frame.
 *
 * <p>Implements the Cunningham/Gottlieb recursion for the normalised-free
 * potential derivatives exactly as given by Montenbruck &amp; Gill,
 * <i>Satellite Orbits</i> (section 3.2.4, eqs 3.29-3.33). The expansion can be
 * truncated independently in degree and order, so a user can ask for e.g. a
 * zonal-only J2-J6 field (order 0) or a full tesseral field.</p>
 */
public final class SphericalHarmonicGravity implements ForceModel {

    private final GravityField field;
    private final int degree;
    private final int order;
    private final Frame bodyFrame;

    /**
     * @param field  the gravity field coefficients
     * @param degree maximum degree to use (&le; field max degree)
     * @param order  maximum order to use (&le; degree and field max order)
     */
    public SphericalHarmonicGravity(final GravityField field, final int degree, final int order) {
        if (degree > field.getMaxDegree() || order > field.getMaxOrder() || order > degree) {
            throw new IllegalArgumentException(
                    "requested degree/order (" + degree + "/" + order + ") out of range for field "
                            + field.getMaxDegree() + "/" + field.getMaxOrder());
        }
        this.field = field;
        this.degree = degree;
        this.order = order;
        this.bodyFrame = FramesFactory.getITRF();
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final Transform inertialToBody =
                FramesFactory.getTransform(state.getFrame(), bodyFrame, state.getDate());
        final Vector3D rBody = inertialToBody.transformPosition(state.getPosition());

        final Vector3D accBody = computeBodyFrameAcceleration(rBody);

        // rotate the acceleration back to the inertial frame
        return inertialToBody.getRotation().applyInverseTo(accBody);
    }

    private Vector3D computeBodyFrameAcceleration(final Vector3D rBody) {
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

        // seed
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

    @Override
    public String getName() {
        return "SphericalHarmonicGravity(" + degree + "x" + order + ")";
    }
}
