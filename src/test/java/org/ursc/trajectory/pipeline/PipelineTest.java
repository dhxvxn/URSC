package org.ursc.trajectory.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Test;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.ursc.trajectory.gnss.RinexObsReader;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource;
import org.ursc.trajectory.io.Oem;
import org.ursc.trajectory.orekit.OrekitDates;
import org.ursc.trajectory.orekit.OrekitDynamics;
import org.ursc.trajectory.orekit.OrekitForecast;
import org.ursc.trajectory.orekit.OrekitSession;
import org.ursc.trajectory.testutil.SyntheticGnss;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Closed-loop test of the OD + forecast pipeline (Orekit engine) on simulated data: a truth LEO
 * orbit propagated with the run's own Orekit force model, a synthetic GPS constellation,
 * dual-frequency code and carrier phase; the pipeline must recover the orbit from the carrier
 * phase, forecast it with a sensible formal covariance and write a valid OEM.
 */
class PipelineTest {

    static RunConfig config(final String cutoff) {
        final RunConfig cfg = new RunConfig();
        cfg.cutoffUtc = cutoff;
        cfg.forces.gravity.degree = 8;
        cfg.forces.gravity.order = 8;
        cfg.forces.solidTides.enabled = false;
        cfg.forces.oceanTides.enabled = false;
        cfg.forces.relativity.enabled = false;
        cfg.forces.albedo.enabled = false;
        cfg.forces.drag.enabled = false;
        cfg.integrator.maxStepSeconds = 60;
        cfg.od.samplingSeconds = 300;
        cfg.od.phaseRateSeconds = 60;
        cfg.od.arcHours = 3;
        cfg.od.antennaOffset = false;
        cfg.forecast.spanDays = 1;
        cfg.forecast.outputStepSeconds = 600;
        cfg.forecast.covarianceEveryNSteps = 6;
        return cfg;
    }

    /** Truth (run force model, optional along-track burn at mid-arc) and its observations at 30 s. */
    static List<RinexObsReader.Epoch> simulate(final RunConfig cfg, final Environment env, final OrekitSession s,
                                               final org.orekit.time.AbsoluteDate t0, final org.orekit.time.AbsoluteDate end,
                                               final SyntheticGnss.Constellation gps, final double burnMps,
                                               final List<RunResult.State> truth) {
        final double r = 7.18e6;
        final double v = Math.sqrt(Constants.EIGEN5C_EARTH_MU / r);
        final double inc = Math.toRadians(98.6);
        SpacecraftState s0 = new SpacecraftState(new CartesianOrbit(new PVCoordinates(new Vector3D(r, 0, 0),
                new Vector3D(0, v * Math.cos(inc), v * Math.sin(inc))), s.gcrf, t0, Constants.EIGEN5C_EARTH_MU))
                .withMass(cfg.spacecraft.massKg);
        final RunConfig c30 = RunConfig.fromJson(cfg.toJson(), new ArrayList<>());
        c30.forecast.outputStepSeconds = 30.0;
        final org.orekit.time.AbsoluteDate burnAt = t0.shiftedBy(0.5 * cfg.od.arcHours * 3600.0);
        final org.orekit.time.AbsoluteDate[][] legs = burnMps == 0.0
                ? new org.orekit.time.AbsoluteDate[][] {{t0, end}}
                : new org.orekit.time.AbsoluteDate[][] {{t0, burnAt}, {burnAt, end}};
        for (final org.orekit.time.AbsoluteDate[] leg : legs) {
            final List<RunResult.State> part = OrekitForecast.run(s0, null, List.of(),
                    () -> OrekitDynamics.forces(c30, env, s, c30.spacecraft.cd, c30.spacecraft.cr, new ArrayList<>()),
                    OrekitDynamics.attitude(c30, env, s), leg[0], leg[1], c30, ProgressListener.NONE);
            if (!truth.isEmpty()) {
                part.remove(0);
            }
            truth.addAll(part);
            final RunResult.State last = truth.get(truth.size() - 1);
            final Vector3D vel = OrekitDates.toOrekit(last.velocity);
            s0 = new SpacecraftState(new CartesianOrbit(new PVCoordinates(OrekitDates.toOrekit(last.position),
                    vel.add(vel.normalize().scalarMultiply(burnMps))), s.gcrf, OrekitDates.toOrekit(last.date),
                    Constants.EIGEN5C_EARTH_MU)).withMass(cfg.spacecraft.massKg);
        }
        final org.orekit.time.AbsoluteDate cutoff = t0.shiftedBy(cfg.od.arcHours * 3600.0);
        final List<org.orekit.time.AbsoluteDate> dates = new ArrayList<>();
        final List<Vector3D> antenna = new ArrayList<>();
        for (final RunResult.State st : truth) {
            final org.orekit.time.AbsoluteDate d = OrekitDates.toOrekit(st.date);
            if (d.durationFrom(cutoff) <= 1.0e-3) {
                dates.add(d);
                antenna.add(OrekitDates.toOrekit(st.position));
            }
        }
        return SyntheticGnss.observe(dates, antenna, gps, s, new SyntheticGnss.Options());
    }

