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
    private static final int SP_POINTS = 64;      // nodes for the short-period quadrature

    private final SpacecraftState initialState;
    private final Frame frame;
    private final double mu;
    private final double mass;
    private final double step;
    private final int averagingPoints;
    private final boolean shortPeriod;


    private final List<ForceModel> forceModels = new ArrayList<>();
    private final List<StepHandler> stepHandlers = new ArrayList<>();
    private final List<EventDetector> eventDetectors = new ArrayList<>();
    private double outputStep = 0.0;
    private OrbitType outputType = OrbitType.KEPLERIAN;

    /**
     * @param initialState    initial (osculating) state at epoch
     * @param step            mean-element integration step (s), e.g. 43200 (half day)
     * @param averagingPoints number of quadrature nodes per revolution (e.g. 24)
     */
    public DSSTPropagator(final SpacecraftState initialState, final double step,
                          final int averagingPoints) {
        this(initialState, step, averagingPoints, true);
    }

    /**
     * @param initialState    initial state at epoch
     * @param step            mean-element integration step (s)
     * @param averagingPoints number of quadrature nodes per revolution
     * @param shortPeriod     when {@code true} (default), the osculating seed is converted to
     *                        mean elements at epoch and the first-order short-period terms are
     *                        restored on output so the result is an osculating ephemeris; when
     *                        {@code false}, the seed is taken as mean and the output is the mean
     *                        orbit (pure mean-element study).
     */
    public DSSTPropagator(final SpacecraftState initialState, final double step,
                          final int averagingPoints, final boolean shortPeriod) {
        this.initialState = initialState;
        this.frame = initialState.getFrame();
        this.mu = initialState.getMu();
        this.mass = initialState.getMass();
        this.step = step;
        this.averagingPoints = averagingPoints;
        this.shortPeriod = shortPeriod;
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
        final double[] oscSeed = meanFromOrbit(initialState.getOrbit());
        double[] y = shortPeriod ? meanFromOsculating(oscSeed, start) : oscSeed;

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

    /**
     * Osculating equinoctial elements {a, ex, ey, hx, hy, lambda} from position/velocity,
     * computed directly from the state vectors (eccentricity vector + node), with no
     * perigee/argument-of-latitude angles. This is non-singular for near-circular orbits,
     * so the velocity finite differences used for the element rates and the short-period
     * generating function stay clean at small eccentricity.
     */
    private double[] osculatingEquinoctial(final Vector3D r, final Vector3D v, final AbsoluteDate date) {
        final double rN = r.getNorm();
        final double v2 = v.getNormSq();
        final double a = 1.0 / (2.0 / rN - v2 / mu);

        final Vector3D hVec = r.crossProduct(v);
        final double inc = Math.acos(Math.max(-1.0, Math.min(1.0, hVec.getZ() / hVec.getNorm())));
        final double raan = Math.atan2(hVec.getX(), -hVec.getY()); // ascending-node right ascension
        final double tanHalfI = Math.tan(inc / 2.0);
        final double hx = tanHalfI * Math.cos(raan);
        final double hy = tanHalfI * Math.sin(raan);

        // eccentricity vector (points to perigee, in the inertial frame)
        final double rv = r.dotProduct(v);
        final Vector3D eccVec = r.scalarMultiply(v2 - mu / rN).subtract(v.scalarMultiply(rv))
                .scalarMultiply(1.0 / mu);
        final double e = eccVec.getNorm();

        // fold the orbit plane into the equatorial plane by rotating about the node by -i;
        // the folded x,y components are ex = e cos(w+O), ey = e sin(w+O)
        final Vector3D node = new Vector3D(Math.cos(raan), Math.sin(raan), 0.0);
        final Vector3D eccFold = rotateAboutAxis(eccVec, node, -inc);
        final Vector3D rFold = rotateAboutAxis(r, node, -inc);
        final double ex = eccFold.getX();
        final double ey = eccFold.getY();

        // true longitude L = O + w + nu  (robust: angle of the folded position)
        final double trueLon = Math.atan2(rFold.getY(), rFold.getX());
        final double varpi = Math.atan2(ey, ex);
        final double nu = trueLon - varpi;                      // true anomaly
        final double eccAnom = Math.atan2(Math.sqrt(Math.max(0.0, 1.0 - e * e)) * Math.sin(nu),
                e + Math.cos(nu));
        final double meanAnom = eccAnom - e * Math.sin(eccAnom);
        final double lambda = meanAnom + varpi;

        return new double[] {a, ex, ey, hx, hy, lambda};
    }

    /** Rotate {@code vec} about the unit axis {@code axis} by {@code angle} (Rodrigues). */
    private static Vector3D rotateAboutAxis(final Vector3D vec, final Vector3D axis, final double angle) {
        final double c = Math.cos(angle);
        final double s = Math.sin(angle);
        return vec.scalarMultiply(c)
                .add(axis.crossProduct(vec).scalarMultiply(s))
                .add(axis.scalarMultiply(axis.dotProduct(vec) * (1.0 - c)));
    }

    // ---- state <-> mean element vector ----

    private double[] meanFromOrbit(final Orbit orbit) {
        final PVCoordinates pv = orbit.getPVCoordinates();
        return osculatingEquinoctial(pv.getPosition(), pv.getVelocity(), orbit.getDate());
    }

    // ---- first-order short-period (osculating <-> mean) ----

    /**
     * Convert an osculating equinoctial vector to mean by removing the first-order
     * short-period terms (fixed-point iteration: mean = osc - delta(mean)).
     */
    private double[] meanFromOsculating(final double[] oscY, final AbsoluteDate date) {
        final double[] mean = oscY.clone();
        for (int it = 0; it < 2; it++) {
            final double[] sp = shortPeriodOffset(mean, date);
            for (int k = 0; k < STATE_DIM; k++) {
                mean[k] = oscY[k] - sp[k];
            }
        }
        return mean;
    }

    /**
     * First-order short-period offset (osculating - mean) of the six equinoctial
     * elements at the current anomaly of the given mean state, computed numerically as
     * the periodic part of the perturbation's Gauss element rates integrated around one
     * revolution — the DSST short-period generating function to first order. Consistent
     * with the numerical averaging used for the secular rates, and uses the same force
     * models. The mean-longitude offset also carries the indirect term from the n(a)
     * oscillation driven by the short-period in the semi-major axis.
     */
    private double[] shortPeriodOffset(final double[] y, final AbsoluteDate date) {
        final double a = y[0];
        final double e = Math.hypot(y[1], y[2]);
        final double inc = 2.0 * Math.atan(Math.hypot(y[3], y[4]));
        final double raan = Math.atan2(y[4], y[3]);
        final double varpi = Math.atan2(y[2], y[1]);
        final double peri = varpi - raan;
        final double n = Math.sqrt(mu / (a * a * a));
        final int nSeg = SP_POINTS;
        final double dE = Constants.TWO_PI / nSeg;

        // sample the perturbation element rates and the (1 - e cosE) weight at each node
        final double[][] rate = new double[nSeg + 1][STATE_DIM];
        final double[] wgt = new double[nSeg + 1];
        for (int j = 0; j <= nSeg; j++) {
            final double ecc = dE * j;
            final double trueAnom = 2.0 * Math.atan2(
                    Math.sqrt(1.0 + e) * Math.sin(ecc / 2.0),
                    Math.sqrt(1.0 - e) * Math.cos(ecc / 2.0));
            final KeplerianOrbit node = new KeplerianOrbit(a, e, inc, peri, raan, trueAnom,
                    PositionAngle.TRUE, frame, date, mu);
            final PVCoordinates pv = node.getPVCoordinates();
            final SpacecraftState st =
                    new SpacecraftState(new CartesianOrbit(pv, frame, date, mu), mass);
            final Vector3D gamma = perturbation(st, pv.getPosition());
            rate[j] = rateContribution(pv.getPosition(), pv.getVelocity(), date, gamma);
            wgt[j] = 1.0 - e * Math.cos(ecc);
        }

        // mean-anomaly average of each rate: <c_k> = (1/2pi) integral c_k (1 - e cosE) dE
        final double[] meanRate = new double[STATE_DIM];
        for (int k = 0; k < STATE_DIM; k++) {
            double s = 0.0;
            for (int j = 0; j < nSeg; j++) {
                s += rate[j][k] * wgt[j];
            }
            meanRate[k] = s / nSeg;
        }

        // cumulative generating function g_k(E) = integral (c_k - <c_k>) (1 - e cosE)/n dE
        final double[][] g = new double[nSeg + 1][STATE_DIM];
        for (int k = 0; k < STATE_DIM; k++) {
            double acc = 0.0;
            for (int j = 1; j <= nSeg; j++) {
                final double f0 = (rate[j - 1][k] - meanRate[k]) * wgt[j - 1] / n;
                final double f1 = (rate[j][k] - meanRate[k]) * wgt[j] / n;
                acc += 0.5 * (f0 + f1) * dE;
                g[j][k] = acc;
            }
        }

        // zero-mean short-period arrays sp_k(E) = g_k - <g_k>
        final double[][] sp = new double[nSeg + 1][STATE_DIM];
        for (int k = 0; k < STATE_DIM; k++) {
            double s = 0.0;
            for (int j = 0; j < nSeg; j++) {
                s += g[j][k] * wgt[j];
            }
            final double gbar = s / nSeg;
            for (int j = 0; j <= nSeg; j++) {
                sp[j][k] = g[j][k] - gbar;
            }
        }

        // indirect mean-longitude term: delta_lambda += integral -(3n/2a) delta_a dt
        double accL = 0.0;
        final double[] gL = new double[nSeg + 1];
        for (int j = 1; j <= nSeg; j++) {
            final double f0 = -(1.5 * n / a) * sp[j - 1][0] * wgt[j - 1] / n;
            final double f1 = -(1.5 * n / a) * sp[j][0] * wgt[j] / n;
            accL += 0.5 * (f0 + f1) * dE;
            gL[j] = accL;
        }
        double sL = 0.0;
        for (int j = 0; j < nSeg; j++) {
            sL += gL[j] * wgt[j];
        }
        final double gLbar = sL / nSeg;
        for (int j = 0; j <= nSeg; j++) {
            sp[j][5] += gL[j] - gLbar;
        }

        // evaluate at the current eccentric anomaly of the state
        double eCur = keplerEccentricAnomaly(y[5] - varpi, e) % Constants.TWO_PI;
        if (eCur < 0.0) {
            eCur += Constants.TWO_PI;
        }
        final double x = eCur / dE;
        final int j0 = (int) Math.floor(x);
        final double frac = x - j0;
        final int jlo = j0 % nSeg;
        final int jhi = jlo + 1; // sp[nSeg] == sp[0] (periodic), so this index is valid
        final double[] delta = new double[STATE_DIM];
        for (int k = 0; k < STATE_DIM; k++) {
            delta[k] = sp[jlo][k] + frac * (sp[jhi][k] - sp[jlo][k]);
        }
        return delta;
    }

    /** Solve Kepler's equation M = E - e sinE for the eccentric anomaly. */
    private static double keplerEccentricAnomaly(final double meanAnomaly, final double e) {
        double m = meanAnomaly % Constants.TWO_PI;
        double ea = m;
        for (int i = 0; i < 20; i++) {
            final double d = (ea - e * Math.sin(ea) - m) / (1.0 - e * Math.cos(ea));
            ea -= d;
            if (Math.abs(d) < 1.0e-13) {
                break;
            }
        }
        return ea;
    }

    private SpacecraftState buildState(final double t, final double[] y, final AbsoluteDate start) {
        final AbsoluteDate date = start.shiftedBy(t);
        // restore the first-order short-period terms so the output is osculating
        final double[] out;
        if (shortPeriod) {
            final double[] sp = shortPeriodOffset(y, date);
            out = new double[STATE_DIM];
            for (int k = 0; k < STATE_DIM; k++) {
                out[k] = y[k] + sp[k];
            }
        } else {
            out = y;
        }
        return new SpacecraftState(equinoctialToOrbit(out, date), mass);
    }

    /** Build a Keplerian orbit from an equinoctial vector {a, ex, ey, hx, hy, lambda}. */
    private Orbit equinoctialToOrbit(final double[] y, final AbsoluteDate date) {
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
        return new KeplerianOrbit(a, e, inc, peri, raan, meanAnomaly,
                PositionAngle.MEAN, frame, date, mu);
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
