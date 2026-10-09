package org.ursc.trajectory.propagation;

import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Common interface for all propagators (numerical, analytical, tabulated). Client
 * code can switch between a coarse analytical model and a full numerical
 * simulation by changing only the concrete type, exactly as in Orekit.
 */
public interface Propagator {

    /** @return the state the propagation starts from. */
    SpacecraftState getInitialState();

    /**
     * Propagate to a target date.
     *
     * @param target the date to propagate to
     * @return the spacecraft state at the target date
     */
    SpacecraftState propagate(AbsoluteDate target);
}
