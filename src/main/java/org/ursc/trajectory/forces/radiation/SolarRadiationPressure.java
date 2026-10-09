package org.ursc.trajectory.forces.radiation;

import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Solar radiation pressure with a conical Earth-shadow model (umbra and
 * penumbra):
 * <pre>
 *   a = nu * Cr * (A/m) * P0 * (AU / d)^2 * u_sun_to_sat
 * </pre>
 * where {@code P0} is the solar pressure at 1 AU, {@code d} the Sun-satellite
 * distance, {@code u_sun_to_sat} the unit vector pointing away from the Sun, and
 * {@code nu in [0,1]} the fraction of the solar disk visible from the spacecraft.
 */
public final class SolarRadiationPressure implements ForceModel {

    private static final double SUN_RADIUS = 6.957e8;         // m
    private static final double EARTH_RADIUS = Constants.EARTH_EQUATORIAL_RADIUS;

    private final CelestialBody sun;
    private final RadiationSensitive spacecraft;

    public SolarRadiationPressure(final CelestialBody sun, final RadiationSensitive spacecraft) {
        this.sun = sun;
        this.spacecraft = spacecraft;
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final Vector3D r = state.getPosition();
        final Vector3D sunPos = sun.getPosition(state.getDate(), state.getFrame());
        final Vector3D satToSun = sunPos.subtract(r);
        final double d = satToSun.getNorm();

        final double lighting = lightingRatio(r, satToSun, d);
        if (lighting == 0.0) {
            return Vector3D.ZERO;
        }

        final double pressure = Constants.SOLAR_RADIATION_PRESSURE
                * (Constants.ASTRONOMICAL_UNIT / d) * (Constants.ASTRONOMICAL_UNIT / d);
        final double acc = lighting * spacecraft.getReflectionCoefficient()
                * spacecraft.getCrossSection() / state.getMass() * pressure;

        // force pushes the spacecraft away from the Sun
        final Vector3D sunToSatUnit = satToSun.scalarMultiply(-1.0 / d);
        return sunToSatUnit.scalarMultiply(acc);
    }

    /**
     * Fraction of the solar disk visible from the spacecraft (1 = full Sun,
     * 0 = total eclipse), using apparent angular radii of the Sun and Earth.
     */
    private double lightingRatio(final Vector3D r, final Vector3D satToSun, final double d) {
        // angular radius of the Sun as seen from the satellite
        final double aSun = Math.asin(Math.min(1.0, SUN_RADIUS / d));
        // angular radius of the Earth (occulting body) as seen from the satellite
        final double rNorm = r.getNorm();
        if (rNorm <= EARTH_RADIUS) {
            return 1.0;
        }
        final double aEarth = Math.asin(Math.min(1.0, EARTH_RADIUS / rNorm));
        // apparent separation between the Sun centre and the Earth centre
        final Vector3D satToEarth = r.negate();
        final double sep = Vector3D.angle(satToSun, satToEarth);

        if (sep >= aSun + aEarth) {
            return 1.0;                          // no occultation
        }
        if (sep <= aEarth - aSun) {
            return 0.0;                          // total eclipse (umbra)
        }
        if (sep <= aSun - aEarth) {
            // Earth entirely inside the solar disk: annular eclipse
            return 1.0 - (aEarth * aEarth) / (aSun * aSun);
        }
        // partial occultation: area of the lens-shaped overlap of two disks
        final double a2 = aSun * aSun;
        final double b2 = aEarth * aEarth;
        final double c1 = (sep * sep + a2 - b2) / (2.0 * sep);
        final double c2 = (sep * sep + b2 - a2) / (2.0 * sep);
        final double area = a2 * Math.acos(clamp(c1 / aSun))
                + b2 * Math.acos(clamp(c2 / aEarth))
                - sep * Math.sqrt(Math.max(0.0, a2 - c1 * c1));
        final double occulted = area / (Math.PI * a2);
        return Math.max(0.0, Math.min(1.0, 1.0 - occulted));
    }

    private static double clamp(final double x) {
        return Math.max(-1.0, Math.min(1.0, x));
    }

    @Override
    public String getName() {
        return "SolarRadiationPressure";
    }
}
