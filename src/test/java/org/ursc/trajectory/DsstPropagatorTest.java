package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.forces.NewtonianAttraction;
import org.ursc.trajectory.forces.drag.DragForce;
import org.ursc.trajectory.forces.drag.ExponentialAtmosphere;
import org.ursc.trajectory.forces.drag.IsotropicDrag;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.forces.gravity.SphericalHarmonicGravity;
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
import org.ursc.trajectory.propagation.semianalytical.DSSTPropagator;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/** Validates the semi-analytical mean-element (DSST) propagator. */
class DsstPropagatorTest {

    private static final double MU = Constants.EARTH_MU;

    private SpacecraftState state(final double altKm, final double e, final double iDeg) {
        final AbsoluteDate d = new AbsoluteDate(2024, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        final KeplerianOrbit o = new KeplerianOrbit(
                Constants.EARTH_EQUATORIAL_RADIUS + altKm * 1000.0, e, Math.toRadians(iDeg),
                0.0, Math.toRadians(30.0), 0.0, PositionAngle.MEAN,
                FramesFactory.getGCRF(), d, MU);
        return new SpacecraftState(o, 500.0);
    }

    @Test
    void twoBodyKeepsElementsAndMatchesKeplerian() {
        final SpacecraftState s0 = state(700, 0.01, 51.6);
        final AbsoluteDate target = s0.getDate().shiftedBy(86400.0);

        final DSSTPropagator dsst = new DSSTPropagator(s0, 3600.0, 32);
        dsst.addForceModel(new NewtonianAttraction(MU)).setOutputType(OrbitType.CARTESIAN);
        final Vector3D rDsst = dsst.propagate(target).getPosition();
        final Vector3D rKep = new KeplerianPropagator(s0).propagate(target).getPosition();

        // pure two-body: the mean elements don't drift, so DSST == Keplerian
        assertTrue(rDsst.distance(rKep) < 1.0,
                "DSST two-body vs Keplerian differ by " + rDsst.distance(rKep) + " m");
    }

    @Test
    void j2NodalRateMatchesSecularTheory() {
        final double a = Constants.EARTH_EQUATORIAL_RADIUS + 700_000.0;
        final double e = 0.001;
        final double i = Math.toRadians(98.0);
        final AbsoluteDate d0 = new AbsoluteDate(2024, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        final KeplerianOrbit o = new KeplerianOrbit(a, e, i, 0.0, Math.toRadians(45.0), 0.0,
                PositionAngle.MEAN, FramesFactory.getGCRF(), d0, MU);
        final SpacecraftState s0 = new SpacecraftState(o, 500.0);

        // J2-only field (zonal degree 2, order 0)
        final DSSTPropagator dsst = new DSSTPropagator(s0, 43200.0, 48);
        dsst.addForceModel(new SphericalHarmonicGravity(GravityFieldFactory.getEgm96(2), 2, 0))
                .setOutputType(OrbitType.KEPLERIAN);

        final double days = 10.0;
        final KeplerianOrbit kf = new KeplerianOrbit(
                dsst.propagate(d0.shiftedBy(days * 86400.0)).getOrbit());
        double dRaan = kf.getRightAscensionOfAscendingNode() - o.getRightAscensionOfAscendingNode();
        while (dRaan > Math.PI) {
            dRaan -= 2 * Math.PI;
        }
        while (dRaan <= -Math.PI) {
            dRaan += 2 * Math.PI;
        }
        final double observed = dRaan / (days * 86400.0);

        final double n = Math.sqrt(MU / (a * a * a));
        final double p = a * (1 - e * e);
        final double j2 = 1.08262668e-3;
        final double re = Constants.EARTH_EQUATORIAL_RADIUS;
        final double theory = -1.5 * n * j2 * (re / p) * (re / p) * Math.cos(i);

        assertEquals(theory, observed, Math.abs(theory) * 0.02, "DSST J2 nodal rate");
    }

    @Test
    void dragSecularDecayMatchesCowellMean() {
        // central + drag only, so Cowell osculating SMA has no J2 short-period and
        // its decay equals the secular (mean) decay the DSST produces
        final SpacecraftState s0 = state(400, 0.001, 51.6);
        final AbsoluteDate target = s0.getDate().shiftedBy(3 * 86400.0);

        final DSSTPropagator dsst = new DSSTPropagator(s0, 21600.0, 32);
        dsst.addForceModel(new NewtonianAttraction(MU))
                .addForceModel(new DragForce(new ExponentialAtmosphere(), new IsotropicDrag(2.2, 2.0)))
                .setOutputType(OrbitType.KEPLERIAN);
        final double daDsst = new KeplerianOrbit(dsst.propagate(target).getOrbit()).getA()
                - s0.getOrbit().getA();

        final NumericalPropagator cowell = new NumericalPropagator(
                s0, new DormandPrince54Integrator(1e-7, 1e-10, 1e-3, 120.0), 60.0);
        cowell.addForceModel(new NewtonianAttraction(MU))
                .addForceModel(new DragForce(new ExponentialAtmosphere(), new IsotropicDrag(2.2, 2.0)))
                .setOutputType(OrbitType.KEPLERIAN);
        final double daCowell = new KeplerianOrbit(cowell.propagate(target).getOrbit()).getA()
                - s0.getOrbit().getA();

        assertTrue(daDsst < -50.0, "expected secular SMA decay, got " + daDsst);
        assertEquals(daCowell, daDsst, Math.abs(daCowell) * 0.05,
                "DSST mean decay vs Cowell: " + daDsst + " vs " + daCowell);
    }
}
