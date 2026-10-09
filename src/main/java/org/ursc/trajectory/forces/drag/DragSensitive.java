package org.ursc.trajectory.forces.drag;

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
}
