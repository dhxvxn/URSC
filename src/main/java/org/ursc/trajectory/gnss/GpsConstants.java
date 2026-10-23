package org.ursc.trajectory.gnss;

/**
 * GPS Interface Control Document (IS-GPS-200) physical constants, used by the
 * GNSS point-positioning and ephemeris code.
 *
 * <p>These are the WGS-84 / GPS-ICD values, which differ very slightly from the
 * EGM96 values in {@link org.ursc.trajectory.math.Constants}. Broadcast-ephemeris
 * satellite positions must be computed with the ICD values to be self-consistent,
 * so they are grouped here rather than reused from the propagation constants. The
 * speed of light is identical to the SI value.</p>
 */
public final class GpsConstants {

    private GpsConstants() {
    }

    /** Speed of light in vacuum (m/s); identical to the SI definition. */
    public static final double SPEED_OF_LIGHT = 299792458.0;

    /** GPS L1 carrier frequency (Hz). */
    public static final double F_L1 = 1575.42e6;

    /** GPS L2 carrier frequency (Hz). */
    public static final double F_L2 = 1227.60e6;

    /** WGS-84 Earth gravitational constant used for GPS orbits (m^3/s^2). */
    public static final double GM = 3.986005e14;

    /** WGS-84 / GPS Earth rotation rate (rad/s). */
    public static final double EARTH_ROTATION_RATE = 7.2921151467e-5;

    /**
     * Relativistic clock-correction constant {@code F = -2*sqrt(GM)/c^2}
     * (s/sqrt(m)); multiplies {@code e*sqrt(A)*sin(E)} to give the eccentricity
     * term of the satellite-clock relativistic correction.
     */
    public static final double RELATIVISTIC_F = -4.442807633e-10;
}
