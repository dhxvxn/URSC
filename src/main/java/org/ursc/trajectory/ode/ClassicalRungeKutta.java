package org.ursc.trajectory.ode;

/**
 * The classical fixed-step fourth-order Runge-Kutta integrator (RK4). Simple and
 * predictable; good for teaching and for comparison against the adaptive
 * integrator. The suggested step size is used verbatim.
 */
public final class ClassicalRungeKutta implements ODEIntegrator {

    @Override
    public StepResult singleStep(final FirstOrderDifferentialEquations eq,
                                 final double t, final double[] y, final double hTry) {
        final int n = eq.getDimension();
        final double h = hTry;

        final double[] k1 = new double[n];
        final double[] k2 = new double[n];
        final double[] k3 = new double[n];
        final double[] k4 = new double[n];
        final double[] tmp = new double[n];

        eq.computeDerivatives(t, y, k1);

        for (int i = 0; i < n; i++) {
            tmp[i] = y[i] + 0.5 * h * k1[i];
        }
        eq.computeDerivatives(t + 0.5 * h, tmp, k2);

        for (int i = 0; i < n; i++) {
            tmp[i] = y[i] + 0.5 * h * k2[i];
        }
        eq.computeDerivatives(t + 0.5 * h, tmp, k3);

        for (int i = 0; i < n; i++) {
            tmp[i] = y[i] + h * k3[i];
        }
        eq.computeDerivatives(t + h, tmp, k4);

        final double[] y1 = new double[n];
        for (int i = 0; i < n; i++) {
            y1[i] = y[i] + (h / 6.0) * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]);
        }

        final double[] yd1 = new double[n];
        eq.computeDerivatives(t + h, y1, yd1);

        return new StepResult(t, t + h, y.clone(), y1, k1, yd1, h);
    }

    @Override
    public String getName() {
        return "ClassicalRungeKutta(RK4)";
    }
}
