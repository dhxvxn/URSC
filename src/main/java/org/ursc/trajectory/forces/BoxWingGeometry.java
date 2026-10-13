package org.ursc.trajectory.forces;

import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Attitude-dependent projected area for a simple box + Sun-tracking single-wing spacecraft
 * (e.g. Sentinel-3), used by both the drag and the solar-radiation-pressure "box-wing" models.
 *
 * <p>The body is a rectangular box whose three face areas are given along the body axes; the
 * attitude is the nominal <b>nadir-pointing, yaw-steered</b> frame derived from the orbit
 * (z = nadir, y = −orbit-normal, x ≈ velocity). The solar array is a flat plate of fixed area that
 * tracks the Sun, so its normal is the Sun direction. The projected area along a flow direction
 * {@code f} (unit vector) is</p>
 * <pre>
 *   A(f) = |f·x|·Ax + |f·y|·Ay + |f·z|·Az  +  Awing·|f·sun|
 * </pre>
 * <p>For SRP the flow direction <em>is</em> the Sun direction, so the wing contributes its full
 * area; for drag it is the ram direction, so the wing contributes according to its tilt to the
 * flow. This is a nominal-attitude model (no attitude telemetry): it captures the geometry, making
 * the fitted drag/SRP coefficient physical, but it is not a substitute for measured attitude.</p>
 */
public final class BoxWingGeometry {

    private BoxWingGeometry() {
    }

    /**
     * Projected area (m^2) of the box + Sun-tracking wing along a flow direction.
     *
     * @param state current state (inertial frame), for the nominal attitude and Sun direction
     * @param flowDir unit flow direction in the inertial frame (ram for drag, Sun for SRP)
     * @param sun the Sun (for the array orientation)
     * @param faceAreaX body face area normal to the body x-axis (m^2)
     * @param faceAreaY body face area normal to the body y-axis (m^2)
     * @param faceAreaZ body face area normal to the body z-axis (m^2)
     * @param wingArea solar-array area (m^2)
     * @return the projected area (m^2)
     */
    public static double projectedArea(final SpacecraftState state, final Vector3D flowDir,
                                       final CelestialBody sun,
                                       final double faceAreaX, final double faceAreaY,
                                       final double faceAreaZ, final double wingArea) {
        final Vector3D r = state.getPosition();
        final Vector3D v = state.getVelocity();

        // nominal nadir-pointing, yaw-steered body frame
        final Vector3D ur = r.normalize();
        final Vector3D h = r.crossProduct(v).normalize();   // orbit normal
        final Vector3D zB = ur.negate();                    // nadir
        final Vector3D yB = h.negate();                     // -orbit-normal
        final Vector3D xB = yB.crossProduct(zB);            // ~velocity (roll)

        double area = Math.abs(flowDir.dotProduct(xB)) * faceAreaX
                    + Math.abs(flowDir.dotProduct(yB)) * faceAreaY
                    + Math.abs(flowDir.dotProduct(zB)) * faceAreaZ;

        // Sun-tracking array: normal points at the Sun
        final Vector3D sunDir = sun.getPosition(state.getDate(), state.getFrame())
                .subtract(r).normalize();
        area += wingArea * Math.abs(flowDir.dotProduct(sunDir));

        return area;
    }
}
