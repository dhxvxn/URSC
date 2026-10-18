package org.ursc.trajectory.forces.radiation;

import org.ursc.trajectory.attitude.AttitudeProvider;
import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.forces.BoxWingGeometry;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Attitude-dependent solar-radiation-pressure geometry for the Sentinel-3 CPOD 8-plate macro-model:
 * a body box with three independent face areas plus a Sun-tracking canted solar array (see
 * {@link BoxWingGeometry}). The body orientation is supplied by an {@link AttitudeProvider}. The
 * reflection coefficient Cr remains a scalar (fitted as for the cannonball); only the projected
 * area, along the Sun direction, becomes attitude-dependent.
 */
public final class BoxWingRadiation implements RadiationSensitive {

    private final double cr;
    private final double areaX;
    private final double areaY;
    private final double areaZ;
    private final double wingArea;
    private final double cantRad;
    private final AttitudeProvider attitude;
    private final CelestialBody sun;

    public BoxWingRadiation(final double cr, final double areaX, final double areaY, final double areaZ,
                            final double wingArea, final double cantRad,
                            final AttitudeProvider attitude, final CelestialBody sun) {
        this.cr = cr;
        this.areaX = areaX;
        this.areaY = areaY;
        this.areaZ = areaZ;
        this.wingArea = wingArea;
        this.cantRad = cantRad;
        this.attitude = attitude;
        this.sun = sun;
    }

    @Override
    public double getReflectionCoefficient() {
        return cr;
    }

    @Override
    public double getCrossSection() {
        return (areaX + areaY + areaZ) / 3.0 + wingArea;
    }

    @Override
    public double getCrossSection(final SpacecraftState state, final Vector3D sunDirection) {
        return BoxWingGeometry.projectedArea(state, sunDirection, sun, attitude,
                areaX, areaY, areaZ, wingArea, cantRad);
    }
}
