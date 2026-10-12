package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.forces.ForceModel;
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
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.propagation.semianalytical.DSSTPropagator;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Validates the DSST osculating&harr;mean conversion and first-order short-period
 * recovery: seeding from an osculating state and restoring short-period terms on
 * output must track the full (Cowell) osculating trajectory far better than the
 * raw mean-element output, and the osculating seed must round-trip at epoch.
 */
class DsstShortPeriodTest {

    private static final double MU = Constants.EARTH_MU;

    private SpacecraftState seed() {
        final AbsoluteDate d = new AbsoluteDate(2024, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        // near-circular, Sun-synchronous-like LEO (where the osculating/mean split is largest)
        final KeplerianOrbit k = new KeplerianOrbit(
                Constants.EARTH_EQUATORIAL_RADIUS + 780e3, 0.002, Math.toRadians(98.6),
                Math.toRadians(40), Math.toRadians(30), Math.toRadians(10),
                PositionAngle.TRUE, FramesFactory.getGCRF(), d, MU);
        return new SpacecraftState(k, 1130.0);
    }

    /** Zonal J2..J6 (order 0) — includes the central term; handled cleanly by the averaging. */
    private ForceModel zonal() {
        return new SphericalHarmonicGravity(GravityFieldFactory.getEgm96(6), 6, 0);
    }

    @Test
    void shortPeriodRecoveryTracksCowellAndBeatsMeanOnly() {
        FramesFactory.setModel(FramesFactory.Model.SIMPLE);
        final SpacecraftState s0 = seed();
        final double outStep = 3600.0;
        final AbsoluteDate end = s0.getDate().shiftedBy(2.0 * 86400.0);

        final NumericalPropagator cow = new NumericalPropagator(s0,
                new DormandPrince54Integrator(1e-8, 1e-10, 1e-3, 120.0), 60.0);
        cow.addForceModel(zonal());
        cow.setOutputType(OrbitType.CARTESIAN);
        final EphemerisCollector cc = new EphemerisCollector();
        cow.setStepHandler(outStep, cc);
        cow.propagate(end);
        final List<SpacecraftState> truth = cc.getStates();

        final List<SpacecraftState> on = runDsst(s0, outStep, end, true);
        final List<SpacecraftState> off = runDsst(s0, outStep, end, false);

        final int n = Math.min(truth.size(), Math.min(on.size(), off.size()));
        double rmsOn = 0, rmsOff = 0;
        for (int i = 0; i < n; i++) {
            final Vector3D rt = truth.get(i).getPosition();
            rmsOn += on.get(i).getPosition().subtract(rt).getNormSq();
            rmsOff += off.get(i).getPosition().subtract(rt).getNormSq();
        }
        rmsOn = Math.sqrt(rmsOn / n);
        rmsOff = Math.sqrt(rmsOff / n);

        // short-period recovery tracks the osculating truth to the first-order (O(J2^2)) level
        assertTrue(rmsOn < 3000.0, "DSST short-period RMS vs Cowell too large: " + rmsOn + " m");
        // and is dramatically better than the raw mean-element output
        assertTrue(rmsOff / rmsOn > 20.0,
                "short-period recovery should beat mean-only by >20x, got " + (rmsOff / rmsOn) + "x");
    }

    @Test
    void osculatingSeedRoundTripsAtEpoch() {
        FramesFactory.setModel(FramesFactory.Model.SIMPLE);
        final SpacecraftState s0 = seed();
        // propagating a negligible interval returns the epoch osculating state
        final List<SpacecraftState> on = runDsst(s0, 1.0, s0.getDate().shiftedBy(1.0), true);
        final double err = on.get(0).getPosition().subtract(s0.getPosition()).getNorm();
        assertTrue(err < 50.0, "osculating seed did not round-trip: " + err + " m");
    }

    private List<SpacecraftState> runDsst(final SpacecraftState s0, final double outStep,
                                          final AbsoluteDate end, final boolean shortPeriod) {
        final DSSTPropagator d = new DSSTPropagator(s0, 21600.0, 48, shortPeriod);
        d.addForceModel(zonal());
        d.setOutputType(OrbitType.CARTESIAN);
        final EphemerisCollector col = new EphemerisCollector();
        d.setStepHandler(outStep, col);
        d.propagate(end);
        return col.getStates();
    }
}
