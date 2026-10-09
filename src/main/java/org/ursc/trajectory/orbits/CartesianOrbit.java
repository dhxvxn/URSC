package org.ursc.trajectory.orbits;

import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Orbit expressed directly as position/velocity. This is the representation used
 * internally by the numerical propagator; the others are derived views.
 */
public final class CartesianOrbit extends Orbit {

    private final PVCoordinates pv;

    public CartesianOrbit(final PVCoordinates pv, final Frame frame,
                          final AbsoluteDate date, final double mu) {
        super(frame, date, mu);
        this.pv = pv;
    }

    /** Copy-convert any orbit into Cartesian form. */
    public CartesianOrbit(final Orbit orbit) {
        super(orbit.getFrame(), orbit.getDate(), orbit.getMu());
        this.pv = orbit.getPVCoordinates();
    }

    @Override
    public OrbitType getType() {
        return OrbitType.CARTESIAN;
    }

    @Override
    public PVCoordinates getPVCoordinates() {
        return pv;
    }

    @Override
    public double getA() {
        final double r = pv.getPosition().getNorm();
        final double v2 = pv.getVelocity().getNormSq();
        return 1.0 / (2.0 / r - v2 / getMu());
    }

    @Override
    public double getE() {
        final Vector3D position = pv.getPosition();
        final Vector3D velocity = pv.getVelocity();
        final double r = position.getNorm();
        final double v2 = velocity.getNormSq();
        final double rv = position.dotProduct(velocity);
        final double mu = getMu();
        final Vector3D eVec = position.scalarMultiply(v2 - mu / r)
                .subtract(velocity.scalarMultiply(rv))
                .scalarMultiply(1.0 / mu);
        return eVec.getNorm();
    }

    @Override
    public double getI() {
        final Vector3D h = pv.getMomentum();
        return Vector3D.angle(h, Vector3D.PLUS_K);
    }

    @Override
    public String toString() {
        return "CartesianOrbit{" + pv + ", frame=" + getFrame() + ", date=" + getDate() + "}";
    }
}
