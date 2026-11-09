package org.ursc.trajectory.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.gnss.GpsBroadcastEphemeris;
import org.ursc.trajectory.gnss.GpsConstants;
import org.ursc.trajectory.gnss.RinexObsReader;
import org.ursc.trajectory.gnss.SppEngine;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource;
import org.ursc.trajectory.io.Oem;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Closed-loop test of the OD + forecast pipeline on simulated data: a truth LEO orbit, a
 * synthetic GPS constellation, noisy pseudoranges with gaps; the pipeline must recover the
 * orbit, forecast it, carry a sensible formal covariance and write a valid OEM.
 */
class PipelineTest {

    private static final double C = GpsConstants.SPEED_OF_LIGHT;

    /** Exact synthetic GPS orbit/clock source (Keplerian broadcast model), Rapid class. */
    static final class SyntheticGps implements GnssOrbitClockSource {
        final Map<Integer, GpsBroadcastEphemeris> sats = new LinkedHashMap<>();
        final AbsoluteDate issue;

        SyntheticGps(final AbsoluteDate ref, final AbsoluteDate issue) {
            this.issue = issue;
            int prn = 1;
            for (int plane = 0; plane < 6; plane++) {
                for (int slot = 0; slot < 4; slot++) {
                    sats.put(prn, new GpsBroadcastEphemeris(prn, ref, ref, 0.0, 1.0e-5 * (prn % 7 - 3), 0.0, 0.0,
                            5153.6, 0.005, slot * Math.PI / 2 + plane * 0.3, 0.0,
                            plane * Math.PI / 3, 0.96, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0));
                    prn++;
                }
            }
        }

        public ProductClass productClass() {
            return ProductClass.RAPID;
        }

        public String description() {
            return "synthetic rapid";
        }

        public AbsoluteDate issueTime() {
            return issue;
        }

        public boolean issueTimeVerified() {
            return true;
        }

        public boolean covers(final int prn, final AbsoluteDate t) {
            return sats.containsKey(prn);
        }

        public Vector3D positionEcef(final int prn, final AbsoluteDate t) {
            return sats.get(prn).positionEcef(t);
        }

        public double clockBias(final int prn, final AbsoluteDate t) {
            return sats.get(prn).clockBias(t);
        }
    }

    static RunConfig config(final String cutoff) {
        final RunConfig cfg = new RunConfig();
        cfg.cutoffUtc = cutoff;
        cfg.forces.gravity.degree = 8;
        cfg.forces.gravity.order = 8;
        cfg.forces.solidTides.enabled = false;
        cfg.forces.oceanTides.enabled = false;
        cfg.forces.relativity.enabled = false;
        cfg.forces.albedo.enabled = false;
        cfg.integrator.absTol = 1e-9;
        cfg.integrator.relTol = 1e-11;
        cfg.integrator.maxStepSeconds = 60;
        cfg.od.samplingSeconds = 300;
        cfg.od.arcHours = 6;
        cfg.forecast.spanDays = 1;
        cfg.forecast.outputStepSeconds = 600;
        cfg.forecast.covarianceEveryNSteps = 6;
        return cfg;
    }

