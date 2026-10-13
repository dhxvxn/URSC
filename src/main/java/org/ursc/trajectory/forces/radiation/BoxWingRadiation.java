package org.ursc.trajectory.forces.radiation;

import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.forces.BoxWingGeometry;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Attitude-dependent solar-radiation-pressure geometry: a rectangular body box plus a Sun-tracking
 * solar array, in the nominal nadir-pointing yaw-steered attitude (see {@link BoxWingGeometry}).
 * The reflection coefficient Cr remains a scalar (fitted as for the cannonball); only the projected
 * area becomes attitude-dependent. Since the array tracks the Sun, it always presents its full area
 * to the Sun; the body faces contribute according to their tilt.
 */
public final class BoxWingRadiation implements RadiationSensitive {

    private final double cr;
    private final double faceAreaX;
    private final double faceAreaY;
    private final double faceAreaZ;
    private final double wingArea;
    private final CelestialBody sun;

    public BoxWingRadiation(final double cr, final double bodyX, final double bodyY, final double bodyZ,
                            final double wingArea, final CelestialBody sun) {
        this.cr = cr;
        this.faceAreaX = bodyY * bodyZ;
        this.faceAreaY = bodyX * bodyZ;
        this.faceAreaZ = bodyX * bodyY;
        this.wingArea = wingArea;
        this.sun = sun;
    }

    @Override
    public double getReflectionCoefficient() {
        return cr;
    }

    @Override
    public double getCrossSection() {
        return (faceAreaX + faceAreaY + faceAreaZ) / 3.0 + wingArea;
    }

    @Override
    public double getCrossSection(final SpacecraftState state, final Vector3D sunDirection) {
        return BoxWingGeometry.projectedArea(state, sunDirection, sun,
                faceAreaX, faceAreaY, faceAreaZ, wingArea);
    }
}
