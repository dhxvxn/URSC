package org.ursc.trajectory.orbits;

import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Abstract base for an orbital state at a given instant, in a given
 * pseudo-inertial frame, under a central attraction coefficient {@code mu}.
 *
 * <p>Concrete subclasses store different parameter sets but all expose the same
 * read-only orbital quantities and can emit {@link PVCoordinates}. Conversions
 * between parameter sets go through Cartesian coordinates.</p>
 */
public abstract class Orbit {

    private final Frame frame;
    private final AbsoluteDate date;
    private final double mu;

    protected Orbit(final Frame frame, final AbsoluteDate date, final double mu) {
        if (!frame.isPseudoInertial()) {
            throw new IllegalArgumentException(
                    "orbit must be defined in a pseudo-inertial frame, got " + frame);
        }
        this.frame = frame;
        this.date = date;
        this.mu = mu;
    }

    public Frame getFrame() {
        return frame;
    }

    public AbsoluteDate getDate() {
        return date;
    }

    public double getMu() {
        return mu;
    }

    public abstract OrbitType getType();

    /** @return position/velocity in the orbit's frame. */
    public abstract PVCoordinates getPVCoordinates();

    public Vector3D getPosition() {
        return getPVCoordinates().getPosition();
    }

    public Vector3D getVelocity() {
        return getPVCoordinates().getVelocity();
    }

    /** @return semi-major axis (m). */
    public abstract double getA();

    /** @return eccentricity. */
    public abstract double getE();

    /** @return inclination (rad). */
    public abstract double getI();

    /** @return orbital period (s) for elliptic orbits. */
    public double getKeplerianPeriod() {
        final double a = getA();
        return 2.0 * Math.PI * Math.sqrt(a * a * a / mu);
    }

    /** @return mean motion (rad/s). */
    public double getKeplerianMeanMotion() {
        final double a = getA();
        return Math.sqrt(mu / (a * a * a));
    }
}
