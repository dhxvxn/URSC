package org.ursc.trajectory.orbits;

import org.ursc.trajectory.math.Vector3D;

/** Position/velocity pair in a frame (metres, metres per second). */
public final class PVCoordinates {

    private final Vector3D position;
    private final Vector3D velocity;

    public PVCoordinates(final Vector3D position, final Vector3D velocity) {
        this.position = position;
        this.velocity = velocity;
    }

    public Vector3D getPosition() {
        return position;
    }

    public Vector3D getVelocity() {
        return velocity;
    }

    /** @return the orbital angular momentum vector r x v. */
    public Vector3D getMomentum() {
        return position.crossProduct(velocity);
    }

    @Override
    public String toString() {
        return "PV{r=" + position + ", v=" + velocity + "}";
    }
}
