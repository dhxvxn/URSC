package org.ursc.trajectory.propagation;

import java.util.List;

import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.propagation.events.EventDetector;
import org.ursc.trajectory.propagation.sampling.StepHandler;

/**
 * A {@link Propagator} that is configured with force models, produces sampled
 * output through {@link StepHandler}s and supports {@link EventDetector}s. Both
 * the Cowell {@code NumericalPropagator} and the semi-analytical
 * {@code DSSTPropagator} implement it, so the CLI, decay predictor and
 * comparison analysis work with either engine.
 *
 * <p>The mutator methods return {@code SampledPropagator} so they can be chained;
 * implementations may return their own type (a covariant subtype).</p>
 */
public interface SampledPropagator extends Propagator {

    SampledPropagator addForceModel(ForceModel force);

    List<ForceModel> getForceModels();

    SampledPropagator addEventDetector(EventDetector detector);

    SampledPropagator setStepHandler(double outputStep, StepHandler handler);

    SampledPropagator addStepHandler(StepHandler handler);

    SampledPropagator setOutputStep(double outputStep);

    SampledPropagator setOutputType(OrbitType outputType);
}
