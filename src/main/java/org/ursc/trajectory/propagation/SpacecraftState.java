package org.ursc.trajectory.propagation;

import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.Orbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Full state of the spacecraft at an instant: its orbit (position/velocity in a
 * pseudo-inertial frame) and its mass. Force models receive this object and may
 * query the state in whichever frame they need.
 */
public final class SpacecraftState {

    private final Orbit orbit;
    private final double mass;

    public SpacecraftState(final Orbit orbit, final double mass) {
        this.orbit = orbit;
        this.mass = mass;
    }

    public SpacecraftState(final Orbit orbit) {
        this(orbit, Double.NaN);
    }

    public Orbit getOrbit() {
        return orbit;
    }

    public AbsoluteDate getDate() {
        return orbit.getDate();
    }

    public Frame getFrame() {
        return orbit.getFrame();
    }

    public double getMu() {
        return orbit.getMu();
    }

    public double getMass() {
        return mass;
    }

    /** @return position/velocity in the orbit's (inertial) frame. */
    public PVCoordinates getPVCoordinates() {
        return orbit.getPVCoordinates();
    }

    public Vector3D getPosition() {
        return orbit.getPosition();
    }

    public Vector3D getVelocity() {
        return orbit.getVelocity();
    }

    /** @return position/velocity expressed in the requested frame. */
    public PVCoordinates getPVCoordinates(final Frame outputFrame) {
        if (outputFrame == getFrame()) {
            return getPVCoordinates();
        }
        final Transform t = FramesFactory.getTransform(getFrame(), outputFrame, getDate());
        final PVCoordinates pv = getPVCoordinates();
        final Vector3D p = t.transformPosition(pv.getPosition());
        final Vector3D v = t.transformVelocity(pv.getVelocity(), p);
        return new PVCoordinates(p, v);
    }

    /** @return position expressed in the requested frame. */
    public Vector3D getPosition(final Frame outputFrame) {
        if (outputFrame == getFrame()) {
            return getPosition();
        }
        return FramesFactory.getTransform(getFrame(), outputFrame, getDate())
                .transformPosition(getPosition());
    }
}
