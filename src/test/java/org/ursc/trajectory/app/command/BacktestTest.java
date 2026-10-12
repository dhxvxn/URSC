package org.ursc.trajectory.app.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.forces.drag.DragForce;
import org.ursc.trajectory.forces.drag.ExponentialAtmosphere;
import org.ursc.trajectory.forces.drag.IsotropicDrag;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.forces.gravity.SphericalHarmonicGravity;
import org.ursc.trajectory.frames.ConstantEop;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.ode.DormandPrince54Integrator;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.orbits.PositionAngle;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Validates the force-model-selection backtest: on a synthetic truth generated with a known
 * force set (central + J2 + drag at low altitude), the hold-out score for the matching model
 * (gravity + drag, with Cd estimated) must be far better than a deficient model that omits
 * drag — i.e. the backtest correctly identifies that drag matters here.
 */
class BacktestTest {

    private static final double MU = Constants.EARTH_MU;
    private static final double MASS = 1130.0;
    private static final double AREA = 8.0;
    private static final double CD_TRUTH = 2.5;

    @Test
    void backtestRanksMatchingForceModelBest() {
        FramesFactory.setModel(FramesFactory.Model.IAU_2006);
        FramesFactory.setEopProvider(new ConstantEop(0.0, 0.0, 0.0));

        final OneAxisEllipsoid earth = new OneAxisEllipsoid(
                Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING);
        final AbsoluteDate t0 = new AbsoluteDate(2024, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());

        // low-altitude orbit so drag is significant over the arc
        final KeplerianOrbit k0 = new KeplerianOrbit(Constants.EARTH_EQUATORIAL_RADIUS + 350e3,
                0.001, Math.toRadians(51.6), Math.toRadians(30), Math.toRadians(40), 0.0,
                PositionAngle.TRUE, FramesFactory.getGCRF(), t0, MU);
        final SpacecraftState s0 = new SpacecraftState(k0, MASS);

        // truth = central + J2 + exponential drag (Cd = 2.5)
        final NumericalPropagator truthProp = new NumericalPropagator(s0,
                new DormandPrince54Integrator(1e-8, 1e-10, 1e-3, 120.0), 60.0);
        truthProp.addForceModel(new SphericalHarmonicGravity(GravityFieldFactory.getEgm96(2), 2, 0));
        truthProp.addForceModel(new DragForce(new ExponentialAtmosphere(earth),
                new IsotropicDrag(CD_TRUTH, AREA)));
        truthProp.setOutputType(OrbitType.CARTESIAN);

        final double step = 60.0;
        final int hours = 6;
        final EphemerisCollector col = new EphemerisCollector();
        truthProp.setStepHandler(step, col);
        truthProp.propagate(t0.shiftedBy(hours * 3600.0));
        final List<SpacecraftState> truth = col.getStates();

        // convert the GCRF truth to ITRF records and build the ephemerides
        final List<AbsoluteDate> dates = new ArrayList<>();
        final List<Vector3D> rItrf = new ArrayList<>();
        final List<Vector3D> vItrf = new ArrayList<>();
        for (final SpacecraftState s : truth) {
            final Transform toItrf =
                    FramesFactory.getTransform(FramesFactory.getGCRF(), FramesFactory.getITRF(), s.getDate());
            final Vector3D rI = toItrf.transformPosition(s.getPosition());
            final Vector3D vI = toItrf.transformVelocity(s.getVelocity(), rI);
            dates.add(s.getDate());
            rItrf.add(rI);
            vItrf.add(vI);
        }
        final PoeOrbReader.Ephemeris full = PoeOrbReader.of(dates, rItrf, vItrf, 0.0);
        final int trainN = (int) (2 * 3600.0 / step) + 1; // first 2 hours = training arc
        final PoeOrbReader.Ephemeris train = PoeOrbReader.of(
                dates.subList(0, trainN), rItrf.subList(0, trainN), vItrf.subList(0, trainN), 0.0);

        // two force sets: matching (gravity J2 + drag) vs deficient (gravity only, no drag)
        final OrbitFiles.ForceSet matching = bare();
        matching.gravityDegree = 2; matching.gravityOrder = 0;
        matching.atmosphere = "exponential"; matching.label = "J2 + drag";

        final OrbitFiles.ForceSet deficient = bare();
        deficient.gravityDegree = 2; deficient.gravityOrder = 0;
        deficient.atmosphere = "none"; deficient.label = "J2 only (no drag)";

        final OrbitFiles.Setup setup = new OrbitFiles.Setup();
        setup.mass = MASS;
        setup.dragArea = AREA;

        final List<OrbitFiles.ForceSet> combos = new ArrayList<>();
        combos.add(matching);
        combos.add(deficient);
        final double[] rms = BacktestCommand.scoreCombos(train, full, setup, combos, 2.2, 10, 120.0);

        final double matchRms = rms[0];
        final double deficientRms = rms[1];
        assertTrue(Double.isFinite(matchRms) && Double.isFinite(deficientRms),
                "scores must be finite: " + matchRms + ", " + deficientRms);
        // the matching model recovers the truth (incl. Cd) and predicts far better
        assertTrue(matchRms < 50.0,
                "matching model should predict the held-out to ~tens of m, got " + matchRms);
        assertTrue(deficientRms > 5.0 * matchRms,
                "dropping drag must clearly hurt the hold-out score: match=" + matchRms
                        + " deficient=" + deficientRms);
    }

    private static OrbitFiles.ForceSet bare() {
        final OrbitFiles.ForceSet fs = new OrbitFiles.ForceSet();
        fs.sun = false; fs.moon = false; fs.atmosphere = "none"; fs.srp = false;
        fs.solidTides = false; fs.oceanTides = false; fs.relativity = false; fs.albedo = false;
        return fs;
    }
}