    @Test
    void recoversAndForecastsASimulatedOrbit() throws Exception {
        final RunConfig cfg = config("2024-10-16T06:00:00");
        final AbsoluteDate cutoff = TimeUtil.parseUtc(cfg.cutoffUtc);
        final AbsoluteDate t0 = cutoff.shiftedBy(-6 * 3600.0);
        try (Environment env = Environment.setUp(cfg, cutoff)) {
            // ---- truth: ~800 km, 98.6 deg orbit
            final double r = 7.18e6;
            final double v = Math.sqrt(Constants.EARTH_MU / r);
            final double inc = Math.toRadians(98.6);
            final SpacecraftState s0 = OdForecastPipeline.state(t0,
                    new double[] {r, 0, 0, 0, v * Math.cos(inc), v * Math.sin(inc)}, cfg.spacecraft.massKg);
            final SampledPropagator truthProp = ForceModelBuilder.build(s0, cfg.spacecraft.cd, cfg.spacecraft.cr,
                    cfg, env, false);
            final EphemerisCollector truth = new EphemerisCollector();
            truthProp.setStepHandler(300.0, truth);
            truthProp.propagate(cutoff.shiftedBy(86400.0));
            final Map<Long, SpacecraftState> truthAt = new LinkedHashMap<>();
            for (final SpacecraftState s : truth.getStates()) {
                truthAt.put(Math.round(s.getDate().durationFrom(t0)), s);
            }

            // ---- observations: 1 m Gaussian pseudorange noise, constant receiver clock, gaps
            final SyntheticGps gps = new SyntheticGps(t0.shiftedBy(3 * 3600.0), t0.shiftedBy(-86400.0));
            final Random rnd = new Random(42);
            final List<RinexObsReader.Epoch> epochs = new ArrayList<>();
            for (int k = 0; k <= 72; k++) {
                if (k == 5 || k == 6 || k == 40) {
                    continue; // data gaps
                }
                final AbsoluteDate tRx = t0.shiftedBy(k * 300.0);
                final Vector3D rcv = FramesFactory.getTransform(FramesFactory.getGCRF(), FramesFactory.getITRF(), tRx)
                        .transformPosition(truthAt.get((long) (k * 300)).getPosition());
                final Map<Integer, Double> pr = new LinkedHashMap<>();
                for (final Map.Entry<Integer, GpsBroadcastEphemeris> e : gps.sats.entrySet()) {
                    double tau = 0.07;
                    for (int it = 0; it < 6; it++) {
                        tau = rcv.distance(SppEngine.satellitePositionAtReception(e.getValue(), tRx, tau)) / C;
                    }
                    final Vector3D s = SppEngine.satellitePositionAtReception(e.getValue(), tRx, tau);
                    if (s.subtract(rcv).dotProduct(rcv) <= 0) {
                        continue; // below the receiver's horizon
                    }
                    pr.put(e.getKey(), C * tau + 3000.0 - C * e.getValue().clockBias(tRx.shiftedBy(-tau))
                            + rnd.nextGaussian());
                }
                epochs.add(RinexObsReader.Epoch.synthetic(tRx, pr));
            }

            final RunResult res = new RunResult();
            OdForecastPipeline.run(cfg, cutoff, epochs, List.of(gps), env, ProgressListener.NONE, res);
            assertEquals(RunResult.Status.SUCCESS, res.status, String.join("; ", res.errors));
            assertEquals(70, res.spp.size());
            assertTrue(res.od.converged);
            assertTrue(res.od.weighted);
            assertTrue(res.od.postFitRms < 5.0, "post-fit RMS " + res.od.postFitRms);
            assertEquals(70, res.productUsage.get("synthetic rapid"));
            assertTrue(res.label.startsWith("forecast"));

            // ---- forecast vs truth
            final RunResult.State atCutoff = res.states.get(0);
            final RunResult.State atDay = res.states.get(res.states.size() - 1);
            assertEquals(0.0, atCutoff.date.durationFrom(cutoff), 1e-6);
            assertEquals(86400.0, atDay.date.durationFrom(cutoff), 1e-6);
            final double e0 = atCutoff.position.distance(truthAt.get(21600L).getPosition());
            final double e1 = atDay.position.distance(truthAt.get(21600L + 86400L).getPosition());
            assertTrue(e0 < 20.0, "cutoff error " + e0);
            assertTrue(e1 < 300.0, "1-day error " + e1);

            // ---- formal covariance: present, symmetric, growing, plausible vs actual error
            assertNotNull(atDay.covariance);
            final double sig0 = Math.sqrt(atCutoff.covariance[0][0] + atCutoff.covariance[1][1] + atCutoff.covariance[2][2]);
            final double sig1 = Math.sqrt(atDay.covariance[0][0] + atDay.covariance[1][1] + atDay.covariance[2][2]);
            assertTrue(sig1 > sig0, "covariance should grow: " + sig0 + " -> " + sig1);
            assertEquals(atDay.covariance[0][4], atDay.covariance[4][0], 1e-9 * Math.abs(atDay.covariance[0][4]) + 1e-12);
            assertTrue(e1 / sig1 < 30 && e1 / sig1 > 0.01, "error/sigma " + e1 / sig1);
            System.out.printf(java.util.Locale.US,
                    "[pipeline] fixes=%d postfit=%.2f m vf=%.3f | err cutoff=%.2f m (1sig %.2f) | +1d=%.2f m (1sig %.2f)%n",
                    res.od.observations, res.od.postFitRms, res.od.varianceFactor, e0, sig0, e1, sig1);

            // ---- OEM round trip (EME2000)
            final Oem oem = OemExport.toOem(res, cfg);
            // keep sample OEMs for external validators (e.g. Orekit's CCSDS parser)
            final java.nio.file.Path dir = java.nio.file.Paths.get("target", "test-oem");
            java.nio.file.Files.createDirectories(dir);
            oem.write(dir.resolve("pipeline_EME2000.oem"));
            cfg.forecast.frame = RunConfig.OutputFrame.ITRF;
            OemExport.toOem(res, cfg).write(dir.resolve("pipeline_ITRF.oem"));
            cfg.forecast.frame = RunConfig.OutputFrame.EME2000;
            final Oem back = Oem.parse(oem.toKvn());
            final Oem.Segment seg = back.segments.get(0);
            assertEquals("EME2000", seg.get("REF_FRAME"));
            assertEquals("UTC", seg.get("TIME_SYSTEM"));
            assertEquals(res.states.size(), seg.states.size());
            assertEquals(25, seg.covariances.size()); // 145 states (10-min steps over 1 day), every 6th
            final Oem.StateLine l = seg.states.get(seg.states.size() - 1);
            final FrameUtil.PV g = FrameUtil.toGcrf(new Vector3D(l.pv[0], l.pv[1], l.pv[2]),
                    new Vector3D(l.pv[3], l.pv[4], l.pv[5]), RunConfig.OutputFrame.EME2000, atDay.date);
            assertTrue(g.r.distance(atDay.position) < 1e-5, "OEM position round trip");
            assertTrue(g.v.distance(atDay.velocity) < 1e-8, "OEM velocity round trip");
        }
    }

    @Test
    void reportsEveryMissingMandatoryInput() {
        final RunResult res = OdForecastPipeline.run(new RunConfig(), ProgressListener.NONE);
        assertEquals(RunResult.Status.FAILED_VALIDATION, res.status);
        assertEquals(3, res.errors.size(), String.join("; ", res.errors));
    }

    @Test
    void configSurvivesJsonRoundTrip() {
        final RunConfig c = config("2024-10-16T06:00:00");
        c.inputs.gnssrd.add("/data/gnssrd");
        c.forces.dragModel = RunConfig.DragModel.JB2008;
        c.forces.moon.enabled = false;
        c.forecast.frame = RunConfig.OutputFrame.ITRF;
        final List<String> warnings = new ArrayList<>();
        final RunConfig back = RunConfig.fromJson(c.toJson() .replace("\"runName\"", "\"oldKey\": 1, \"runName\""), warnings);
        assertEquals(c.toJson(), back.toJson());
        assertEquals(1, warnings.size());
        assertEquals("/data/gnssrd", back.inputs.gnssrd.get(0));
        assertEquals(RunConfig.DragModel.JB2008, back.forces.dragModel);
    }
}
