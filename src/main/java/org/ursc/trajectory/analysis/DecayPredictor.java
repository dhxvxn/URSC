package org.ursc.trajectory.analysis;

import java.util.List;

import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.events.AltitudeDetector;
import org.ursc.trajectory.propagation.events.EventDetector;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Predicts orbital decay: propagates until the geodetic altitude drops to a
 * re-entry threshold (via {@link AltitudeDetector}) or a maximum time is reached.
 * Reuses the existing event machinery; records a sparse altitude history for
 * plotting when requested.
 */
public final class DecayPredictor {

    /** Result of a single decay run. */
    public static final class Result {
        public final boolean decayed;
        public final AbsoluteDate decayDate;   // event date (or the cap if not decayed)
        public final double daysToDecay;       // elapsed days to the returned date
        public final double[] timeDays;        // sparse history (empty if not sampled)
        public final double[] altitudeKm;

        Result(final boolean decayed, final AbsoluteDate decayDate, final double daysToDecay,
               final double[] timeDays, final double[] altitudeKm) {
            this.decayed = decayed;
            this.decayDate = decayDate;
            this.daysToDecay = daysToDecay;
            this.timeDays = timeDays;
            this.altitudeKm = altitudeKm;
        }
    }

    private DecayPredictor() {
    }

    /**
     * @param propagator        a fully-configured propagator (its initial state is used)
     * @param reentryAltitudeM  altitude (m) at which the satellite is considered to have decayed
     * @param maxYears          propagation cap
     * @param sampleStepSeconds history sampling step; &le; 0 disables history (faster, for Monte Carlo)
     */
    public static Result predict(final NumericalPropagator propagator,
                                 final double reentryAltitudeM, final double maxYears,
                                 final double sampleStepSeconds) {
        final SpacecraftState initial = propagator.getInitialState();
        final AbsoluteDate start = initial.getDate();
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(
                Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING);

        propagator.addEventDetector(
                new AltitudeDetector(reentryAltitudeM, earth, EventDetector.Action.STOP, 60.0));

        EphemerisCollector collector = null;
        if (sampleStepSeconds > 0) {
            collector = new EphemerisCollector();
            propagator.setStepHandler(sampleStepSeconds, collector);
        }

        final AbsoluteDate cap = start.shiftedBy(maxYears * 365.25 * Constants.JULIAN_DAY);
        final SpacecraftState finalState = propagator.propagate(cap);

        final double secondsToEnd = finalState.getDate().durationFrom(start);
        final boolean decayed = finalState.getDate().isBefore(cap.shiftedBy(-1.0));
        final double daysToDecay = secondsToEnd / Constants.JULIAN_DAY;

        double[] timeDays = new double[0];
        double[] altKm = new double[0];
        if (collector != null) {
            final List<SpacecraftState> states = collector.getStates();
            timeDays = new double[states.size()];
            altKm = new double[states.size()];
            for (int i = 0; i < states.size(); i++) {
                final SpacecraftState s = states.get(i);
                timeDays[i] = s.getDate().durationFrom(start) / Constants.JULIAN_DAY;
                altKm[i] = altitudeKm(s, earth);
            }
        }
        return new Result(decayed, finalState.getDate(), daysToDecay, timeDays, altKm);
    }

    private static double altitudeKm(final SpacecraftState s, final OneAxisEllipsoid earth) {
        final var rBody = FramesFactory
                .getTransform(s.getFrame(), FramesFactory.getITRF(), s.getDate())
                .transformPosition(s.getPosition());
        return earth.getAltitude(rBody) / 1000.0;
    }
}
