package org.ursc.trajectory.bodies;

/** A point on or above the Earth ellipsoid: geodetic latitude, longitude, altitude. */
public final class GeodeticPoint {

    private final double latitude;   // rad
    private final double longitude;  // rad
    private final double altitude;   // m above the ellipsoid

    public GeodeticPoint(final double latitude, final double longitude, final double altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    @Override
    public String toString() {
        return String.format("GeodeticPoint{lat=%.4f deg, lon=%.4f deg, alt=%.1f m}",
                Math.toDegrees(latitude), Math.toDegrees(longitude), altitude);
    }
}
