package org.ursc.trajectory.ode;

/** A first-order ODE system {@code dy/dt = f(t, y)}. */
public interface FirstOrderDifferentialEquations {

    /** @return the dimension of the state vector. */
    int getDimension();

    /**
     * Evaluate the derivatives.
     *
     * @param t    independent variable (time, s)
     * @param y    state vector
     * @param yDot output derivatives (filled in by the implementation)
     */
    void computeDerivatives(double t, double[] y, double[] yDot);
}
