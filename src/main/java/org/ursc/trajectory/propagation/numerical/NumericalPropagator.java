package org.ursc.trajectory.propagation.numerical;

import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.ode.FirstOrderDifferentialEquations;
import org.ursc.trajectory.ode.ODEIntegrator;
import org.ursc.trajectory.ode.StepInterpolator;
import org.ursc.trajectory.ode.StepResult;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.EquinoctialOrbit;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.Orbit;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.events.EventDetector;
import org.ursc.trajectory.propagation.sampling.StepHandler;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Cowell numerical propagator: integrates the full Cartesian equations of motion
 * under the sum of the registered {@link ForceModel}s, using any
 * {@link ODEIntegrator}. This is the heart of the library and the most accurate
 * propagation mode.
 *
 * <p>Force models, the integrator, the output representation, the output step and
 * event detectors are all injected, so the same engine serves everything from a
 * quick two-body check to a high-fidelity multi-perturbation lifetime run.</p>
 */
public final class NumericalPropagator implements SampledPropagator {

    private final SpacecraftState initialState;
    private final Frame frame;
    private final double mu;
    private final double mass;
    private final ODEIntegrator integrator;
    private final double initialStep;

    private final List<ForceModel> forceModels = new ArrayList<>();
    private final List<StepHandler> stepHandlers = new ArrayList<>();
    private final List<EventDetector> eventDetectors = new ArrayList<>();
    private double outputStep = 0.0;
    private OrbitType outputType = OrbitType.CARTESIAN;

    public NumericalPropagator(final SpacecraftState initialState,
                               final ODEIntegrator integrator, final double initialStep) {
        this.initialState = initialState;
        this.frame = initialState.getFrame();
        this.mu = initialState.getMu();
        this.mass = initialState.getMass();
        this.integrator = integrator;
        this.initialStep = initialStep;
    }

    public NumericalPropagator addForceModel(final ForceModel force) {
        forceModels.add(force);
        return this;
    }

    public List<ForceModel> getForceModels() {
        return forceModels;
    }

    public NumericalPropagator addEventDetector(final EventDetector detector) {
        eventDetectors.add(detector);
        return this;
    }

    /** Register a step handler and the output grid spacing (s) delivered to all handlers. */
    public NumericalPropagator setStepHandler(final double outputStep, final StepHandler handler) {
        this.outputStep = outputStep;
        this.stepHandlers.add(handler);
        return this;
    }

    public NumericalPropagator addStepHandler(final StepHandler handler) {
        this.stepHandlers.add(handler);
        return this;
    }

    public NumericalPropagator setOutputStep(final double outputStep) {
        this.outputStep = outputStep;
        return this;
    }

    public NumericalPropagator setOutputType(final OrbitType outputType) {
        this.outputType = outputType;
        return this;
    }

    @Override
    public SpacecraftState getInitialState() {
        return initialState;
    }

    @Override
    public SpacecraftState propagate(final AbsoluteDate target) {
        final AbsoluteDate startDate = initialState.getDate();
        final double tEnd = target.durationFrom(startDate);

        final PVCoordinates pv0 = initialState.getPVCoordinates();
        double[] y = {
                pv0.getPosition().getX(), pv0.getPosition().getY(), pv0.getPosition().getZ(),
                pv0.getVelocity().getX(), pv0.getVelocity().getY(), pv0.getVelocity().getZ()
        };

        final FirstOrderDifferentialEquations eq = new EquationsOfMotion(startDate);

        final boolean sampling = outputStep > 0.0 && !stepHandlers.isEmpty();
        for (final StepHandler h : stepHandlers) {
            h.init(initialState, target);
        }

        if (tEnd == 0.0) {
            final SpacecraftState s = buildState(0.0, y, startDate);
            if (sampling) {
                for (final StepHandler h : stepHandlers) {
                    h.handleStep(s);
                }
            }
            finishHandlers(s);
            return convertOutput(s);
        }

        final double sign = Math.signum(tEnd);
        double t = 0.0;
        double hTry = sign * initialStep;
        double lastEmitted = Double.NaN;

        if (sampling) {
            emit(buildState(0.0, y, startDate));
            lastEmitted = 0.0;
        }
        double nextOutput = sign * outputStep;

        while (sign > 0 ? t < tEnd - 1.0e-9 : t > tEnd + 1.0e-9) {
            final double remaining = tEnd - t;
            if (Math.abs(hTry) > Math.abs(remaining)) {
                hTry = remaining;
            }
            final StepResult step = integrator.singleStep(eq, t, y, hTry);
            final StepInterpolator interp = new StepInterpolator(step);

            // --- event detection within the step ---
            final EventOutcome outcome = detectEvents(interp, startDate);
            if (outcome != null) {
                if (sampling) {
                    emitGridUpTo(interp, outcome.time, nextOutput, sign, startDate);
                }
                final SpacecraftState eventState = buildState(outcome.time,
                        interp.interpolate(outcome.time), startDate);
                if (sampling) {
                    emit(eventState);
                }
                finishHandlers(eventState);
                return convertOutput(eventState);
            }

            // --- output sampling within the step ---
            if (sampling) {
                while (sign > 0 ? nextOutput <= step.t1 + 1.0e-9 : nextOutput >= step.t1 - 1.0e-9) {
                    final double tg = nextOutput;
                    emit(buildState(tg, interp.interpolate(tg), startDate));
                    lastEmitted = tg;
                    nextOutput += sign * outputStep;
                }
            }

            t = step.t1;
            y = step.y1;
            hTry = step.nextStep;
        }

        final SpacecraftState finalState = buildState(tEnd, y, startDate);
        if (sampling && (Double.isNaN(lastEmitted) || Math.abs(lastEmitted - tEnd) > 1.0e-6)) {
            emit(finalState);
        }
        finishHandlers(finalState);
        return convertOutput(finalState);
    }

