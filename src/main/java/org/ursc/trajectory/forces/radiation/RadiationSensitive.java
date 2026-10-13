package org.ursc.trajectory.forces.radiation;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Spacecraft properties relevant to solar radiation pressure. Implement this for
 * box-and-wing models; {@link IsotropicRadiationSingleCoefficient} provides the
 * simple fixed-area model with a single reflection coefficient.
 */
public interface RadiationSensitive {

    /** @return the radiation pressure coefficient Cr (1 = black body, 2 = fully reflective). */
    double getReflectionCoefficient();

    /** @return the cross-sectional area exposed to the Sun (m^2). */
    double getCrossSection();

    /**
     * Projected area (m^2) exposed to the Sun in the current state. The default is the
     * direction-independent constant {@link #getCrossSection()}; attitude/box-wing models
     * override this, using the state (for the nominal body attitude) and the Sun unit vector.
     *
     * @param state current spacecraft state (inertial frame)
     * @param sunDirection unit vector from the spacecraft toward the Sun
     * @return the projected area exposed to the Sun (m^2)
     */
    default double getCrossSection(final SpacecraftState state, final Vector3D sunDirection) {
        return getCrossSection();
    }
}
