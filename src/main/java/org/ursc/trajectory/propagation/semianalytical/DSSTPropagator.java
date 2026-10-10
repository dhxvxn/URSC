package org.ursc.trajectory.propagation.semianalytical;

import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.ode.ClassicalRungeKutta;
import org.ursc.trajectory.ode.FirstOrderDifferentialEquations;
import org.ursc.trajectory.ode.StepInterpolator;
import org.ursc.trajectory.ode.StepResult;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.EquinoctialOrbit;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.Orbit;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.orbits.PositionAngle;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.events.EventDetector;
import org.ursc.trajectory.propagation.sampling.StepHandler;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Semi-analytical mean-element propagator (numerically-averaged DSST).
 *
 * <p>The fast angle is removed by orbit-averaging the variational equations, so
 * the six mean equinoctial elements {@code (a, ex, ey, hx, hy, lambda)} evolve
 * smoothly and are stepped with a large fixed step &mdash; far cheaper than
 * Cowell for multi-year / lifetime runs, using the <em>same</em> force models.</p>
 *
 * <p>Each averaging step builds the osculating state at {@code N} points around
 * the current mean orbit, evaluates the perturbing acceleration (sum of the
 * registered {@link ForceModel}s minus the two-body term), and averages the
 * element rates {@code d E/dt = dE/dv . gamma} (the perturbation only enters the
 * velocity equation; {@code dE/dv} is formed by finite differences). The mean
 * longitude additionally advances at the mean motion {@code n(a)}.</p>
 *
 * <p>Output is <strong>mean</strong> elements (short-period oscillation is
 * averaged out) &mdash; the right quantity for long-term trend and decay studies.</p>
 */
public final class DSSTPropagator implements SampledPropagator {

    private static final int STATE_DIM = 6;
    private static final double FD_STEP = 1.0e-3; // velocity finite-difference step (m/s)

    private final SpacecraftState initialState;
    private final Frame frame;
    private final double mu;
    private final double mass;
    private final double step;
    private final int averagingPoints;

    private final List<ForceModel> forceModels = new ArrayList<>();
    private final List<StepHandler> stepHandlers = new ArrayList<>();
    private final List<EventDetector> eventDetectors = new ArrayList<>();
    private double outputStep = 0.0;
    private OrbitType outputType = OrbitType.KEPLERIAN;

    /**
     * @param initialState    initial state (treated as the mean state at epoch)
     * @param step            mean-element integration step (s), e.g. 43200 (half day)
     * @param averagingPoints number of quadrature nodes per revolution (e.g. 24)
     */
    public DSSTPropagator(final SpacecraftState initialState, final double step,
                          final int averagingPoints) {
        this.initialState = initialState;
        this.frame = initialState.getFrame();
        this.mu = initialState.getMu();
        this.mass = initialState.getMass();
        this.step = step;
        this.averagingPoints = averagingPoints;
    }

    @Override
    public DSSTPropagator addForceModel(final ForceModel force) {
        forceModels.add(force);
        return this;
    }

    @Override
    public List<ForceModel> getForceModels() {
        return forceModels;
    }

    @Override
    public DSSTPropagator addEventDetector(final EventDetector detector) {
        eventDetectors.add(detector);
        return this;
    }

    @Override
    public DSSTPropagator setStepHandler(final double outputStep, final StepHandler handler) {
        this.outputStep = outputStep;
        this.stepHandlers.add(handler);
        return this;
    }

    @Override
    public DSSTPropagator addStepHandler(final StepHandler handler) {
        this.stepHandlers.add(handler);
        return this;
    }

    @Override
    public DSSTPropagator setOutputStep(final double outputStep) {
        this.outputStep = outputStep;
        return this;
    }

    @Override
    public DSSTPropagator setOutputType(final OrbitType outputType) {
        this.outputType = outputType;
        return this;
    }

    @Override
    public SpacecraftState getInitialState() {
        return initialState;
    }

