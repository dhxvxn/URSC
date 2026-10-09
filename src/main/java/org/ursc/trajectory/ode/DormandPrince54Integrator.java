package org.ursc.trajectory.ode;

/**
 * Adaptive Dormand-Prince 5(4) integrator (the classic DOPRI5). A seventh-stage,
 * first-same-as-last (FSAL) embedded Runge-Kutta pair: the fifth-order solution
 * is propagated and the difference against the embedded fourth-order solution
 * drives per-step error control and step-size adaptation.
 *
 * <p>This is the recommended integrator for LEO propagation: it automatically
 * shrinks the step near perigee and in the denser atmosphere and grows it where
 * the dynamics are smooth, holding the local error within the requested
 * tolerances.</p>
 */
public final class DormandPrince54Integrator implements ODEIntegrator {

    // nodes
    private static final double C2 = 1.0 / 5.0;
    private static final double C3 = 3.0 / 10.0;
    private static final double C4 = 4.0 / 5.0;
    private static final double C5 = 8.0 / 9.0;

    // coupling coefficients
    private static final double A21 = 1.0 / 5.0;
    private static final double A31 = 3.0 / 40.0, A32 = 9.0 / 40.0;
    private static final double A41 = 44.0 / 45.0, A42 = -56.0 / 15.0, A43 = 32.0 / 9.0;
    private static final double A51 = 19372.0 / 6561.0, A52 = -25360.0 / 2187.0,
            A53 = 64448.0 / 6561.0, A54 = -212.0 / 729.0;
    private static final double A61 = 9017.0 / 3168.0, A62 = -355.0 / 33.0,
            A63 = 46732.0 / 5247.0, A64 = 49.0 / 176.0, A65 = -5103.0 / 18656.0;
    // 5th order weights (also the a7x row, FSAL)
    private static final double B1 = 35.0 / 384.0, B3 = 500.0 / 1113.0, B4 = 125.0 / 192.0,
            B5 = -2187.0 / 6784.0, B6 = 11.0 / 84.0;
    // 4th order (embedded) weights
    private static final double E1 = 5179.0 / 57600.0, E3 = 7571.0 / 16695.0,
            E4 = 393.0 / 640.0, E5 = -92097.0 / 339200.0, E6 = 187.0 / 2100.0, E7 = 1.0 / 40.0;

    private static final double SAFETY = 0.9;
    private static final double MIN_FACTOR = 0.2;
    private static final double MAX_FACTOR = 5.0;

    private final double absoluteTolerance;
    private final double relativeTolerance;
    private final double minStep;
    private final double maxStep;

    public DormandPrince54Integrator(final double absoluteTolerance, final double relativeTolerance,
                                     final double minStep, final double maxStep) {
        this.absoluteTolerance = absoluteTolerance;
        this.relativeTolerance = relativeTolerance;
        this.minStep = minStep;
        this.maxStep = maxStep;
    }

    @Override
    public StepResult singleStep(final FirstOrderDifferentialEquations eq,
                                 final double t, final double[] y, final double hTry) {
        final int n = eq.getDimension();
        final double[] k1 = new double[n];
        eq.computeDerivatives(t, y, k1);

        double h = clampMagnitude(hTry);

        while (true) {
            final double[] k2 = new double[n];
            final double[] k3 = new double[n];
            final double[] k4 = new double[n];
            final double[] k5 = new double[n];
            final double[] k6 = new double[n];
            final double[] k7 = new double[n];
            final double[] tmp = new double[n];
            final double[] y5 = new double[n];

            for (int i = 0; i < n; i++) {
                tmp[i] = y[i] + h * A21 * k1[i];
            }
            eq.computeDerivatives(t + C2 * h, tmp, k2);

            for (int i = 0; i < n; i++) {
                tmp[i] = y[i] + h * (A31 * k1[i] + A32 * k2[i]);
            }
            eq.computeDerivatives(t + C3 * h, tmp, k3);

            for (int i = 0; i < n; i++) {
                tmp[i] = y[i] + h * (A41 * k1[i] + A42 * k2[i] + A43 * k3[i]);
            }
            eq.computeDerivatives(t + C4 * h, tmp, k4);

            for (int i = 0; i < n; i++) {
                tmp[i] = y[i] + h * (A51 * k1[i] + A52 * k2[i] + A53 * k3[i] + A54 * k4[i]);
            }
            eq.computeDerivatives(t + C5 * h, tmp, k5);

            for (int i = 0; i < n; i++) {
                tmp[i] = y[i] + h * (A61 * k1[i] + A62 * k2[i] + A63 * k3[i]
                        + A64 * k4[i] + A65 * k5[i]);
            }
            eq.computeDerivatives(t + h, tmp, k6);

            // 5th order solution
            for (int i = 0; i < n; i++) {
                y5[i] = y[i] + h * (B1 * k1[i] + B3 * k3[i] + B4 * k4[i] + B5 * k5[i] + B6 * k6[i]);
            }
            // FSAL: derivative at the end of the step
            eq.computeDerivatives(t + h, y5, k7);

            // error estimate = 5th - 4th order solutions
            double errNorm = 0.0;
            for (int i = 0; i < n; i++) {
                final double y4i = y[i] + h * (E1 * k1[i] + E3 * k3[i] + E4 * k4[i]
                        + E5 * k5[i] + E6 * k6[i] + E7 * k7[i]);
                final double scale = absoluteTolerance
                        + relativeTolerance * Math.max(Math.abs(y[i]), Math.abs(y5[i]));
                final double ratio = (y5[i] - y4i) / scale;
                errNorm += ratio * ratio;
            }
            errNorm = Math.sqrt(errNorm / n);

            if (errNorm <= 1.0) {
                // accepted
                double factor = (errNorm == 0.0)
                        ? MAX_FACTOR
                        : SAFETY * Math.pow(errNorm, -0.2);
                factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, factor));
                double nextStep = h * factor;
                nextStep = Math.copySign(Math.min(Math.abs(nextStep), maxStep), h);
                return new StepResult(t, t + h, y.clone(), y5, k1, k7, nextStep);
            }

            // rejected: shrink and retry
            double factor = SAFETY * Math.pow(errNorm, -0.2);
            factor = Math.max(MIN_FACTOR, factor);
            final double newH = h * factor;
            if (Math.abs(newH) < minStep) {
                // cannot meet tolerance; accept at the floor step to make progress
                return new StepResult(t, t + h, y.clone(), y5, k1, k7,
                        Math.copySign(minStep, h));
            }
            h = newH;
        }
    }

    private double clampMagnitude(final double h) {
        final double mag = Math.min(Math.max(Math.abs(h), minStep), maxStep);
        return Math.copySign(mag, h);
    }

    @Override
    public String getName() {
        return "DormandPrince54(atol=" + absoluteTolerance + ", rtol=" + relativeTolerance + ")";
    }
}
