package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * An atmospheric density model. Implementations provide the local density given
 * an Earth-fixed position; new models (NRLMSISE-00, Jacchia-Bowman, DTM, ...) are
 * added by implementing this interface.
 */
public interface Atmosphere {

    /**
     * @param date            the instant
     * @param positionBodyFixed satellite position in the Earth-fixed frame (m)
     * @return local atmospheric density (kg/m^3)
     */
    double getDensity(AbsoluteDate date, Vector3D positionBodyFixed);

    /** @return a short name for the model. */
    String getName();
}
