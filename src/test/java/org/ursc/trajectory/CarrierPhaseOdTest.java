package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.attitude.AttitudeProvider;
import org.ursc.trajectory.attitude.BodyFrame;
import org.ursc.trajectory.attitude.NominalYawSteering;
import org.ursc.trajectory.estimation.CarrierPhaseOd;
import org.ursc.trajectory.forces.NewtonianAttraction;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.gnss.GpsConstants;
import org.ursc.trajectory.gnss.RinexObsReader;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource;
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
 * Closed-loop test of {@link CarrierPhaseOd}: simulate dual-frequency code and carrier phase from
 * a known LEO orbit to a synthetic GPS constellation (with receiver clock, ambiguities, an antenna
 * offset and an undeclared cycle slip), start from a perturbed state and check the estimator
 * recovers the truth.
 */
class CarrierPhaseOdTest {

    private static final double MU = Constants.EARTH_MU;
    private static final double C = GpsConstants.SPEED_OF_LIGHT;
    private static final double WE = GpsConstants.EARTH_ROTATION_RATE;
    private static final double L1 = C / GpsConstants.F_L1;
    private static final double L2 = C / GpsConstants.F_L2;

    /** 24 circular GPS-like orbits defined directly in the Earth-fixed frame. */
    private static final class FakeGps implements GnssOrbitClockSource {
        final AbsoluteDate t0;

        FakeGps(final AbsoluteDate t0) {
            this.t0 = t0;
        }

        @Override public ProductClass productClass() { return ProductClass.FINAL; }
        @Override public String description() { return "synthetic"; }
        @Override public AbsoluteDate issueTime() { return null; }
        @Override public boolean issueTimeVerified() { return false; }
        @Override public boolean covers(final int prn, final AbsoluteDate t) { return prn >= 1 && prn <= 24; }

        @Override
        public Vector3D positionEcef(final int prn, final AbsoluteDate t) {
            final double a = 26_560e3;
            final double n = Math.sqrt(MU / (a * a * a));
            final int plane = (prn - 1) / 4;
            final double raan = Math.toRadians(60.0 * plane);
            final double inc = Math.toRadians(55.0);
            final double u = Math.toRadians(90.0 * ((prn - 1) % 4) + 15.0 * plane) + n * t.durationFrom(t0);
            final double xo = a * Math.cos(u);
            final double yo = a * Math.sin(u);
            final double x = xo * Math.cos(raan) - yo * Math.cos(inc) * Math.sin(raan);
            final double y = xo * Math.sin(raan) + yo * Math.cos(inc) * Math.cos(raan);
            final double z = yo * Math.sin(inc);
            final double th = WE * t.durationFrom(t0);   // inertial -> Earth-fixed
            return new Vector3D(Math.cos(th) * x + Math.sin(th) * y, -Math.sin(th) * x + Math.cos(th) * y, z);
        }

        @Override
        public double clockBias(final int prn, final AbsoluteDate t) {
            return 1.0e-5 * Math.sin(prn) + 1.0e-11 * prn * t.durationFrom(t0);
        }
    }

    private static SampledPropagator build(final double[] p, final AbsoluteDate t0) {
        final SpacecraftState st = new SpacecraftState(new CartesianOrbit(new PVCoordinates(
                new Vector3D(p[0], p[1], p[2]), new Vector3D(p[3], p[4], p[5])),
                FramesFactory.getGCRF(), t0, MU), 1000.0);
        final NumericalPropagator prop =
                new NumericalPropagator(st, new DormandPrince54Integrator(1e-8, 1e-10, 1e-3, 60.0), 10.0);
        prop.addForceModel(new NewtonianAttraction(MU));
        prop.setOutputType(OrbitType.CARTESIAN);
        return prop;
    }

    private static Vector3D rot(final Vector3D p, final double tau) {
        final double th = WE * tau;
        return new Vector3D(Math.cos(th) * p.getX() + Math.sin(th) * p.getY(),
                -Math.sin(th) * p.getX() + Math.cos(th) * p.getY(), p.getZ());
    }

