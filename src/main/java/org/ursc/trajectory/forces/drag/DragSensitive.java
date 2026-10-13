package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Spacecraft properties relevant to atmospheric drag. Implement this to model
 * attitude- or geometry-dependent cross sections; {@link IsotropicDrag} provides
 * the simple fixed-area cannonball model.
 */
public interface DragSensitive {

    /** @return the dimensionless drag coefficient Cd. */
    double getDragCoefficient();

    /** @return the drag cross-sectional area (m^2). */
    double getCrossSection();

    /**
     * Projected drag area (m^2) in the current state. The default is the direction-independent
     * constant {@link #getCrossSection()}; attitude/box-wing models override this, using the
     * state (for the nominal body attitude and Sun direction) and the ram unit vector.
     *
     * @param state current spacecraft state (inertial frame)
     * @param ramDirection unit vector of the velocity relative to the co-rotating atmosphere
     * @return the projected drag area (m^2)
     */
    default double getCrossSection(final SpacecraftState state, final Vector3D ramDirection) {
        return getCrossSection();
    }
}
