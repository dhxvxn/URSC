package org.ursc.trajectory.bodies;

import org.ursc.trajectory.math.Vector3D;

/**
 * An oblate ellipsoid of revolution modelling the Earth's surface, used to
 * obtain geodetic coordinates and altitude from an Earth-fixed position.
 */
public final class OneAxisEllipsoid {

    private final double a;   // equatorial radius
    private final double f;   // flattening
    private final double e2;  // first eccentricity squared

    public OneAxisEllipsoid(final double equatorialRadius, final double flattening) {
        this.a = equatorialRadius;
        this.f = flattening;
        this.e2 = f * (2.0 - f);
    }

    public double getEquatorialRadius() {
        return a;
    }

    public double getFlattening() {
        return f;
    }

    /**
     * Convert an Earth-fixed Cartesian position into geodetic coordinates using
     * Bowring's closed-form method (sub-millimetre accuracy at LEO altitudes).
     *
     * @param pointBodyFixed position expressed in the Earth-fixed frame (m)
     * @return the geodetic latitude, longitude and altitude
     */
    public GeodeticPoint transform(final Vector3D pointBodyFixed) {
        final double x = pointBodyFixed.getX();
        final double y = pointBodyFixed.getY();
        final double z = pointBodyFixed.getZ();

        final double longitude = Math.atan2(y, x);

        final double p = Math.sqrt(x * x + y * y);
        final double b = a * (1.0 - f);
        final double ep2 = (a * a - b * b) / (b * b); // second eccentricity squared

        if (p < 1.0e-9) {
            // on the polar axis
            final double latitude = Math.copySign(Math.PI / 2.0, z);
            final double altitude = Math.abs(z) - b;
            return new GeodeticPoint(latitude, longitude, altitude);
        }

        final double theta = Math.atan2(z * a, p * b);
        final double sinTheta = Math.sin(theta);
        final double cosTheta = Math.cos(theta);

        final double latitude = Math.atan2(
                z + ep2 * b * sinTheta * sinTheta * sinTheta,
                p - e2 * a * cosTheta * cosTheta * cosTheta);

        final double sinLat = Math.sin(latitude);
        final double n = a / Math.sqrt(1.0 - e2 * sinLat * sinLat);
        final double altitude = p / Math.cos(latitude) - n;

        return new GeodeticPoint(latitude, longitude, altitude);
    }

    /**
     * @param pointBodyFixed position in the Earth-fixed frame (m)
     * @return altitude above the ellipsoid (m)
     */
    public double getAltitude(final Vector3D pointBodyFixed) {
        return transform(pointBodyFixed).getAltitude();
    }
}
