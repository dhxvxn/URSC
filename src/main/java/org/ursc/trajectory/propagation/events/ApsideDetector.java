package org.ursc.trajectory.propagation.events;

import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Detects perigee and apogee passages. The switching function is the radial
 * velocity {@code r.v}, which is zero at both apsides (increasing through zero at
 * perigee, decreasing at apogee).
 */
public final class ApsideDetector implements EventDetector {

    private final Action action;

    public ApsideDetector(final Action action) {
        this.action = action;
    }

    public ApsideDetector() {
        this(Action.CONTINUE);
    }

    @Override
    public double g(final SpacecraftState state) {
        final PVCoordinates pv = state.getPVCoordinates();
        return pv.getPosition().dotProduct(pv.getVelocity());
    }

    @Override
    public Action eventOccurred(final SpacecraftState state, final boolean increasing) {
        return action;
    }
}
