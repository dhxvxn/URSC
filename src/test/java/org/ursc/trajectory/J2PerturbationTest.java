package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.forces.gravity.GravityField;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.forces.gravity.SphericalHarmonicGravity;
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
 * With a J2-only field, the numerically-propagated nodal regression rate must
 * match the classical secular formula
 * {@code dRAAN/dt = -1.5 n J2 (Re/p)^2 cos i}.
 */
class J2PerturbationTest {

    @Test
    void nodalRegressionMatchesSecularTheory() {
        final double mu = Constants.EARTH_MU;
        final double re = Constants.EARTH_EQUATORIAL_RADIUS;
        final double a = re + 700_000.0;
        final double e = 0.001;
        final double i = Math.toRadians(98.0); // sun-synchronous-ish

        final AbsoluteDate date =
                new AbsoluteDate(2024, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        final KeplerianOrbit orbit = new KeplerianOrbit(
                a, e, i, 0.0, Math.toRadians(45.0), 0.0,
                PositionAngle.MEAN, FramesFactory.getGCRF(), date, mu);
        final SpacecraftState s0 = new SpacecraftState(orbit, 500.0);

        // J2-only field: zero out J3..J6 from the default
        final GravityField full = GravityFieldFactory.getDefaultZonalField();
        final double[][] c = new double[7][7];
        final double[][] s = new double[7][7];
        c[0][0] = 1.0;
        c[2][0] = full.getC(2, 0);
        final GravityField j2Only = new GravityField(mu, re, 6, 0, c, s);

        final NumericalPropagator propagator = new NumericalPropagator(
                s0, new DormandPrince54Integrator(1.0e-8, 1.0e-11, 1.0e-3, 120.0), 30.0)
                .addForceModel(new SphericalHarmonicGravity(j2Only, 2, 0))
                .setOutputType(OrbitType.KEPLERIAN);

        final double days = 1.0;
        final SpacecraftState sf = propagator.propagate(s0.getDate().shiftedBy(days * 86400.0));
        final KeplerianOrbit kf = new KeplerianOrbit(sf.getOrbit());

        double dRaan = kf.getRightAscensionOfAscendingNode()
                - orbit.getRightAscensionOfAscendingNode();
        // unwrap into (-pi, pi]
        while (dRaan > Math.PI) {
            dRaan -= 2 * Math.PI;
        }
        while (dRaan <= -Math.PI) {
            dRaan += 2 * Math.PI;
        }
        final double observedRate = dRaan / (days * 86400.0);

        final double n = Math.sqrt(mu / (a * a * a));
        final double p = a * (1 - e * e);
        final double j2 = -c[2][0]; // C20 = -J2
        final double theoreticalRate =
                -1.5 * n * j2 * (re / p) * (re / p) * Math.cos(i);

        // agreement to a few percent (numerical includes short-period terms)
        assertEquals(theoreticalRate, observedRate, Math.abs(theoreticalRate) * 0.03,
                "nodal regression rate mismatch");
    }
}