    @Override
    public SpacecraftState propagate(final AbsoluteDate target) {
        final AbsoluteDate start = initialState.getDate();
        final double tEnd = target.durationFrom(start);
        double[] y = meanFromOrbit(initialState.getOrbit());

        final FirstOrderDifferentialEquations eq = new MeanEquations(start);
        final ClassicalRungeKutta integrator = new ClassicalRungeKutta();
        final boolean sampling = outputStep > 0.0 && !stepHandlers.isEmpty();

        for (final StepHandler h : stepHandlers) {
            h.init(initialState, target);
        }
        if (tEnd == 0.0) {
            final SpacecraftState s = buildState(0.0, y, start);
            if (sampling) {
                emit(s);
            }
            finish(s);
            return convertOutput(s);
        }

        final double sign = Math.signum(tEnd);
        double t = 0.0;
        double lastEmitted = Double.NaN;
        if (sampling) {
            emit(buildState(0.0, y, start));
            lastEmitted = 0.0;
        }
        double nextOutput = sign * outputStep;

        while (sign > 0 ? t < tEnd - 1.0e-6 : t > tEnd + 1.0e-6) {
            double h = sign * step;
            final double remaining = tEnd - t;
            if (Math.abs(h) > Math.abs(remaining)) {
                h = remaining;
            }
            final StepResult sr = integrator.singleStep(eq, t, y, h);
            final StepInterpolator interp = new StepInterpolator(sr);

            final Double eventTime = detectEvents(interp, start);
            if (eventTime != null) {
                if (sampling) {
                    while (sign > 0 ? nextOutput <= eventTime : nextOutput >= eventTime) {
                        emit(buildState(nextOutput, interp.interpolate(nextOutput), start));
                        nextOutput += sign * outputStep;
                    }
                }
                final SpacecraftState eventState =
                        buildState(eventTime, interp.interpolate(eventTime), start);
                if (sampling) {
                    emit(eventState);
                }
                finish(eventState);
                return convertOutput(eventState);
            }

            if (sampling) {
                while (sign > 0 ? nextOutput <= sr.t1 + 1.0e-6 : nextOutput >= sr.t1 - 1.0e-6) {
                    emit(buildState(nextOutput, interp.interpolate(nextOutput), start));
                    lastEmitted = nextOutput;
                    nextOutput += sign * outputStep;
                }
            }
            t = sr.t1;
            y = sr.y1;
        }

        final SpacecraftState finalState = buildState(tEnd, y, start);
        if (sampling && (Double.isNaN(lastEmitted) || Math.abs(lastEmitted - tEnd) > 1.0e-6)) {
            emit(finalState);
        }
        finish(finalState);
        return convertOutput(finalState);
    }

    // ---- averaging ----

    /** Averaged mean-element rates at the given mean state and date. */
    private void meanRates(final double[] y, final AbsoluteDate date, final double[] out) {
        final double a = y[0];
        final double ex = y[1];
        final double ey = y[2];
        final double hx = y[3];
        final double hy = y[4];
        final double e = Math.hypot(ex, ey);
        final double inc = 2.0 * Math.atan(Math.hypot(hx, hy));
        final double raan = Math.atan2(hy, hx);
        final double peri = Math.atan2(ey, ex) - raan;

        final double[] acc = new double[STATE_DIM];
        for (int j = 0; j < averagingPoints; j++) {
            final double ecc = Constants.TWO_PI * (j + 0.5) / averagingPoints;
            final double trueAnom = 2.0 * Math.atan2(
                    Math.sqrt(1.0 + e) * Math.sin(ecc / 2.0),
                    Math.sqrt(1.0 - e) * Math.cos(ecc / 2.0));
            final KeplerianOrbit node = new KeplerianOrbit(a, e, inc, peri, raan, trueAnom,
                    PositionAngle.TRUE, frame, date, mu);
            final PVCoordinates pv = node.getPVCoordinates();
            final SpacecraftState state =
                    new SpacecraftState(new CartesianOrbit(pv, frame, date, mu), mass);
            final Vector3D gamma = perturbation(state, pv.getPosition());

            final double[] contrib = rateContribution(pv.getPosition(), pv.getVelocity(), date, gamma);
            final double w = 1.0 - e * Math.cos(ecc);
            for (int k = 0; k < STATE_DIM; k++) {
                acc[k] += contrib[k] * w;
            }
        }
        for (int k = 0; k < STATE_DIM; k++) {
            out[k] = acc[k] / averagingPoints;
        }
        out[5] += Math.sqrt(mu / (a * a * a)); // Keplerian mean-longitude rate
    }

