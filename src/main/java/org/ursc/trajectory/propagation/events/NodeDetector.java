package org.ursc.trajectory.propagation.events;

import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Detects ascending/descending node crossings of the (inertial) equatorial
 * plane. The switching function is the Z component of position; it increases
 * through zero at the ascending node.
 */
public final class NodeDetector implements EventDetector {

    private final Action action;

    public NodeDetector(final Action action) {
        this.action = action;
    }

    public NodeDetector() {
        this(Action.CONTINUE);
    }

    @Override
    public double g(final SpacecraftState state) {
        return state.getPosition().getZ();
    }

    @Override
    public Action eventOccurred(final SpacecraftState state, final boolean increasing) {
        return action;
    }
}
