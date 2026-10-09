package org.ursc.trajectory.forces;

import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * General-relativistic corrections to the central attraction (IERS-2010 eq 10.12,
 * with PPN parameters beta = gamma = 1).
 *
 * <p>Always includes the Schwarzschild term (Montenbruck &amp; Gill eq 3.146):
 * <pre>
 *   a_S = (mu / (c^2 r^3)) * [ (4 mu/r - v^2) r + 4 (r.v) v ]
 * </pre>
 * Optionally adds the Lense-Thirring (frame-dragging) term from the Earth's
 * rotation:
 * <pre>
 *   a_LT = (2 mu / (c^2 r^3)) * [ (3/r^2) (r.J) (r x v) + (v x J) ]
 * </pre>
 * where {@code J} is the Earth's angular momentum per unit mass. All are tiny for
 * LEO (Schwarzschild rotates the apsides; Lense-Thirring is ~1e-9 m/s^2), included
 * for completeness and precise long-arc work. The de Sitter (geodesic precession)
 * term is omitted: it requires the Earth's heliocentric velocity and is
 * sub-mm/s^2 for LEO.</p>
 */
public final class Relativity implements ForceModel {

    /** Earth angular momentum per unit mass about the pole (m^2/s), IERS value. */
    private static final double EARTH_ANGULAR_MOMENTUM = 9.8e8;

    private final double mu;
    private final boolean lenseThirring;

    public Relativity(final double mu) {
        this(mu, false);
    }

    public Relativity(final double mu, final boolean lenseThirring) {
        this.mu = mu;
        this.lenseThirring = lenseThirring;
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final Vector3D r = state.getPosition();
        final Vector3D v = state.getVelocity();
        final double rNorm = r.getNorm();
        final double c2 = Constants.SPEED_OF_LIGHT * Constants.SPEED_OF_LIGHT;
        final double common = mu / (c2 * rNorm * rNorm * rNorm);

        final double rv = r.dotProduct(v);
        final double v2 = v.getNormSq();
        final Vector3D schwarzschild = r.scalarMultiply(4.0 * mu / rNorm - v2)
                .add(v.scalarMultiply(4.0 * rv))
                .scalarMultiply(common);

        if (!lenseThirring) {
            return schwarzschild;
        }

        // Earth angular momentum vector along the (inertial) pole
        final Vector3D j = new Vector3D(0.0, 0.0, EARTH_ANGULAR_MOMENTUM);
        final Vector3D rCrossV = r.crossProduct(v);
        final Vector3D lt = rCrossV.scalarMultiply(3.0 / (rNorm * rNorm) * r.dotProduct(j))
                .add(v.crossProduct(j))
                .scalarMultiply(2.0 * common);
        return schwarzschild.add(lt);
    }

    @Override
    public String getName() {
        return lenseThirring ? "Relativity(Schwarzschild+LenseThirring)" : "Relativity(Schwarzschild)";
    }
}