    private static RunResult.State at(final List<RunResult.State> truth, final AbsoluteDate d) {
        for (final RunResult.State st : truth) {
            if (Math.abs(st.date.durationFrom(d)) < 1.0e-3) {
                return st;
            }
        }
        throw new IllegalArgumentException("no truth at " + d);
    }

    @Test
    void flagsAManoeuvreInsideTheArc() throws Exception {
        final RunConfig cfg = config("2024-10-16T06:00:00");
        cfg.forecast.covariance = false;
        cfg.forecast.spanDays = 0.05;
        cfg.od.arcHours = 6;
        final AbsoluteDate cutoff = TimeUtil.parseUtc(cfg.cutoffUtc);
        final AbsoluteDate t0 = cutoff.shiftedBy(-cfg.od.arcHours * 3600.0);
        try (Environment env = Environment.setUp(cfg, cutoff);
             OrekitSession s = OdForecastPipeline.openSession(cfg, env, t0.shiftedBy(-86400), cutoff.shiftedBy(86400 * 2))) {
            final SyntheticGnss.Constellation gps = new SyntheticGnss.Constellation(OrekitDates.toOrekit(t0), s,
                    GnssOrbitClockSource.ProductClass.RAPID, t0.shiftedBy(-86400.0));
            final List<RunResult.State> truth = new ArrayList<>();
            final List<RinexObsReader.Epoch> epochs = simulate(cfg, env, s, OrekitDates.toOrekit(t0),
                    OrekitDates.toOrekit(cutoff.shiftedBy(3600)), gps, 0.05, truth);
            final RunResult res = new RunResult();
            OdForecastPipeline.run(cfg, cutoff, epochs, List.of(gps), env, s, ProgressListener.NONE, res, null);
            assertTrue(!res.flags.isEmpty(), "a 5 cm/s burn mid-arc must be flagged; status " + res.status
                    + " " + res.errors);
            System.out.println("[manoeuvre] " + res.status + " flags: " + res.flags);
        }
    }

