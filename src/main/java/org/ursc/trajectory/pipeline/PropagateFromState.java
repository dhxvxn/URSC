package org.ursc.trajectory.pipeline;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.orekit.frames.Frame;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;
import org.ursc.trajectory.io.Oem;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orekit.OrekitDates;
import org.ursc.trajectory.orekit.OrekitDynamics;
import org.ursc.trajectory.orekit.OrekitForecast;
import org.ursc.trajectory.orekit.OrekitSession;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Propagate from a single state (no orbit determination) with the Orekit numerical propagator:
 * typed by the user, or picked from an OEM or a POEORB file. Only data up to the initial epoch is
 * used for weather/EOP when the run is in forecast mode.
 */
public final class PropagateFromState {

    private PropagateFromState() {
    }

    public static RunResult run(final RunConfig cfg, final ProgressListener pl) {
        final long t0 = System.nanoTime();
        final RunResult res = new RunResult();
        res.workflow = RunConfig.Workflow.PROPAGATE.name();
        res.runName = cfg.runName;
        res.objectName = cfg.spacecraft.objectName;
        res.objectId = cfg.spacecraft.objectId;
        try {
            pl.stage("Reading initial state", 0.05);
            final RunConfig.InitialState is = cfg.initialState;
            AbsoluteDate epoch = is.epochUtc == null || is.epochUtc.isBlank() ? null : TimeUtil.parseUtc(is.epochUtc);
            if (is.source == RunConfig.InitialState.Source.TYPED && epoch == null) {
                res.errors.add("A typed initial state needs its UTC epoch");
            }
            if (is.source != RunConfig.InitialState.Source.TYPED && (is.file == null || is.file.isBlank())) {
                res.errors.add("Select the OEM or POEORB file holding the initial state");
            }
            if (cfg.forecast.spanDays <= 0 || cfg.forecast.outputStepSeconds <= 0) {
                res.errors.add("Propagation span and output step must be positive");
            }
            if (!res.errors.isEmpty()) {
                res.status = RunResult.Status.FAILED_VALIDATION;
                return res;
            }
            // Frames must exist before converting the seed; the environment is anchored at the seed epoch.
            final AbsoluteDate anchor = epoch != null ? epoch : peekEpoch(is);
            if (Environment.de440(cfg) == null) {
                res.errors.add("The JPL DE440 ephemeris (lnxp1990.440) was not found; select it as the DE440 file");
                res.status = RunResult.Status.FAILED_VALIDATION;
                return res;
            }
            try (Environment env = Environment.setUp(cfg, anchor);
                 OrekitSession session = OdForecastPipeline.openSession(cfg, env, anchor.shiftedBy(-86400.0),
                         anchor.shiftedBy((cfg.forecast.spanDays + 2.0) * 86400.0))) {
                res.defaultsUsed.addAll(env.defaultsUsed);
                res.warnings.addAll(env.warnings);
                final AbsoluteDate[] when = new AbsoluteDate[1];
                final TimeStampedPVCoordinates pv = seed(is, epoch, when, res, session);
                epoch = when[0];
                res.cutoffUtc = TimeUtil.formatUtc(epoch);
                res.label = "propagation from a single state (Orekit)";
                final SpacecraftState s0 = new SpacecraftState(new CartesianOrbit(pv, session.gcrf,
                        Constants.EIGEN5C_EARTH_MU)).withMass(cfg.spacecraft.massKg);
                pl.stage("Propagating (Orekit)", 0.2);
                final org.orekit.attitudes.AttitudeProvider att = OrekitDynamics.attitude(cfg, env, session);
                final List<String> warnings = new java.util.ArrayList<>();
                final List<RunResult.State> st = OrekitForecast.run(s0, null, List.of(),
                        () -> OrekitDynamics.forces(cfg, env, session, cfg.spacecraft.cd, cfg.spacecraft.cr, warnings),
                        att, s0.getDate(), s0.getDate().shiftedBy(cfg.forecast.spanDays * 86400.0), cfg, pl);
                OrekitForecast.toOutputFrame(st, cfg.forecast.frame, session);
                res.states.addAll(st);
                res.warnings.addAll(new java.util.LinkedHashSet<>(warnings));
                pl.stage("Done", 1.0);
                res.status = RunResult.Status.SUCCESS;
            }
        } catch (final Exception e) {
            res.errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            res.status = RunResult.Status.ERROR;
        } finally {
            res.elapsedSeconds = (System.nanoTime() - t0) / 1e9;
        }
        return res;
    }

