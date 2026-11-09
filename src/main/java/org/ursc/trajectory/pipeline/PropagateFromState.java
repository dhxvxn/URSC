package org.ursc.trajectory.pipeline;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.ursc.trajectory.io.Oem;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Propagate from a single state (no orbit determination): typed by the user, or picked from
 * an OEM or a POEORB file. Only data up to the initial epoch is used for weather/EOP when the
 * run is in forecast mode.
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
            try (Environment env = Environment.setUp(cfg, anchor)) {
                res.defaultsUsed.addAll(env.defaultsUsed);
                res.warnings.addAll(env.warnings);
                final FrameUtil.PV[] gcrf = new FrameUtil.PV[1];
                final AbsoluteDate[] when = new AbsoluteDate[1];
                seed(is, epoch, gcrf, when, res);
                epoch = when[0];
                res.cutoffUtc = TimeUtil.formatUtc(epoch);
                res.label = "propagation from a single state";
                final SpacecraftState s0 = OdForecastPipeline.state(epoch, new double[] {
                    gcrf[0].r.getX(), gcrf[0].r.getY(), gcrf[0].r.getZ(),
                    gcrf[0].v.getX(), gcrf[0].v.getY(), gcrf[0].v.getZ()}, cfg.spacecraft.massKg);
                pl.stage("Propagating", 0.2);
                final SampledPropagator prop = ForceModelBuilder.build(s0, cfg.spacecraft.cd, cfg.spacecraft.cr,
                        cfg, env, false);
                final EphemerisCollector col = new EphemerisCollector();
                prop.setStepHandler(cfg.forecast.outputStepSeconds, col);
                prop.propagate(epoch.shiftedBy(cfg.forecast.spanDays * 86400.0));
                for (final SpacecraftState s : col.getStates()) {
                    res.states.add(new RunResult.State(s.getDate(), s.getPosition(), s.getVelocity(), null));
                }
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

    private static void seed(final RunConfig.InitialState is, final AbsoluteDate epoch, final FrameUtil.PV[] out,
                             final AbsoluteDate[] when, final RunResult res) throws Exception {
        switch (is.source) {
            case TYPED:
                when[0] = epoch;
                out[0] = FrameUtil.toGcrf(new Vector3D(is.x, is.y, is.z), new Vector3D(is.vx, is.vy, is.vz),
                        is.frame, epoch);
                return;
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
                out[0] = FrameUtil.toGcrf(new Vector3D(best.pv[0], best.pv[1], best.pv[2]),
                        new Vector3D(best.pv[3], best.pv[4], best.pv[5]), frame, when[0]);
                res.log.add("Initial state from OEM " + is.file + " at " + best.epoch + " (" + frame + ")");
                return;
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
                out[0] = FrameUtil.itrfToGcrf(eph.positionItrf.get(idx), eph.velocityItrf.get(idx), when[0]);
                res.log.add("Initial state from POEORB " + is.file + " at " + TimeUtil.formatUtc(when[0]));
            }
        }
    }
}