    /** Perturbing acceleration = sum of force models minus the two-body term. */
    private Vector3D perturbation(final SpacecraftState state, final Vector3D r) {
        Vector3D g = Vector3D.ZERO;
        for (final ForceModel f : forceModels) {
            g = g.add(f.acceleration(state));
        }
        final double rNorm = r.getNorm();
        final Vector3D central = r.scalarMultiply(-mu / (rNorm * rNorm * rNorm));
        return g.subtract(central);
    }

    /** (dE/dv . gamma) for the six equinoctial elements, by central differences. */
    private double[] rateContribution(final Vector3D r, final Vector3D v,
                                      final AbsoluteDate date, final Vector3D gamma) {
        final double[] out = new double[STATE_DIM];
        final double[] g = {gamma.getX(), gamma.getY(), gamma.getZ()};
        for (int axis = 0; axis < 3; axis++) {
            final Vector3D plus = perturbVelocity(v, axis, FD_STEP);
            final Vector3D minus = perturbVelocity(v, axis, -FD_STEP);
            final double[] ep = osculatingEquinoctial(r, plus, date);
            final double[] em = osculatingEquinoctial(r, minus, date);
            final double inv = 1.0 / (2.0 * FD_STEP);
            for (int k = 0; k < 5; k++) {
                out[k] += (ep[k] - em[k]) * inv * g[axis];
            }
            // mean longitude: unwrap the difference
            out[5] += wrapToPi(ep[5] - em[5]) * inv * g[axis];
        }
        return out;
    }

    private static Vector3D perturbVelocity(final Vector3D v, final int axis, final double d) {
        switch (axis) {
            case 0: return new Vector3D(v.getX() + d, v.getY(), v.getZ());
            case 1: return new Vector3D(v.getX(), v.getY() + d, v.getZ());
            default: return new Vector3D(v.getX(), v.getY(), v.getZ() + d);
        }
    }

    /** Osculating equinoctial elements {a, ex, ey, hx, hy, lambda} from position/velocity. */
    private double[] osculatingEquinoctial(final Vector3D r, final Vector3D v, final AbsoluteDate date) {
        final KeplerianOrbit k = new KeplerianOrbit(new PVCoordinates(r, v), frame, date, mu);
        final double e = k.getE();
        final double raan = k.getRightAscensionOfAscendingNode();
        final double peri = k.getPerigeeArgument();
        final double varpi = peri + raan;
        final double tanHalfI = Math.tan(k.getI() / 2.0);
        return new double[] {
                k.getA(),
                e * Math.cos(varpi),
                e * Math.sin(varpi),
                tanHalfI * Math.cos(raan),
                tanHalfI * Math.sin(raan),
                k.getMeanAnomaly() + varpi
        };
    }

    // ---- state <-> mean element vector ----

    private double[] meanFromOrbit(final Orbit orbit) {
        final KeplerianOrbit k = (orbit instanceof KeplerianOrbit)
                ? (KeplerianOrbit) orbit : new KeplerianOrbit(orbit);
        final double e = k.getE();
        final double raan = k.getRightAscensionOfAscendingNode();
        final double varpi = k.getPerigeeArgument() + raan;
        final double tanHalfI = Math.tan(k.getI() / 2.0);
        return new double[] {
                k.getA(),
                e * Math.cos(varpi),
                e * Math.sin(varpi),
                tanHalfI * Math.cos(raan),
                tanHalfI * Math.sin(raan),
                k.getMeanAnomaly() + varpi
        };
    }

