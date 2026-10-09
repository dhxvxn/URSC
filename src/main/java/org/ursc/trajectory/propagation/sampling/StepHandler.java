package org.ursc.trajectory.propagation.sampling;

import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Callback invoked by a propagator at regular output steps. Several handlers may
 * be registered at once (e.g. a fine-grained CSV logger and a coarse progress
 * reporter). Implementations should not assume anything about the propagator's
 * internal integration step &mdash; states are delivered on the requested output
 * grid via dense interpolation.
 */
public interface StepHandler {

    /** Called once before propagation starts. */
    default void init(SpacecraftState initialState, AbsoluteDate target) {
    }

    /**
     * Called at each output grid point.
     *
     * @param state the interpolated state at this grid point
     */
    void handleStep(SpacecraftState state);

    /** Called once after propagation finishes, with the final state. */
    default void finish(SpacecraftState finalState) {
    }
}
