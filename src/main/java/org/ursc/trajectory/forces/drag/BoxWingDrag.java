package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.attitude.AttitudeProvider;
import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.forces.BoxWingGeometry;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Attitude-dependent drag geometry for the Sentinel-3 CPOD 8-plate macro-model: a body box with
 * three independent face areas plus a Sun-tracking canted solar array (see {@link BoxWingGeometry}).
 * The body orientation is supplied by an {@link AttitudeProvider}. The drag coefficient Cd remains a
 * scalar (still fitted exactly as for the cannonball); only the projected area, along the ram
 * direction, becomes attitude-dependent.
 */
public final class BoxWingDrag implements DragSensitive {

    private final double cd;
    private final double areaX;
    private final double areaY;
    private final double areaZ;
    private final double wingArea;
    private final double cantRad;
    private final AttitudeProvider attitude;
    private final CelestialBody sun;

    /**
     * @param cd       drag coefficient
     * @param areaX    body face area normal to {@code Xsat} (m^2)
     * @param areaY    body face area normal to {@code Ysat} (m^2)
     * @param areaZ    body face area normal to {@code Zsat} (m^2)
     * @param wingArea solar-array area (m^2)
     * @param cantRad  array cant from the rotation plane (rad)
     * @param attitude body-orientation source
     * @param sun      the Sun (for the array orientation)
     */
    public BoxWingDrag(final double cd, final double areaX, final double areaY, final double areaZ,
                       final double wingArea, final double cantRad,
                       final AttitudeProvider attitude, final CelestialBody sun) {
        this.cd = cd;
        this.areaX = areaX;
        this.areaY = areaY;
        this.areaZ = areaZ;
        this.wingArea = wingArea;
        this.cantRad = cantRad;
        this.attitude = attitude;
        this.sun = sun;
    }

    @Override
    public double getDragCoefficient() {
        return cd;
    }

    /** Mean of the body face areas plus the wing — a representative constant fallback. */
    @Override
    public double getCrossSection() {
        return (areaX + areaY + areaZ) / 3.0 + wingArea;
    }

    @Override
    public double getCrossSection(final SpacecraftState state, final Vector3D ramDirection) {
        return BoxWingGeometry.projectedArea(state, ramDirection, sun, attitude,
                areaX, areaY, areaZ, wingArea, cantRad);
    }
}
