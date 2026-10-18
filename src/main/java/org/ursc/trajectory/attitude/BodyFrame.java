package org.ursc.trajectory.attitude;

import org.ursc.trajectory.math.Vector3D;

/**
 * The three spacecraft body axes (unit vectors), expressed in the inertial frame the
 * {@link AttitudeProvider} was queried in. By convention {@code x}, {@code y}, {@code z}
 * are the satellite {@code Xsat}, {@code Ysat}, {@code Zsat} axes respectively; for
 * Sentinel-3 these are related to the orbit as {@code +Z} nadir and {@code -X} ram-facing
 * (see {@link NominalYawSteering}).
 */
public final class BodyFrame {

    private final Vector3D x;
    private final Vector3D y;
    private final Vector3D z;

    public BodyFrame(final Vector3D x, final Vector3D y, final Vector3D z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** @return the body {@code Xsat} axis (unit vector, inertial frame). */
    public Vector3D x() {
        return x;
    }

    /** @return the body {@code Ysat} axis (unit vector, inertial frame). */
    public Vector3D y() {
        return y;
    }

    /** @return the body {@code Zsat} axis (unit vector, inertial frame). */
    public Vector3D z() {
        return z;
    }
}
