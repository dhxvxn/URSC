package org.ursc.trajectory.forces;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Point-mass central attraction: {@code a = -mu * r / |r|^3}.
 *
 * <p>Use this when only the two-body term is wanted. When a
 * {@link org.ursc.trajectory.forces.gravity.SphericalHarmonicGravity} model is
 * used instead, it already includes the central term, so this model should be
 * left out to avoid double counting.</p>
 */
public final class NewtonianAttraction implements ForceModel {

    private final double mu;

    public NewtonianAttraction(final double mu) {
        this.mu = mu;
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final Vector3D r = state.getPosition();
        final double rNorm = r.getNorm();
        final double factor = -mu / (rNorm * rNorm * rNorm);
        return r.scalarMultiply(factor);
    }

    @Override
    public String getName() {
        return "NewtonianAttraction";
    }
}
