package org.ursc.trajectory.frames;

import org.ursc.trajectory.math.RotationMatrix;
import org.ursc.trajectory.math.Vector3D;

/**
 * A kinematic transform between two frames at a fixed instant: a rotation plus
 * the angular velocity of the destination frame relative to the source
 * (expressed in the destination frame), so that velocities are transformed
 * with the Coriolis term included.
 */
public final class Transform {

    private final RotationMatrix rotation;
    private final Vector3D rotationRate;

    public Transform(final RotationMatrix rotation, final Vector3D rotationRate) {
        this.rotation = rotation;
        this.rotationRate = rotationRate;
    }

    /** Transform a position (or any non-rotating vector such as an acceleration). */
    public Vector3D transformPosition(final Vector3D p) {
        return rotation.applyTo(p);
    }

    /** Transform a free vector by rotation only (e.g. an acceleration). */
    public Vector3D transformVector(final Vector3D v) {
        return rotation.applyTo(v);
    }

    /**
     * Transform a velocity given the already-transformed destination position,
     * including the {@code -omega x r} rotating-frame term.
     */
    public Vector3D transformVelocity(final Vector3D velocitySource, final Vector3D positionDest) {
        final Vector3D rotated = rotation.applyTo(velocitySource);
        return rotated.subtract(rotationRate.crossProduct(positionDest));
    }

    public RotationMatrix getRotation() {
        return rotation;
    }

    /** @return angular velocity of the destination frame w.r.t. the source, in destination axes. */
    public Vector3D getRotationRate() {
        return rotationRate;
    }

    /** @return the inverse transform. */
    public Transform getInverse() {
        final RotationMatrix inv = rotation.transpose();
        // rotation rate of source w.r.t. destination, expressed in source axes
        final Vector3D invRate = inv.applyTo(rotationRate).negate();
        return new Transform(inv, invRate);
    }
}
