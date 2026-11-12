package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.conversion.NumericalPropagatorBuilder;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.PVCoordinatesProvider;
import org.orekit.utils.TimeStampedPVCoordinates;
import org.ursc.trajectory.frames.ConstantEop;
import org.ursc.trajectory.gnss.GpsConstants;
import org.ursc.trajectory.gnss.RinexObsReader;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource;
import org.ursc.trajectory.orekit.OrekitCarrierPhaseOd;
import org.ursc.trajectory.orekit.OrekitDates;
import org.ursc.trajectory.orekit.OrekitDynamics;
import org.ursc.trajectory.orekit.OrekitGpsSource;
import org.ursc.trajectory.orekit.OrekitSession;
import org.ursc.trajectory.pipeline.RunConfig;
import org.ursc.trajectory.testutil.SyntheticGnss;

/**
 * Closed-loop test of the Orekit carrier-phase orbit determination: dual-frequency code and phase
 * are simulated from a known two-body LEO orbit to a synthetic GPS constellation (receiver clock,
 * float ambiguities, one wide-lane cycle slip and one equal L1/L2 slip, antenna offset), with an
 * independent light-time model; the estimator must recover the orbit from a perturbed start.
 */
class OrekitCarrierPhaseOdTest {

    private static final double C = Constants.SPEED_OF_LIGHT;
    private static final double MU = Constants.EIGEN5C_EARTH_MU;

    @Test
    void recoversOrbitFromCodeAndCarrierPhase() throws Exception {
        final Path de = Paths.get("data", "ephemeris", "lnxp1990.440");
        Assumptions.assumeTrue(Files.isRegularFile(de), "DE440 file not present");
        final org.ursc.trajectory.time.AbsoluteDate d0 = new org.ursc.trajectory.time.AbsoluteDate(2024, 10, 15, 0, 0, 0.0,
                org.ursc.trajectory.time.TimeScalesFactory.getGPS());
        try (OrekitSession s = OrekitSession.open(ConstantEop.ZERO, d0.shiftedBy(-86400), d0.shiftedBy(86400), null, de)) {
            final AbsoluteDate t0 = OrekitDates.toOrekit(d0);
            final KeplerianOrbit kep = new KeplerianOrbit(Constants.WGS84_EARTH_EQUATORIAL_RADIUS + 800e3, 0.001,
                    Math.toRadians(98.6), 0.3, 1.1, 0.2, PositionAngleType.MEAN, s.gcrf, t0, MU);
            final CartesianOrbit truth = new CartesianOrbit(kep);
            final Vector3D antBody = new Vector3D(1.4, -0.4, -0.8);
            final org.orekit.attitudes.AttitudeProvider att = OrekitDynamics.nominalAttitude(s);
            final SyntheticGnss.Constellation gps = new SyntheticGnss.Constellation(t0, s,
                    org.ursc.trajectory.gnss.products.GnssOrbitClockSource.ProductClass.FINAL, null);

            // truth trajectory (two-body) at 10 s
            final double step = 10.0;
            final int n = 541;
            final NumericalPropagator tp = new NumericalPropagator(
                    new org.hipparchus.ode.nonstiff.DormandPrince853Integrator(1e-3, 30, 1e-6, 1e-12), att);
            tp.setOrbitType(org.orekit.orbits.OrbitType.CARTESIAN);
            tp.setInitialState(new SpacecraftState(truth));
            final List<SpacecraftState> states = new ArrayList<>();
            tp.setStepHandler(step, states::add);
            tp.propagate(t0.shiftedBy((n - 1) * step));

            final List<AbsoluteDate> dates = new ArrayList<>();
            final List<Vector3D> antenna = new ArrayList<>();
            for (final SpacecraftState st : states) {
                dates.add(st.getDate());
                antenna.add(st.getPosition().add(st.getAttitude().getRotation().applyInverseTo(antBody)));
            }
            final SyntheticGnss.Options opt = new SyntheticGnss.Options();
            opt.wideLaneSlipAt = 300;
            opt.equalSlipAt = 240;
            final List<RinexObsReader.Epoch> epochs = SyntheticGnss.observe(dates, antenna, gps, s, opt);

            // perturbed seed: 30 m, 3 cm/s
            final PVCoordinates pv = truth.getPVCoordinates();
            final CartesianOrbit seed = new CartesianOrbit(new PVCoordinates(
                    pv.getPosition().add(new Vector3D(30, -20, 15)), pv.getVelocity().add(new Vector3D(0.03, -0.02, 0.01))),
                    s.gcrf, t0, MU);
            final RunConfig cfg = new RunConfig();
            final NumericalPropagatorBuilder b = OrekitDynamics.builder(seed, 1000.0, cfg, List.of(), att);
            final OrekitCarrierPhaseOd.Settings set = new OrekitCarrierPhaseOd.Settings();
            set.antennaBody = antBody;
            final OrekitCarrierPhaseOd.Result r = new OrekitCarrierPhaseOd(b, epochs, d0,
                    OrekitDates.fromOrekit(t0.shiftedBy((n - 1) * step)), (t, prns) -> gps, set, s).estimate();

            final PVCoordinates e = r.orbit.getPVCoordinates();
            final double dr = e.getPosition().distance(pv.getPosition());
            final double dv = e.getVelocity().distance(pv.getVelocity());
            final String diag = "dr=" + dr + " m dv=" + dv + " m/s phaseRms=" + r.phaseRms + " codeRms=" + r.codeRms
                    + " arcs=" + r.arcs + " slipsPre=" + r.slipsPreprocessing + " slipsRes=" + r.slipsFromResiduals
                    + " it=" + r.iterations + " vf=" + r.varianceFactor + " notes=" + r.notes;
            System.out.println("[orekit cp-od test] " + diag);
            assertTrue(r.converged, "not converged: " + diag);
            assertTrue(opt.wideLanePrn > 0 && opt.equalPrn > 0, "slips were not injected");
            assertTrue(r.slipsPreprocessing == 1, "wide-lane slip not detected in preprocessing: " + diag);
            assertTrue(r.slipsFromResiduals == 1, "equal L1/L2 slip not found from residuals: " + diag);
            assertTrue(r.phaseRms < 0.005, "phase RMS too large: " + diag);
            assertTrue(dr < 0.02, "position not recovered: " + diag);
            assertTrue(dv < 2e-5, "velocity not recovered: " + diag);
            assertTrue(r.covariance != null && Math.sqrt(r.covariance[0][0]) < 0.05, "covariance: " + diag);
        }
    }
}
