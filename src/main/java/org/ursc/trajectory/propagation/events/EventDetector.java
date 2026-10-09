package org.ursc.trajectory.propagation.events;

import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * A discrete event, detected as a sign change of a continuous switching function
 * {@code g(state)} along the trajectory. The propagator sub-samples each step at
 * no more than {@link #getMaxCheckInterval()} seconds, brackets the root and
 * refines it to {@link #getThreshold()} seconds, then invokes
 * {@link #eventOccurred}.
 */
public interface EventDetector {

    /** The switching function; events occur where it crosses zero. */
    double g(SpacecraftState state);

    /**
     * Action to take when the event is detected.
     *
     * @param state      the state at the event
     * @param increasing whether g was increasing through zero
     * @return the action the propagator should take
     */
    Action eventOccurred(SpacecraftState state, boolean increasing);

    /** @return the maximum time (s) between checks of g. */
    default double getMaxCheckInterval() {
        return 60.0;
    }

    /** @return the convergence threshold (s) for root location. */
    default double getThreshold() {
        return 1.0e-3;
    }

    /** What the propagator does after an event. */
    enum Action {
        /** Continue propagation. */
        CONTINUE,
        /** Stop propagation at the event. */
        STOP
    }
}
