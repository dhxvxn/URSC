package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.forces.NewtonianAttraction;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.ode.DormandPrince54Integrator;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.orbits.PositionAngle;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.analytical.KeplerianPropagator;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Under pure central attraction, the numerical propagator must (a) conserve
 * energy and (b) agree with the analytical Keplerian propagator.
 */
class TwoBodyPropagationTest {

    private static final double MU = Constants.EARTH_MU;

    private SpacecraftState initialState() {
        final AbsoluteDate date =
                new AbsoluteDate(2024, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        final KeplerianOrbit orbit = new KeplerianOrbit(
                7_000_000.0, 0.01, Math.toRadians(45.0),
                0.0, 0.0, 0.0, PositionAngle.MEAN, FramesFactory.getGCRF(), date, MU);
        return new SpacecraftState(orbit, 500.0);
    }

    private double specificEnergy(final SpacecraftState s) {
        final Vector3D r = s.getPosition();
        final Vector3D v = s.getVelocity();
        return v.getNormSq() / 2.0 - MU / r.getNorm();
    }

    @Test
    void energyIsConserved() {
        final SpacecraftState s0 = initialState();
        final NumericalPropagator propagator = new NumericalPropagator(
                s0, new DormandPrince54Integrator(1.0e-9, 1.0e-12, 1.0e-3, 300.0), 60.0)
                .addForceModel(new NewtonianAttraction(MU));

        final double period = s0.getOrbit().getKeplerianPeriod();
        final SpacecraftState sf = propagator.propagate(s0.getDate().shiftedBy(10 * period));

        final double e0 = specificEnergy(s0);
        final double ef = specificEnergy(sf);
        assertEquals(e0, ef, Math.abs(e0) * 1.0e-9, "specific energy drifted");
    }

    @Test
    void matchesAnalyticalKeplerian() {
        final SpacecraftState s0 = initialState();
        final NumericalPropagator numerical = new NumericalPropagator(
                s0, new DormandPrince54Integrator(1.0e-10, 1.0e-12, 1.0e-3, 300.0), 60.0)
                .addForceModel(new NewtonianAttraction(MU))
                .setOutputType(OrbitType.CARTESIAN);
        final KeplerianPropagator analytical = new KeplerianPropagator(s0);

        final AbsoluteDate target = s0.getDate().shiftedBy(3600.0);
        final Vector3D rNum = numerical.propagate(target).getPosition();
        final Vector3D rAna = analytical.propagate(target).getPosition();

        // agreement to well under a metre after an hour
        assertTrue(rNum.distance(rAna) < 0.5,
                "numerical vs analytical position differ by " + rNum.distance(rAna) + " m");
    }
}
