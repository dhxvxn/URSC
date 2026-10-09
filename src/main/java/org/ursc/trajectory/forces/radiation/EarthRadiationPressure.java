package org.ursc.trajectory.forces.radiation;

import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Radiation pressure from the Earth: sunlight reflected off the surface (albedo)
 * plus the planet's thermal infrared emission. Both push outward on the
 * spacecraft and, for LEO, contribute of order 10-35% of direct solar radiation
 * pressure.
 *
 * <p>Uses the Knocke-Ries (1988) second-degree zonal albedo/emissivity model,
 * integrated numerically over the visible Earth cap discretised into a
 * latitude/longitude grid of surface elements. Each element reflects (Lambertian)
 * the sunlight it receives and emits its IR share; the irradiance reaching the
 * spacecraft is summed and converted to acceleration via the reflection
 * coefficient and area-to-mass ratio.</p>
 */
public final class EarthRadiationPressure implements ForceModel {

    // Knocke-Ries zonal coefficients (seasonal P1 term omitted)
    private static final double ALBEDO_0 = 0.34;
    private static final double ALBEDO_2 = 0.29;
    private static final double EMISS_0 = 0.68;
    private static final double EMISS_2 = -0.18;

    private final CelestialBody sun;
    private final RadiationSensitive spacecraft;
    private final int nLat;
    private final int nLon;
    private final double earthRadius;
    private final double solarFlux1Au; // W/m^2 at 1 AU

    public EarthRadiationPressure(final CelestialBody sun, final RadiationSensitive spacecraft,
                                  final int nLat, final int nLon) {
        this.sun = sun;
        this.spacecraft = spacecraft;
        this.nLat = nLat;
        this.nLon = nLon;
        this.earthRadius = Constants.EARTH_EQUATORIAL_RADIUS;
        this.solarFlux1Au = Constants.SOLAR_RADIATION_PRESSURE * Constants.SPEED_OF_LIGHT;
    }

    /** Default: built-in Sun, a 10x20 surface grid. */
    public EarthRadiationPressure(final RadiationSensitive spacecraft) {
        this(CelestialBodyFactory.getSun(), spacecraft, 10, 20);
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final Transform toBody =
                FramesFactory.getTransform(state.getFrame(), FramesFactory.getITRF(), state.getDate());
        final Vector3D rSat = toBody.transformPosition(state.getPosition());
        final Vector3D rSun = toBody.transformPosition(sun.getPosition(state.getDate(), state.getFrame()));

        final double sunDist = rSun.getNorm();
        final double flux = solarFlux1Au
                * (Constants.ASTRONOMICAL_UNIT / sunDist) * (Constants.ASTRONOMICAL_UNIT / sunDist);
        final Vector3D sunHat = rSun.scalarMultiply(1.0 / sunDist);

        final double dLat = Math.PI / nLat;
        final double dLon = 2.0 * Math.PI / nLon;

        Vector3D sum = Vector3D.ZERO;
        for (int i = 0; i < nLat; i++) {
            final double phi = -Math.PI / 2.0 + (i + 0.5) * dLat; // element centre latitude
            final double cosPhi = Math.cos(phi);
            final double sinPhi = Math.sin(phi);
            final double dA = earthRadius * earthRadius * cosPhi * dLat * dLon;
            final double p2 = 0.5 * (3.0 * sinPhi * sinPhi - 1.0);
            final double albedo = ALBEDO_0 + ALBEDO_2 * p2;
            final double emiss = EMISS_0 + EMISS_2 * p2;
            for (int j = 0; j < nLon; j++) {
                final double lon = (j + 0.5) * dLon;
                final Vector3D nHat = new Vector3D(cosPhi * Math.cos(lon), cosPhi * Math.sin(lon), sinPhi);
                final Vector3D rElem = nHat.scalarMultiply(earthRadius);

                final Vector3D toSat = rSat.subtract(rElem);
                final double d = toSat.getNorm();
                final Vector3D u = toSat.scalarMultiply(1.0 / d);
                final double cosSat = u.dotProduct(nHat);
                if (cosSat <= 0) {
                    continue; // element not visible from the spacecraft
                }

                final double cosSun = Math.max(0.0, sunHat.dotProduct(nHat));
                final double exitance = albedo * flux * cosSun + emiss * flux / 4.0;
                // Lambertian irradiance reaching the spacecraft from this element
                final double e = (exitance / Math.PI) * cosSat * dA / (d * d);
                sum = sum.add(e, u);
            }
        }

        final double factor = spacecraft.getReflectionCoefficient() * spacecraft.getCrossSection()
                / (state.getMass() * Constants.SPEED_OF_LIGHT);
        final Vector3D accBody = sum.scalarMultiply(factor);
        return toBody.getRotation().applyInverseTo(accBody);
    }

    @Override
    public String getName() {
        return "EarthRadiationPressure(" + nLat + "x" + nLon + ")";
    }
}
