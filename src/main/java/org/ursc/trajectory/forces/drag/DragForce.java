package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Atmospheric drag acceleration:
 * <pre>
 *   a = -0.5 * (Cd * A / m) * rho * |v_rel| * v_rel
 * </pre>
 * where {@code v_rel} is the velocity of the spacecraft relative to the
 * co-rotating atmosphere, {@code v_rel = v - omega x r}. The density comes from a
 * pluggable {@link Atmosphere} model and the ballistic properties from a
 * {@link DragSensitive}. This is the dominant non-gravitational perturbation for
 * LEO and the main driver of orbital decay.
 */
public final class DragForce implements ForceModel {

    private static final Vector3D EARTH_OMEGA =
            new Vector3D(0.0, 0.0, Constants.EARTH_ROTATION_RATE);

    private final Atmosphere atmosphere;
    private final DragSensitive spacecraft;

    public DragForce(final Atmosphere atmosphere, final DragSensitive spacecraft) {
        this.atmosphere = atmosphere;
        this.spacecraft = spacecraft;
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final Vector3D rInertial = state.getPosition();
        final Vector3D vInertial = state.getVelocity();

        // atmosphere co-rotates with the Earth: relative velocity in the inertial frame
        final Vector3D vRel = vInertial.subtract(EARTH_OMEGA.crossProduct(rInertial));
        final double vRelNorm = vRel.getNorm();

        final Vector3D rBodyFixed = FramesFactory
                .getTransform(state.getFrame(), FramesFactory.getITRF(), state.getDate())
                .transformPosition(rInertial);
        final double rho = atmosphere.getDensity(state.getDate(), rBodyFixed);

        final double ballistic = spacecraft.getDragCoefficient() * spacecraft.getCrossSection()
                / state.getMass();
        final double factor = -0.5 * ballistic * rho * vRelNorm;
        return vRel.scalarMultiply(factor);
    }

    @Override
    public String getName() {
        return "DragForce(" + atmosphere.getName() + ")";
    }
}