    private SpacecraftState buildState(final double t, final double[] y, final AbsoluteDate start) {
        final AbsoluteDate date = start.shiftedBy(t);
        final double a = y[0];
        final double ex = y[1];
        final double ey = y[2];
        final double hx = y[3];
        final double hy = y[4];
        final double e = Math.hypot(ex, ey);
        final double inc = 2.0 * Math.atan(Math.hypot(hx, hy));
        final double raan = Math.atan2(hy, hx);
        final double varpi = Math.atan2(ey, ex);
        final double peri = varpi - raan;
        final double meanAnomaly = y[5] - varpi;
        final Orbit orbit = new KeplerianOrbit(a, e, inc, peri, raan, meanAnomaly,
                PositionAngle.MEAN, frame, date, mu);
        return new SpacecraftState(orbit, mass);
    }

    private SpacecraftState convertOutput(final SpacecraftState state) {
        final Orbit orbit = state.getOrbit();
        switch (outputType) {
            case CARTESIAN:
                return new SpacecraftState(new CartesianOrbit(orbit), state.getMass());
            case EQUINOCTIAL:
                return new SpacecraftState(new EquinoctialOrbit(orbit), state.getMass());
            case KEPLERIAN:
            default:
                return state;
        }
    }

    // ---- sampling / events (mirrors the Cowell propagator) ----

    private void emit(final SpacecraftState state) {
        for (final StepHandler h : stepHandlers) {
            h.handleStep(state);
        }
    }

    private void finish(final SpacecraftState finalState) {
        for (final StepHandler h : stepHandlers) {
            h.finish(finalState);
        }
    }

    private Double detectEvents(final StepInterpolator interp, final AbsoluteDate start) {
        if (eventDetectors.isEmpty()) {
            return null;
        }
        Double earliest = null;
        for (final EventDetector detector : eventDetectors) {
            final double t0 = interp.getT0();
            final double t1 = interp.getT1();
            final double span = t1 - t0;
            final int subs = Math.max(1, (int) Math.ceil(Math.abs(span) / detector.getMaxCheckInterval()));
            final double dt = span / subs;
            double ta = t0;
            double ga = detector.g(buildState(ta, interp.interpolate(ta), start));
            for (int i = 1; i <= subs; i++) {
                final double tb = t0 + i * dt;
                final double gb = detector.g(buildState(tb, interp.interpolate(tb), start));
                if (ga == 0.0 || ga * gb < 0.0) {
                    final double root = (ga == 0.0) ? ta
                            : bisect(detector, interp, start, ta, tb, ga, detector.getThreshold());
                    final SpacecraftState s = buildState(root, interp.interpolate(root), start);
                    if (detector.eventOccurred(s, gb > ga) == EventDetector.Action.STOP
                            && (earliest == null || Math.abs(root - t0) < Math.abs(earliest - t0))) {
                        earliest = root;
                    }
                    break;
                }
                ta = tb;
                ga = gb;
            }
        }
        return earliest;
    }

    private double bisect(final EventDetector detector, final StepInterpolator interp,
                          final AbsoluteDate start, final double taIn, final double tbIn,
                          final double gaIn, final double threshold) {
        double ta = taIn;
        double tb = tbIn;
        double ga = gaIn;
        for (int iter = 0; iter < 100 && Math.abs(tb - ta) > threshold; iter++) {
            final double tm = 0.5 * (ta + tb);
            final double gm = detector.g(buildState(tm, interp.interpolate(tm), start));
            if (gm == 0.0) {
                return tm;
            }
            if (ga * gm < 0.0) {
                tb = tm;
            } else {
                ta = tm;
                ga = gm;
            }
        }
        return 0.5 * (ta + tb);
    }

    private static double wrapToPi(final double angle) {
        double a = angle % Constants.TWO_PI;
        if (a > Math.PI) {
            a -= Constants.TWO_PI;
        } else if (a <= -Math.PI) {
            a += Constants.TWO_PI;
        }
        return a;
    }

    /** The averaged mean-element equations of motion (dimension 6). */
    private final class MeanEquations implements FirstOrderDifferentialEquations {
        private final AbsoluteDate start;

        MeanEquations(final AbsoluteDate start) {
            this.start = start;
        }

        @Override
        public int getDimension() {
            return STATE_DIM;
        }

        @Override
        public void computeDerivatives(final double t, final double[] y, final double[] yDot) {
            meanRates(y, start.shiftedBy(t), yDot);
        }
    }
}
