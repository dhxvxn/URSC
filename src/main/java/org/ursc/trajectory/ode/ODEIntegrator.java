package org.ursc.trajectory.ode;

/**
 * A numerical integrator that advances an ODE by one accepted step. The
 * propagator owns the time loop and calls this repeatedly, so fixed-step and
 * adaptive-step integrators share one interface; adaptive integrators perform
 * their own error control and step-size adaptation inside {@link #singleStep}.
 */
public interface ODEIntegrator {

    /**
     * Advance one accepted step starting at {@code (t, y)} attempting size
     * {@code hTry} (its sign gives the integration direction).
     *
     * @param equations the ODE system
     * @param t         current time
     * @param y         current state (not modified)
     * @param hTry      suggested step size (signed)
     * @return the accepted step result
     */
    StepResult singleStep(FirstOrderDifferentialEquations equations,
                          double t, double[] y, double hTry);

    /** @return a short name for the integrator. */
    String getName();
}
