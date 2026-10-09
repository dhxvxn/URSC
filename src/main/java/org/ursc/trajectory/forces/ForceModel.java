package org.ursc.trajectory.forces;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * A perturbing (or central) acceleration acting on the spacecraft.
 *
 * <p>This is the single extension point of the library: to add a new force model
 * &mdash; solid tides, Earth albedo, thrust, a new atmosphere, relativistic
 * corrections, anything &mdash; implement this interface and register the model
 * with the {@code NumericalPropagator}. Each model returns the acceleration it
 * contributes, expressed in the state's (inertial) frame; the propagator sums
 * them. Models are completely independent and may be enabled/disabled or
 * reconfigured individually.</p>
 */
public interface ForceModel {

    /**
     * Acceleration contributed by this model.
     *
     * @param state the current spacecraft state
     * @return the acceleration (m/s^2) in the state's frame
     */
    Vector3D acceleration(SpacecraftState state);

    /** @return a short human-readable name, used in configuration and logging. */
    String getName();
}
