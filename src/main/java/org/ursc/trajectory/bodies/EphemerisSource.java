package org.ursc.trajectory.bodies;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * A source of high-precision geocentric Sun and Moon positions, used to replace the
 * low-precision analytic series in {@link CelestialBodyFactory} when a JPL ephemeris file is
 * available. Positions are returned in GCRF (ICRF, which differs by {@literal <}0.01&Prime;),
 * in metres.
 */
public interface EphemerisSource {

    /** @return geocentric Sun position at {@code date} (m, GCRF). */
    Vector3D sunGeocentric(AbsoluteDate date);

    /** @return geocentric Moon position at {@code date} (m, GCRF). */
    Vector3D moonGeocentric(AbsoluteDate date);
}
