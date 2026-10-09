package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.analysis.DecayPredictor;
import org.ursc.trajectory.config.PropagationConfig;
import org.ursc.trajectory.forces.drag.ExponentialAtmosphere;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.ode.DormandPrince54Integrator;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.orbits.PositionAngle;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * A low, high-drag orbit must decay quickly and deterministically, with a
 * monotonically falling altitude history — exercising the DecayPredictor end to end.
 */
class DecayPredictorTest {

    @Test
    void lowDragHeavyOrbitDecays() {
        final AbsoluteDate epoch =
                new AbsoluteDate(2024, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        final double a = Constants.EARTH_EQUATORIAL_RADIUS + 250_000.0;
        final KeplerianOrbit orbit = new KeplerianOrbit(
                a, 0.0005, Math.toRadians(51.6), 0.0, 0.0, 0.0,
                PositionAngle.MEAN, FramesFactory.getGCRF(), epoch, Constants.EARTH_MU);
        final SpacecraftState initial = new SpacecraftState(orbit, 100.0);

        final NumericalPropagator propagator = new PropagationConfig()
                .initialState(initial)
                .integrator(new DormandPrince54Integrator(1e-6, 1e-9, 1e-3, 120.0), 60.0)
                .gravityField(GravityFieldFactory.getEgm96(4), 4, 0)
                .drag(new ExponentialAtmosphere(), 2.2, 5.0)
                .outputType(OrbitType.KEPLERIAN)
                .build();

        final DecayPredictor.Result r =
                DecayPredictor.predict(propagator, 120_000.0, 1.0, 3600.0);

        assertTrue(r.decayed, "250 km high-drag orbit should decay within a year");
        assertTrue(r.daysToDecay > 0 && r.daysToDecay < 365, "decay time out of range: " + r.daysToDecay);
        assertTrue(r.altitudeKm.length >= 2, "expected an altitude history");
        // altitude should end far below where it started
        assertTrue(r.altitudeKm[r.altitudeKm.length - 1] < r.altitudeKm[0] - 50,
                "altitude should fall substantially");
    }
}
