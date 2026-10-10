package org.ursc.trajectory.estimation;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/** A position observation (inertial GCRF, metres) at a given epoch. */
public final class PositionObservation {

    private final AbsoluteDate date;
    private final Vector3D position;

    public PositionObservation(final AbsoluteDate date, final Vector3D position) {
        this.date = date;
        this.position = position;
    }

    public AbsoluteDate getDate() {
        return date;
    }

    public Vector3D getPosition() {
        return position;
    }
}
