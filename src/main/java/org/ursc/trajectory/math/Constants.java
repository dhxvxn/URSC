package org.ursc.trajectory.math;

/**
 * Physical and astronomical constants used throughout the library.
 *
 * <p>Values follow the IERS 2010 conventions / WGS84 / EGM96 where applicable.
 * Units are SI (metres, seconds, kilograms, radians) unless noted.</p>
 */
public final class Constants {

    private Constants() {
    }

    /** Speed of light in vacuum (m/s). */
    public static final double SPEED_OF_LIGHT = 299792458.0;

    /** Astronomical unit (m). */
    public static final double ASTRONOMICAL_UNIT = 1.495978707e11;

    /** Earth gravitational constant GM (m^3/s^2), EGM96. */
    public static final double EARTH_MU = 3.986004418e14;

    /** Earth equatorial radius (m), EGM96 reference radius. */
    public static final double EARTH_EQUATORIAL_RADIUS = 6378136.3;

    /** Earth flattening (WGS84). */
    public static final double EARTH_FLATTENING = 1.0 / 298.257223563;

    /** Earth mean rotation rate (rad/s), IERS. */
    public static final double EARTH_ROTATION_RATE = 7.292115e-5;

    /** Sun gravitational constant GM (m^3/s^2). */
    public static final double SUN_MU = 1.32712440018e20;

    /** Moon gravitational constant GM (m^3/s^2). */
    public static final double MOON_MU = 4.9028e12;

    /** Solar radiation pressure at 1 AU (N/m^2). */
    public static final double SOLAR_RADIATION_PRESSURE = 4.56e-6;

    /** Number of seconds in a Julian day. */
    public static final double JULIAN_DAY = 86400.0;

    /** Number of days in a Julian century. */
    public static final double JULIAN_CENTURY_DAYS = 36525.0;

    /** Obliquity of the ecliptic at J2000 (radians). */
    public static final double OBLIQUITY_J2000 = Math.toRadians(23.43929111);

    public static final double TWO_PI = 2.0 * Math.PI;

    /** Normalise an angle into the interval [0, 2*pi). */
    public static double normalizeAngleZeroTwoPi(final double angle) {
        double a = angle % TWO_PI;
        if (a < 0) {
            a += TWO_PI;
        }
        return a;
    }

    /** Normalise an angle into the interval centred on {@code center}. */
    public static double normalizeAngle(final double angle, final double center) {
        return angle - TWO_PI * Math.floor((angle + Math.PI - center) / TWO_PI);
    }
}
