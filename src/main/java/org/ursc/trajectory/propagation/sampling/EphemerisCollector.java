package org.ursc.trajectory.propagation.sampling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * A step handler that accumulates every sampled state into an in-memory list,
 * giving an ephemeris that can be navigated after propagation &mdash; the
 * analogue of Orekit's ephemeris generator.
 */
public final class EphemerisCollector implements StepHandler {

    private final List<SpacecraftState> states = new ArrayList<>();

    @Override
    public void handleStep(final SpacecraftState state) {
        states.add(state);
    }

    /** @return an unmodifiable view of the collected states, in time order. */
    public List<SpacecraftState> getStates() {
        return Collections.unmodifiableList(states);
    }

    public int size() {
        return states.size();
    }
}
