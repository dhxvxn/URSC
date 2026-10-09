package org.ursc.trajectory.ode;

/**
 * The outcome of one accepted integration step: the state and its derivative at
 * both ends of the step (enabling cubic-Hermite dense output) plus the step size
 * suggested for the next step.
 */
public final class StepResult {

    public final double t0;
    public final double t1;
    public final double[] y0;
    public final double[] y1;
    public final double[] yDot0;
    public final double[] yDot1;
    public final double nextStep;

    public StepResult(final double t0, final double t1,
                      final double[] y0, final double[] y1,
                      final double[] yDot0, final double[] yDot1,
                      final double nextStep) {
        this.t0 = t0;
        this.t1 = t1;
        this.y0 = y0;
        this.y1 = y1;
        this.yDot0 = yDot0;
        this.yDot1 = yDot1;
        this.nextStep = nextStep;
    }
}
