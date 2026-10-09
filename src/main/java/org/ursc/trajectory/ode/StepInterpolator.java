package org.ursc.trajectory.ode;

/**
 * Cubic-Hermite dense output over a single step, built from the state and
 * derivative at both ends. Used by the propagator to sample states at arbitrary
 * output times and to locate events inside a step.
 */
public final class StepInterpolator {

    private final double t0;
    private final double t1;
    private final double h;
    private final double[] y0;
    private final double[] y1;
    private final double[] yd0;
    private final double[] yd1;

    public StepInterpolator(final StepResult step) {
        this.t0 = step.t0;
        this.t1 = step.t1;
        this.h = step.t1 - step.t0;
        this.y0 = step.y0;
        this.y1 = step.y1;
        this.yd0 = step.yDot0;
        this.yd1 = step.yDot1;
    }

    public double getT0() {
        return t0;
    }

    public double getT1() {
        return t1;
    }

    /**
     * @param t a time within [t0, t1]
     * @return the interpolated state vector
     */
    public double[] interpolate(final double t) {
        final double theta = (t - t0) / h;      // in [0, 1]
        final double theta2 = theta * theta;
        final double theta3 = theta2 * theta;

        // Hermite basis functions
        final double h00 = 2 * theta3 - 3 * theta2 + 1;
        final double h10 = theta3 - 2 * theta2 + theta;
        final double h01 = -2 * theta3 + 3 * theta2;
        final double h11 = theta3 - theta2;

        final double[] y = new double[y0.length];
        for (int i = 0; i < y.length; i++) {
            y[i] = h00 * y0[i] + h10 * h * yd0[i] + h01 * y1[i] + h11 * h * yd1[i];
        }
        return y;
    }
}
