package org.ursc.trajectory.estimation;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/** A position observation (inertial GCRF, metres) at a given epoch, with optional covariance. */
public final class PositionObservation {

    private final AbsoluteDate date;
    private final Vector3D position;
    private final double[][] covariance;

    public PositionObservation(final AbsoluteDate date, final Vector3D position) {
        this(date, position, null);
    }

    /**
     * @param covariance 3x3 position covariance (m^2) in the same frame as {@code position},
     *                   or {@code null} for unit weighting
     */
    public PositionObservation(final AbsoluteDate date, final Vector3D position, final double[][] covariance) {
        this.date = date;
        this.position = position;
        this.covariance = covariance == null ? null
                : new double[][] {covariance[0].clone(), covariance[1].clone(), covariance[2].clone()};
    }

    public AbsoluteDate getDate() {
        return date;
    }

    public Vector3D getPosition() {
        return position;
    }

    /** @return the 3x3 covariance (m^2), or {@code null} if unweighted. */
    public double[][] getCovariance() {
        return covariance == null ? null
                : new double[][] {covariance[0].clone(), covariance[1].clone(), covariance[2].clone()};
    }
}