    @Test
    void recoversOrbitFromCodeAndPhaseWithCycleSlip() {
        final AbsoluteDate t0 = new AbsoluteDate(2024, 10, 15, 0, 0, 0.0, TimeScalesFactory.getGPS());
        final KeplerianOrbit kep = new KeplerianOrbit(Constants.EARTH_EQUATORIAL_RADIUS + 800e3, 0.001,
                Math.toRadians(98.6), 0.3, 1.1, 0.2, PositionAngle.MEAN, FramesFactory.getGCRF(), t0, MU);
        final Vector3D r0 = kep.getPVCoordinates().getPosition();
        final Vector3D v0 = kep.getPVCoordinates().getVelocity();
        final double[] truth = {r0.getX(), r0.getY(), r0.getZ(), v0.getX(), v0.getY(), v0.getZ()};

        final Vector3D antBody = new Vector3D(0.3, -0.2, -0.8);
        final AttitudeProvider att = new NominalYawSteering();
        final FakeGps gps = new FakeGps(t0);

        final double step = 10.0;
        final int n = 541; // 90 minutes at 10 s
        final EphemerisCollector col = new EphemerisCollector();
        final SampledPropagator tp = build(truth, t0);
        tp.setStepHandler(step, col);
        tp.propagate(t0.shiftedBy((n - 1) * step));

        final Random rnd = new Random(7);
        final Map<Integer, double[]> amb = new HashMap<>();   // {N1, N2} cycles per PRN
        final List<RinexObsReader.Epoch> epochs = new ArrayList<>();
        int slipL1 = -1;       // gets a 5-cycle L1 slip (Melbourne-Wubbena detects it)
        int slipEqual = -1;    // gets equal 1-cycle L1+L2 slips (invisible to GF and MW)
        for (int i = 0; i < n; i++) {
            final SpacecraftState s = col.getStates().get(i);
            final AbsoluteDate t = s.getDate();
            final BodyFrame b = att.attitude(s);
            final Vector3D antI = s.getPosition().add(antBody.getX(), b.x()).add(antBody.getY(), b.y())
                    .add(antBody.getZ(), b.z());
            final Vector3D ra = FramesFactory.getTransform(FramesFactory.getGCRF(), FramesFactory.getITRF(), t)
                    .transformPosition(antI);
            final double clk = 150.0 + 0.02 * i;                  // receiver clock (m)
            final Map<Integer, double[]> obs = new HashMap<>();
            final Set<Integer> lli = new HashSet<>();
            for (int prn = 1; prn <= 24; prn++) {
                if (gps.positionEcef(prn, t).subtract(ra).dotProduct(ra) <= 0.0) {
                    continue;                                       // below the local horizon
                }
                double tau = 0.07;
                Vector3D rs = null;
                for (int k = 0; k < 4; k++) {
                    rs = rot(gps.positionEcef(prn, t.shiftedBy(-tau)), tau);
                    tau = rs.distance(ra) / C;
                }
                final double rho = rs.distance(ra);
                final double sum = rs.getNorm() + ra.getNorm();
                final double range = rho - C * gps.clockBias(prn, t.shiftedBy(-tau)) + clk
                        + 2.0 * MU / (C * C) * Math.log((sum + rho) / (sum - rho));
                final boolean known = amb.containsKey(prn);
                final double[] nn = amb.computeIfAbsent(prn,
                        p -> new double[] {Math.round(rnd.nextGaussian() * 1e5), Math.round(rnd.nextGaussian() * 1e5)});
                if (i == 300 && slipL1 < 0 && known && prn != slipEqual) {
                    slipL1 = prn;
                    nn[0] += 5.0;                                   // wide-lane slip: Melbourne-Wubbena
                }
                if (i == 240 && slipEqual < 0 && known) {
                    slipEqual = prn;
                    nn[0] += 1.0;
                    nn[1] += 1.0;
                }
                final double noise = 0.002 * rnd.nextGaussian();
                obs.put(prn, new double[] {
                        range + 0.3 * rnd.nextGaussian(), (range + noise) / L1 + nn[0],
                        range + 0.3 * rnd.nextGaussian(), (range + noise) / L2 + nn[1]});
            }
            epochs.add(RinexObsReader.Epoch.syntheticDual(t, obs, lli));
        }

        final double[] guess = truth.clone();
        guess[0] += 30;
        guess[1] -= 20;
        guess[2] += 15;
        guess[3] += 0.03;
        guess[4] -= 0.02;
        guess[5] += 0.01;
        final double[] scale = {1, 1, 1, 1e-3, 1e-3, 1e-3};

        final CarrierPhaseOd.Settings set = new CarrierPhaseOd.Settings();
        set.antennaBody = antBody;
        set.attitude = att;
        final CarrierPhaseOd od = new CarrierPhaseOd(p -> build(p, t0), scale, epochs, t0,
                t0.shiftedBy((n - 1) * step), (t, prns) -> gps, set);
        final CarrierPhaseOd.Result r = od.estimate(guess);

        final double dr = Math.sqrt(sq(r.params[0] - truth[0]) + sq(r.params[1] - truth[1]) + sq(r.params[2] - truth[2]));
        final double dv = Math.sqrt(sq(r.params[3] - truth[3]) + sq(r.params[4] - truth[4]) + sq(r.params[5] - truth[5]));
        final String diag = "dr=" + dr + " m dv=" + dv + " m/s phaseRms=" + r.phaseRms + " codeRms=" + r.codeRms
                + " arcs=" + r.arcs + " slipsPre=" + r.slipsPreprocessing + " slipsRes=" + r.slipsFromResiduals
                + " it=" + r.iterations + " notes=" + r.notes;
        assertTrue(r.converged, "not converged: " + diag);
        assertTrue(slipL1 > 0 && slipEqual > 0, "slips were not injected");
        assertTrue(r.slipsPreprocessing == 1, "wide-lane slip not detected in preprocessing: " + diag);
        assertTrue(r.slipsFromResiduals == 1, "equal L1/L2 slip not found from residuals: " + diag);
        assertTrue(r.phaseRms < 0.005, "phase RMS too large: " + diag);
        assertTrue(dr < 0.02, "position not recovered: " + diag);
        assertTrue(dv < 2e-5, "velocity not recovered: " + diag);
        assertTrue(r.covariance != null && Math.sqrt(r.covariance[0][0]) < 0.05, "covariance: " + diag);
    }

    private static double sq(final double x) {
        return x * x;
    }
}
