package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.forces.BoxWingGeometry;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Attitude-dependent drag geometry: a rectangular body box plus a Sun-tracking solar array, in the
 * nominal nadir-pointing yaw-steered attitude (see {@link BoxWingGeometry}). The drag coefficient
 * Cd remains a scalar (still fitted exactly as for the cannonball); only the projected area becomes
 * attitude-dependent via the ram direction.
 */
public final class BoxWingDrag implements DragSensitive {

    private final double cd;
    private final double faceAreaX;
    private final double faceAreaY;
    private final double faceAreaZ;
    private final double wingArea;
    private final CelestialBody sun;

    /**
     * @param cd drag coefficient
     * @param bodyX body dimension along x (m)
     * @param bodyY body dimension along y (m)
     * @param bodyZ body dimension along z (m)
     * @param wingArea solar-array area (m^2)
     * @param sun the Sun (for the array orientation)
     */
    public BoxWingDrag(final double cd, final double bodyX, final double bodyY, final double bodyZ,
                       final double wingArea, final CelestialBody sun) {
        this.cd = cd;
        this.faceAreaX = bodyY * bodyZ;
        this.faceAreaY = bodyX * bodyZ;
        this.faceAreaZ = bodyX * bodyY;
        this.wingArea = wingArea;
        this.sun = sun;
    }

    @Override
    public double getDragCoefficient() {
        return cd;
    }

    /** Mean of the body face areas plus the wing — a representative constant fallback. */
    @Override
    public double getCrossSection() {
        return (faceAreaX + faceAreaY + faceAreaZ) / 3.0 + wingArea;
    }

    @Override
    public double getCrossSection(final SpacecraftState state, final Vector3D ramDirection) {
        return BoxWingGeometry.projectedArea(state, ramDirection, sun,
                faceAreaX, faceAreaY, faceAreaZ, wingArea);
    }
}