    @Test
    void recoversAndForecastsASimulatedOrbit() throws Exception {
        final RunConfig cfg = config("2024-10-16T06:00:00");
        final AbsoluteDate cutoff = TimeUtil.parseUtc(cfg.cutoffUtc);
        final AbsoluteDate t0 = cutoff.shiftedBy(-cfg.od.arcHours * 3600.0);
        try (Environment env = Environment.setUp(cfg, cutoff);
             OrekitSession s = OdForecastPipeline.openSession(cfg, env, t0.shiftedBy(-86400), cutoff.shiftedBy(86400 * 3))) {
            final SyntheticGnss.Constellation gps = new SyntheticGnss.Constellation(OrekitDates.toOrekit(t0), s,
                    GnssOrbitClockSource.ProductClass.RAPID, t0.shiftedBy(-86400.0));
            final List<RunResult.State> truth = new ArrayList<>();
            final List<RinexObsReader.Epoch> epochs = simulate(cfg, env, s, OrekitDates.toOrekit(t0),
                    OrekitDates.toOrekit(cutoff.shiftedBy(86400.0)), gps, 0.0, truth);
            final RunResult res = new RunResult();
            OdForecastPipeline.run(cfg, cutoff, epochs, List.of(gps), env, s, ProgressListener.NONE, res, null);
            assertEquals(RunResult.Status.SUCCESS, res.status, String.join("; ", res.errors));
            assertEquals(37, res.spp.size());
            assertTrue(res.od.converged);
            assertEquals("CARRIER_PHASE", res.od.method);
            assertTrue(res.od.phaseRms < 0.005, "phase RMS " + res.od.phaseRms);
            assertTrue(res.od.phaseObservations > 1000, "phase observations " + res.od.phaseObservations);
            assertTrue(res.label.startsWith("forecast"));
            assertTrue(res.flags.isEmpty(), "clean arc must not be flagged: " + res.flags);

            // ---- forecast vs truth
            final RunResult.State atCutoff = res.states.get(0);
            final RunResult.State atDay = res.states.get(res.states.size() - 1);
            assertEquals(0.0, atCutoff.date.durationFrom(cutoff), 1e-6);
            assertEquals(86400.0, atDay.date.durationFrom(cutoff), 1e-6);
            final double e0 = atCutoff.position.distance(at(truth, cutoff).position);
            final double e1 = atDay.position.distance(at(truth, cutoff.shiftedBy(86400.0)).position);
            assertTrue(e0 < 0.05, "cutoff error " + e0);
            assertTrue(e1 < 2.0, "1-day error " + e1);

            // ---- formal covariance: present, symmetric, growing, plausible vs actual error
            assertNotNull(atDay.covariance);
            final double sig0 = Math.sqrt(atCutoff.covariance[0][0] + atCutoff.covariance[1][1] + atCutoff.covariance[2][2]);
            final double sig1 = Math.sqrt(atDay.covariance[0][0] + atDay.covariance[1][1] + atDay.covariance[2][2]);
            assertTrue(sig1 > sig0, "covariance should grow: " + sig0 + " -> " + sig1);
            assertEquals(atDay.covariance[0][4], atDay.covariance[4][0], 1e-9 * Math.abs(atDay.covariance[0][4]) + 1e-12);
            assertTrue(e1 / sig1 < 30 && e1 / sig1 > 0.01, "error/sigma " + e1 / sig1);
            System.out.printf(java.util.Locale.US,
                    "[pipeline] obs=%d phase rms=%.4f m vf=%.3f | err cutoff=%.3f m (1sig %.3f) | +1d=%.3f m (1sig %.3f)%n",
                    res.od.observations, res.od.phaseRms, res.od.varianceFactor, e0, sig0, e1, sig1);

            // ---- OEM round trip (EME2000)
            final Oem oem = OemExport.toOem(res, cfg);
            final java.nio.file.Path dir = java.nio.file.Paths.get("target", "test-oem");
            java.nio.file.Files.createDirectories(dir);
            oem.write(dir.resolve("pipeline_EME2000.oem"));
            final Oem back = Oem.parse(oem.toKvn());
            final Oem.Segment seg = back.segments.get(0);
            assertEquals("EME2000", seg.get("REF_FRAME"));
            assertEquals("UTC", seg.get("TIME_SYSTEM"));
            assertEquals(res.states.size(), seg.states.size());
            assertEquals(25, seg.covariances.size()); // 145 states (10-min steps over 1 day), every 6th
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
