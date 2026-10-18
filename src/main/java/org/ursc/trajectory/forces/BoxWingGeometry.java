package org.ursc.trajectory.forces;

import org.ursc.trajectory.attitude.AttitudeProvider;
import org.ursc.trajectory.attitude.BodyFrame;
import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Attitude-dependent projected area for the Sentinel-3 CPOD 8-plate macro-model
 * (GMV-CPOD-TN-0027): a body box with three independent face areas plus a Sun-tracking
 * solar array. The body orientation comes from an {@link AttitudeProvider} (the nominal
 * yaw-steered law, or measured AUX_PROQUA quaternions), so the plates are oriented by the
 * real attitude rather than an internally-assumed one.
 *
 * <p>The three body face areas are given directly (not derived from box dimensions), because
 * the CPOD model specifies them independently: bus &plusmn;X = 2.84, &plusmn;Y = 5.41,
 * &plusmn;Z = 6.10 m&sup2;. The solar array is a flat plate of area {@code wingArea} (10.50 m&sup2;)
 * that rotates about the body {@code Y} axis to track the Sun, canted by {@code cantRad} from the
 * rotation plane. The projected area along a flow direction {@code f} (unit vector) is</p>
 * <pre>
 *   A(f) = |f·X|·Ax + |f·Y|·Ay + |f·Z|·Az  +  Awing·|f·n_array|
 * </pre>
 * <p>where {@code n_array} is the array normal (the Sun direction projected perpendicular to the
 * array rotation axis and tilted by the cant toward the Sun). For SRP the flow direction is the
 * Sun direction; for drag it is the ram direction. The scalar Cd/Cr still apply and are still
 * fitted; only the area becomes attitude-dependent.</p>
 */
public final class BoxWingGeometry {

    private BoxWingGeometry() {
    }

    /**
     * Projected area (m&sup2;) of the box + Sun-tracking array along a flow direction, using the
     * attitude from {@code attitude} and a Sun-tracking canted array.
     *
     * @param state    current state (its frame is the inertial frame of {@code flowDir})
     * @param flowDir  unit flow direction in the inertial frame (ram for drag, Sun for SRP)
     * @param sun      the Sun (for the array orientation)
     * @param attitude the body-orientation source
     * @param areaX    body face area normal to {@code Xsat} (m&sup2;)
     * @param areaY    body face area normal to {@code Ysat} (m&sup2;)
     * @param areaZ    body face area normal to {@code Zsat} (m&sup2;)
     * @param wingArea solar-array area (m&sup2;)
     * @param cantRad  array cant from the rotation plane (rad)
     * @return the projected area (m&sup2;)
     */
    public static double projectedArea(final SpacecraftState state, final Vector3D flowDir,
                                       final CelestialBody sun, final AttitudeProvider attitude,
                                       final double areaX, final double areaY, final double areaZ,
                                       final double wingArea, final double cantRad) {
        final BodyFrame body = attitude.attitude(state);
        final Vector3D sunDir = sun.getPosition(state.getDate(), state.getFrame())
                .subtract(state.getPosition()).normalize();
        final Vector3D arrayNormal = arrayNormal(body, sunDir, cantRad);
        return projectedArea(flowDir, body, arrayNormal, areaX, areaY, areaZ, wingArea);
    }

    /**
     * Core summation along a flow direction given an explicit body frame and array normal.
     * Exposed for validation against the CPOD deployed projected-area table.
     *
     * @param flowDir     unit flow direction (inertial)
     * @param body        body axes (inertial)
     * @param arrayNormal solar-array normal (inertial), or {@code null} to omit the array
     */
    public static double projectedArea(final Vector3D flowDir, final BodyFrame body,
                                       final Vector3D arrayNormal, final double areaX,
                                       final double areaY, final double areaZ,
                                       final double wingArea) {
        double area = Math.abs(flowDir.dotProduct(body.x())) * areaX
                    + Math.abs(flowDir.dotProduct(body.y())) * areaY
                    + Math.abs(flowDir.dotProduct(body.z())) * areaZ;
        if (arrayNormal != null) {
            area += wingArea * Math.abs(flowDir.dotProduct(arrayNormal));
        }
        return area;
    }

    /**
     * The solar-array normal: the Sun direction projected perpendicular to the array rotation axis
     * (body {@code Y}) and tilted by the cant toward the Sun hemisphere. Degenerates gracefully to
     * the body {@code Z} axis when the Sun is along the rotation axis.
     */
    public static Vector3D arrayNormal(final BodyFrame body, final Vector3D sunDir,
                                       final double cantRad) {
        final Vector3D yB = body.y();
        final double along = sunDir.dotProduct(yB);
        Vector3D inPlane = sunDir.subtract(yB.scalarMultiply(along));
        if (inPlane.getNorm() < 1.0e-9) {
            inPlane = body.z();                         // Sun along the rotation axis
        } else {
            inPlane = inPlane.normalize();
        }
        final double tilt = (along >= 0 ? 1.0 : -1.0) * Math.sin(cantRad);
        return inPlane.scalarMultiply(Math.cos(cantRad)).add(yB.scalarMultiply(tilt)).normalize();
    }
}
