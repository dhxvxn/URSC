package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.estimation.BatchLeastSquares;
import org.ursc.trajectory.estimation.PositionObservation;
import org.ursc.trajectory.forces.NewtonianAttraction;
import org.ursc.trajectory.forces.drag.DragForce;
import org.ursc.trajectory.forces.drag.ExponentialAtmosphere;
import org.ursc.trajectory.forces.drag.IsotropicDrag;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.ode.DormandPrince54Integrator;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.orbits.PositionAngle;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Self-consistency tests for {@link BatchLeastSquares}: generate noise-free
 * position observations from a known truth trajectory, perturb the initial
 * guess, and confirm the batch fit recovers the truth. No external data.
 */
class BatchLeastSquaresTest {

    private static final double MU = Constants.EARTH_MU;
    private static final double MASS = 500.0;

    private SpacecraftState seed(final double altKm, final double e, final double iDeg) {
        final AbsoluteDate d = new AbsoluteDate(2024, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        final KeplerianOrbit o = new KeplerianOrbit(
                Constants.EARTH_EQUATORIAL_RADIUS + altKm * 1000.0, e, Math.toRadians(iDeg),
                0.0, Math.toRadians(30.0), 0.0, PositionAngle.MEAN,
                FramesFactory.getGCRF(), d, MU);
        return new SpacecraftState(o, MASS);
    }

    /** Build a propagator from [rx,ry,rz,vx,vy,vz,(Cd)]; Cd slot optional. */
    private SampledPropagator build(final double[] p, final SpacecraftState s0,
                                    final double fixedCd, final double area) {
        final Vector3D r = new Vector3D(p[0], p[1], p[2]);
        final Vector3D v = new Vector3D(p[3], p[4], p[5]);
        final double cd = p.length > 6 ? p[6] : fixedCd;
        final SpacecraftState st = new SpacecraftState(
                new CartesianOrbit(new PVCoordinates(r, v), s0.getFrame(), s0.getDate(), MU), MASS);
        final NumericalPropagator prop =
                new NumericalPropagator(st, new DormandPrince54Integrator(1e-8, 1e-10, 1e-3, 60.0), 30.0);
        prop.addForceModel(new NewtonianAttraction(MU));
        prop.addForceModel(new DragForce(new ExponentialAtmosphere(), new IsotropicDrag(cd, area)));
        prop.setOutputType(OrbitType.CARTESIAN);
        return prop;
    }

    private List<PositionObservation> observe(final SampledPropagator truth, final AbsoluteDate t0,
                                              final double step, final int count) {
        final EphemerisCollector col = new EphemerisCollector();
        truth.setStepHandler(step, col);
        truth.propagate(t0.shiftedBy((count - 1) * step));
        final List<SpacecraftState> states = col.getStates();
        final List<PositionObservation> obs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            obs.add(new PositionObservation(states.get(i).getDate(), states.get(i).getPosition()));
        }
        return obs;
    }

    @Test
    void recoversPerturbedInitialState() {
        final SpacecraftState s0 = seed(600, 0.001, 51.6);
        final Vector3D r0 = s0.getPosition();
        final Vector3D v0 = s0.getVelocity();
        final double cd = 2.2;
        final double area = 4.0;

        final double step = 60.0;
        final int count = 40; // 39 minutes
        final List<PositionObservation> obs = observe(
                build(new double[] {r0.getX(), r0.getY(), r0.getZ(), v0.getX(), v0.getY(), v0.getZ()},
                        s0, cd, area), s0.getDate(), step, count);

        // perturb the guess: +40 m radial-ish, +0.04 m/s
        final double[] guess = {r0.getX() + 40, r0.getY() - 25, r0.getZ() + 15,
                v0.getX() + 0.04, v0.getY() - 0.03, v0.getZ() + 0.02};
        final double[] scale = {1, 1, 1, 1e-3, 1e-3, 1e-3};

        final BatchLeastSquares.Result res = new BatchLeastSquares(
                params -> build(params, s0, cd, area), obs, scale).estimate(guess, 15);

        assertTrue(res.postFitRms < 1.0e-2,
                "post-fit RMS should collapse to ~mm, was " + res.postFitRms + " m");
        final double dr = Math.hypot(Math.hypot(res.params[0] - r0.getX(), res.params[1] - r0.getY()),
                res.params[2] - r0.getZ());
        assertTrue(dr < 1.0e-2, "recovered position off by " + dr + " m");
    }

    @Test
    void recoversDragCoefficient() {
        // low perigee → strong, observable drag signal so Cd is well-determined
        final SpacecraftState s0 = seed(300, 0.001, 51.6);
        final Vector3D r0 = s0.getPosition();
        final Vector3D v0 = s0.getVelocity();
        final double trueCd = 3.0;
        final double area = 5.0;

        final double step = 60.0;
        final int count = 90; // ~89 minutes, roughly one revolution
        final List<PositionObservation> obs = observe(
                build(new double[] {r0.getX(), r0.getY(), r0.getZ(), v0.getX(), v0.getY(), v0.getZ(), trueCd},
                        s0, trueCd, area), s0.getDate(), step, count);

        final double[] guess = {r0.getX(), r0.getY(), r0.getZ(), v0.getX(), v0.getY(), v0.getZ(), 2.2};
        final double[] scale = {1, 1, 1, 1e-3, 1e-3, 1e-3, 1e-2};

        final BatchLeastSquares.Result res = new BatchLeastSquares(
                params -> build(params, s0, trueCd, area), obs, scale).estimate(guess, 20);

        assertTrue(res.postFitRms < 1.0,
                "post-fit RMS should be sub-metre, was " + res.postFitRms + " m");
        assertTrue(Math.abs(res.params[6] - trueCd) < 0.05,
                "recovered Cd " + res.params[6] + " should be near " + trueCd);
    }
}
