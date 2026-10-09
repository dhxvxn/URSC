package org.ursc.trajectory.forces;

import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * General-relativistic Schwarzschild correction to the central attraction
 * (Montenbruck &amp; Gill eq 3.146):
 * <pre>
 *   a = (mu / (c^2 r^3)) * [ (4 mu/r - v^2) r + 4 (r.v) v ]
 * </pre>
 * A small but secular perturbation (it rotates the line of apsides); included for
 * completeness in long-arc and precise applications.
 */
public final class Relativity implements ForceModel {

    private final double mu;

    public Relativity(final double mu) {
        this.mu = mu;
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final Vector3D r = state.getPosition();
        final Vector3D v = state.getVelocity();
        final double rNorm = r.getNorm();
        final double c2 = Constants.SPEED_OF_LIGHT * Constants.SPEED_OF_LIGHT;

        final double rv = r.dotProduct(v);
        final double v2 = v.getNormSq();
        final double common = mu / (c2 * rNorm * rNorm * rNorm);

        final Vector3D term1 = r.scalarMultiply(4.0 * mu / rNorm - v2);
        final Vector3D term2 = v.scalarMultiply(4.0 * rv);
        return term1.add(term2).scalarMultiply(common);
    }

    @Override
    public String getName() {
        return "Relativity";
    }
}
