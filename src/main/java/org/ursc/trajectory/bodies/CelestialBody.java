package org.ursc.trajectory.bodies;

import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/** A celestial body providing its position and gravitational parameter. */
public interface CelestialBody {

    /** @return the body name. */
    String getName();

    /** @return the gravitational parameter GM (m^3/s^2). */
    double getMu();

    /**
     * Geocentric-equivalent position of the body at a date, expressed in the
     * requested frame.
     *
     * @param date  the instant
     * @param frame the output frame (GCRF supported)
     * @return position vector (m)
     */
    Vector3D getPosition(AbsoluteDate date, Frame frame);
}