    private void emit(final SpacecraftState state) {
        for (final StepHandler h : stepHandlers) {
            h.handleStep(state);
        }
    }

    private void emitGridUpTo(final StepInterpolator interp, final double tLimit,
                              final double startNextOutput, final double sign,
                              final AbsoluteDate startDate) {
        double nextOutput = startNextOutput;
        while (sign > 0 ? nextOutput <= tLimit : nextOutput >= tLimit) {
            emit(buildState(nextOutput, interp.interpolate(nextOutput), startDate));
            nextOutput += sign * outputStep;
        }
    }

    private void finishHandlers(final SpacecraftState finalState) {
        for (final StepHandler h : stepHandlers) {
            h.finish(finalState);
        }
    }

    /** Scan the step for the earliest STOP event; returns null if none. */
    private EventOutcome detectEvents(final StepInterpolator interp, final AbsoluteDate startDate) {
        if (eventDetectors.isEmpty()) {
            return null;
        }
        EventOutcome earliest = null;
        for (final EventDetector detector : eventDetectors) {
            final double tStart = interp.getT0();
            final double tEndStep = interp.getT1();
            final double span = tEndStep - tStart;
            final double maxCheck = detector.getMaxCheckInterval();
            final int subdivisions = Math.max(1, (int) Math.ceil(Math.abs(span) / maxCheck));
            final double dt = span / subdivisions;

            double ta = tStart;
            double ga = detector.g(buildState(ta, interp.interpolate(ta), startDate));
            for (int i = 1; i <= subdivisions; i++) {
                final double tb = tStart + i * dt;
                final double gb = detector.g(buildState(tb, interp.interpolate(tb), startDate));
                if (ga == 0.0 || ga * gb < 0.0) {
                    final double root = (ga == 0.0) ? ta
                            : bisect(detector, interp, startDate, ta, tb, ga, gb);
                    final boolean increasing = gb > ga;
                    final SpacecraftState rootState =
                            buildState(root, interp.interpolate(root), startDate);
                    if (detector.eventOccurred(rootState, increasing) == EventDetector.Action.STOP) {
                        if (earliest == null || Math.abs(root - interp.getT0())
                                < Math.abs(earliest.time - interp.getT0())) {
                            earliest = new EventOutcome(root);
                        }
                    }
                    break; // one crossing per detector per step is sufficient here
                }
                ta = tb;
                ga = gb;
            }
        }
        return earliest;
    }

    private double bisect(final EventDetector detector, final StepInterpolator interp,
                          final AbsoluteDate startDate, final double taIn, final double tbIn,
                          final double gaIn, final double gbIn) {
        double ta = taIn;
        double tb = tbIn;
        double ga = gaIn;
        final double threshold = detector.getThreshold();
        for (int iter = 0; iter < 100 && Math.abs(tb - ta) > threshold; iter++) {
            final double tm = 0.5 * (ta + tb);
            final double gm = detector.g(buildState(tm, interp.interpolate(tm), startDate));
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

    private SpacecraftState buildState(final double t, final double[] y, final AbsoluteDate startDate) {
        final AbsoluteDate date = startDate.shiftedBy(t);
        final PVCoordinates pv = new PVCoordinates(
                new Vector3D(y[0], y[1], y[2]),
                new Vector3D(y[3], y[4], y[5]));
        final Orbit orbit = new CartesianOrbit(pv, frame, date, mu);
        return new SpacecraftState(orbit, mass);
    }

    private SpacecraftState convertOutput(final SpacecraftState state) {
        final Orbit orbit = state.getOrbit();
        final Orbit converted;
        switch (outputType) {
            case KEPLERIAN:
                converted = new KeplerianOrbit(orbit);
                break;
            case EQUINOCTIAL:
                converted = new EquinoctialOrbit(orbit);
                break;
            case CARTESIAN:
            default:
                return state;
        }
        return new SpacecraftState(converted, state.getMass());
    }

    /** The Cartesian equations of motion: dr/dt = v, dv/dt = sum of accelerations. */
    private final class EquationsOfMotion implements FirstOrderDifferentialEquations {

        private final AbsoluteDate startDate;

        EquationsOfMotion(final AbsoluteDate startDate) {
            this.startDate = startDate;
        }

        @Override
        public int getDimension() {
            return 6;
        }

        @Override
        public void computeDerivatives(final double t, final double[] y, final double[] yDot) {
            final SpacecraftState state = buildState(t, y, startDate);
            Vector3D acc = Vector3D.ZERO;
            for (final ForceModel force : forceModels) {
                acc = acc.add(force.acceleration(state));
            }
            yDot[0] = y[3];
            yDot[1] = y[4];
            yDot[2] = y[5];
            yDot[3] = acc.getX();
            yDot[4] = acc.getY();
            yDot[5] = acc.getZ();
        }
    }

    private static final class EventOutcome {
        private final double time;

        EventOutcome(final double time) {
            this.time = time;
        }
    }
}