    /** First epoch of the file (used to anchor the environment before the exact state is chosen). */
    private static AbsoluteDate peekEpoch(final RunConfig.InitialState is) throws Exception {
        final Path f = Paths.get(is.file);
        if (is.source == RunConfig.InitialState.Source.OEM_FILE) {
            final Oem oem = Oem.read(f);
            return TimeUtil.parseUtc(oem.segments.get(0).states.get(0).epoch);
        }
        return PoeOrbReader.read(f).dates.get(0);
    }

    private static Frame frame(final RunConfig.OutputFrame f, final OrekitSession s) {
        switch (f) {
            case ITRF:
                return s.itrf;
            case EME2000:
                return s.eme2000;
            case GCRF:
            default:
                return s.gcrf;
        }
    }

    /** Initial state in GCRF (Orekit frames). */
    private static TimeStampedPVCoordinates seed(final RunConfig.InitialState is, final AbsoluteDate epoch,
                                                 final AbsoluteDate[] when, final RunResult res,
                                                 final OrekitSession s) throws Exception {
        final Vector3D r;
        final Vector3D v;
        final Frame from;
        switch (is.source) {
            case TYPED:
                when[0] = epoch;
                r = new Vector3D(is.x, is.y, is.z);
                v = new Vector3D(is.vx, is.vy, is.vz);
                from = frame(is.frame, s);
                break;
            case OEM_FILE: {
                final Oem oem = Oem.read(Paths.get(is.file));
                final Oem.Segment seg = oem.segments.get(0);
                if (!"UTC".equalsIgnoreCase(seg.get("TIME_SYSTEM"))) {
                    throw new IllegalArgumentException("only UTC OEM files are supported, found " + seg.get("TIME_SYSTEM"));
                }
                final RunConfig.OutputFrame frame = RunConfig.OutputFrame.valueOf(seg.get("REF_FRAME").toUpperCase());
                Oem.StateLine best = seg.states.get(0);
                if (epoch != null) {
                    double bestDt = Double.MAX_VALUE;
                    for (final Oem.StateLine l : seg.states) {
                        final double dt = Math.abs(TimeUtil.parseUtc(l.epoch).durationFrom(epoch));
                        if (dt < bestDt) {
                            bestDt = dt;
                            best = l;
                        }
                    }
                }
                when[0] = TimeUtil.parseUtc(best.epoch);
                r = new Vector3D(best.pv[0], best.pv[1], best.pv[2]);
                v = new Vector3D(best.pv[3], best.pv[4], best.pv[5]);
                from = frame(frame, s);
                res.log.add("Initial state from OEM " + is.file + " at " + best.epoch + " (" + frame + ")");
                break;
            }
            case POEORB_FILE:
            default: {
                final PoeOrbReader.Ephemeris eph = PoeOrbReader.read(Paths.get(is.file));
                int idx = 0;
                if (epoch != null) {
                    double bestDt = Double.MAX_VALUE;
                    final List<AbsoluteDate> d = eph.dates;
                    for (int i = 0; i < d.size(); i++) {
                        final double dt = Math.abs(d.get(i).durationFrom(epoch));
                        if (dt < bestDt) {
                            bestDt = dt;
                            idx = i;
                        }
                    }
                }
                when[0] = eph.dates.get(idx);
                r = eph.positionItrf.get(idx);
                v = eph.velocityItrf.get(idx);
                from = s.itrf;
                res.log.add("Initial state from POEORB " + is.file + " at " + TimeUtil.formatUtc(when[0]));
            }
        }
        final org.orekit.time.AbsoluteDate t = OrekitDates.toOrekit(when[0]);
        final PVCoordinates g = from.getTransformTo(s.gcrf, t).transformPVCoordinates(
                new PVCoordinates(OrekitDates.toOrekit(r), OrekitDates.toOrekit(v)));
        return new TimeStampedPVCoordinates(t, g);
    }
}
