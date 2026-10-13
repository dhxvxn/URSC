package org.ursc.trajectory.app.command;

import org.ursc.trajectory.estimation.BatchLeastSquares;
import org.ursc.trajectory.estimation.PositionObservation;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Incremental perturbation study: starting from two-body, add one force model at a time
 * (cumulative ladder). For EACH combination, determine the orbit (state + Cd + Cr) on the
 * first 6 h of the real precise orbit, predict the held-out remainder, and tabulate the
 * model's six Cartesian elements (x,y,z,vx,vy,vz) vs the actual six elements, plus the
 * position/velocity error. Writes one CSV per combination + a summary.
 *
 *   args[0] = POEORB truth file (ITRF)
 *   args[1] = space-weather file
 *   args[2] = output directory
 *   args[3] = training window hours (e.g. 6)
 *   args[4] = gravity degree for the "+ gravity" rung (e.g. 20)
 *   args[5] = EOP file (finals2000A.all) [optional]
 */
public final class LadderTables {

    public static void main(final String[] a) throws Exception {
        final String truthPath = a[0];
        final String swPath = a[1];
        final String outDir = a[2];
        final double trainHours = Double.parseDouble(a[3]);
        final int gravDeg = Integer.parseInt(a[4]);
        final String eopPath = a.length > 5 && !a[5].isEmpty() ? a[5] : null;

        final PoeOrbReader.Ephemeris truth = PoeOrbReader.read(java.nio.file.Paths.get(truthPath));
        final org.ursc.trajectory.frames.EopProvider eop =
                eopPath != null ? OrbitFiles.loadEop(eopPath, truth) : null;
        OrbitFiles.configureFrames(truth, eop);

        final OrbitFiles.Setup setup = new OrbitFiles.Setup();
        setup.gravityDegree = gravDeg;
        setup.spaceWeather = OrbitFiles.loadSpaceWeather(swPath, setup);

        final AbsoluteDate epoch = truth.dates.get(0);
        final double cadence = truth.dates.get(1).durationFrom(epoch);
        final double trainSpan = trainHours * 3600.0;

        // training observations (GCRF) over the first trainHours, at 30 s
        final List<Vector3D> truthGcrf = OrbitFiles.gcrfPositions(truth);
        final int obsStride = Math.max(1, (int) Math.round(30.0 / cadence));
        final List<PositionObservation> obs = new ArrayList<>();
        for (int i = 0; i < truth.size(); i += obsStride) {
            if (truth.dates.get(i).durationFrom(epoch) > trainSpan + 1.0) {
                break;
            }
            obs.add(new PositionObservation(truth.dates.get(i), truthGcrf.get(i)));
        }
        final SpacecraftState seed = OrbitFiles.gcrfState(truth, 0, setup.mass);

        // truth ITRF state indexed by integer seconds-from-epoch (for lookup)
        final Map<Long, Integer> truthIdx = new HashMap<>();
        for (int i = 0; i < truth.size(); i++) {
            truthIdx.put(Math.round(truth.dates.get(i).durationFrom(epoch)), i);
        }
        final AbsoluteDate lastDate = truth.dates.get(truth.size() - 1);

        new java.io.File(outDir).mkdirs();
        final PrintWriter sum = new PrintWriter(outDir + "/ladder_summary.csv");
        sum.println("step,label,active_models,est_cd,est_cr,heldout_rms_m,heldout_max_m,heldout_final_m");

        final List<OrbitFiles.ForceSet> ladder = ladder(gravDeg);
        int step = 0;
        for (final OrbitFiles.ForceSet fs : ladder) {
            step++;
            final boolean drag = fs.hasDrag();
            final boolean srp = fs.srp;
            final int idxCd = drag ? 6 : -1;
            final int idxCr = srp ? (drag ? 7 : 6) : -1;
            final int p = 6 + (drag ? 1 : 0) + (srp ? 1 : 0);
            final Vector3D r0 = seed.getPosition();
            final Vector3D v0 = seed.getVelocity();
            final double[] guess = new double[p];
            guess[0] = r0.getX(); guess[1] = r0.getY(); guess[2] = r0.getZ();
            guess[3] = v0.getX(); guess[4] = v0.getY(); guess[5] = v0.getZ();
            final double[] scale = new double[p];
            scale[0] = scale[1] = scale[2] = 1.0;
            scale[3] = scale[4] = scale[5] = 1.0e-3;
            if (drag) { guess[idxCd] = 2.2; scale[idxCd] = 1.0e-2; }
            if (srp) { guess[idxCr] = setup.cr; scale[idxCr] = 1.0e-2; }

            final BatchLeastSquares.PropagatorFactory factory = params -> {
                final Vector3D r = new Vector3D(params[0], params[1], params[2]);
                final Vector3D v = new Vector3D(params[3], params[4], params[5]);
                final double cd = drag ? params[idxCd] : 2.2;
                final double cr = srp ? params[idxCr] : setup.cr;
                final SpacecraftState st = new SpacecraftState(
                        new CartesianOrbit(new PVCoordinates(r, v), seed.getFrame(), seed.getDate(),
                                Constants.EARTH_MU), setup.mass);
                return (SampledPropagator) OrbitFiles.config(fs, st, setup, cd, cr).build();
            };

            final BatchLeastSquares.Result fit =
                    new BatchLeastSquares(factory, obs, scale).estimate(guess, 10);

            // predict forward on a 60 s grid
            final NumericalPropagator predictor = (NumericalPropagator) factory.build(fit.params);
            final EphemerisCollector col = new EphemerisCollector();
            predictor.setStepHandler(60.0, col);
            predictor.propagate(lastDate);

            // score held-out + build hourly table
            final String safe = fs.label.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
            final PrintWriter tw = new PrintWriter(String.format(Locale.US, "%s/ladder_%02d_%s.csv", outDir, step, safe));
            tw.println("epoch_utc,hours_from_predict_start,"
                    + "model_x_m,model_y_m,model_z_m,model_vx_ms,model_vy_ms,model_vz_ms,"
                    + "actual_x_m,actual_y_m,actual_z_m,actual_vx_ms,actual_vy_ms,actual_vz_ms,dPos_m,dVel_ms");
            double sumSq = 0, max = 0, last = 0; int n = 0;
            final org.ursc.trajectory.frames.Frame gcrf = FramesFactory.getGCRF();
            final org.ursc.trajectory.frames.Frame itrf = FramesFactory.getITRF();
            for (final SpacecraftState m : col.getStates()) {
                final long off = Math.round(m.getDate().durationFrom(epoch));
                if (off <= Math.round(trainSpan) + 1) {
                    continue; // held-out only
                }
                final Integer ti = truthIdx.get(off);
                if (ti == null) {
                    continue;
                }
                final AbsoluteDate d = m.getDate();
                final Transform g2i = FramesFactory.getTransform(gcrf, itrf, d);
                final Vector3D rM = g2i.transformPosition(m.getPosition());
                final Vector3D vM = g2i.transformVelocity(m.getVelocity(), rM);
                final Vector3D rA = truth.positionItrf.get(ti);
                final Vector3D vA = truth.velocityItrf.get(ti);
                final double dPos = rM.subtract(rA).getNorm();
                final double dVel = vM.subtract(vA).getNorm();
                sumSq += dPos * dPos; max = Math.max(max, dPos); last = dPos; n++;
                if ((off - Math.round(trainSpan)) % 3600 == 0) { // hourly rows
                    tw.printf(Locale.US, "%s,%.3f,%.3f,%.3f,%.3f,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f,%.4f,%.4f,%.4f,%.3f,%.4f%n",
                            fmt(d), (off - trainSpan) / 3600.0,
                            rM.getX(), rM.getY(), rM.getZ(), vM.getX(), vM.getY(), vM.getZ(),
                            rA.getX(), rA.getY(), rA.getZ(), vA.getX(), vA.getY(), vA.getZ(), dPos, dVel);
                }
            }
            tw.close();
            final double rms = Math.sqrt(sumSq / n);
            final double cd = drag ? fit.params[idxCd] : Double.NaN;
            final double cr = srp ? fit.params[idxCr] : Double.NaN;
            sum.printf(Locale.US, "%d,%s,%s,%s,%s,%.3f,%.3f,%.3f%n", step, fs.label, activeModels(fs, gravDeg),
                    drag ? String.format(Locale.US, "%.3f", cd) : "-",
                    srp ? String.format(Locale.US, "%.3f", cr) : "-", rms, max, last);
            System.out.printf(Locale.US, "%2d. %-28s held-out RMS=%10.2f m  max=%9.2f  final=%9.2f  Cd=%s Cr=%s%n",
                    step, fs.label, rms, max, last,
                    drag ? String.format(Locale.US, "%.2f", cd) : "-",
                    srp ? String.format(Locale.US, "%.2f", cr) : "-");
        }
        sum.close();
        System.out.println("tables written to " + outDir);
    }

    private static List<OrbitFiles.ForceSet> ladder(final int gravDeg) {
        final List<OrbitFiles.ForceSet> list = new ArrayList<>();
        OrbitFiles.ForceSet fs = bare();
        fs.gravityDegree = 1; fs.gravityOrder = 0; fs.label = "two-body (central)";
        list.add(copy(fs));
        fs.gravityDegree = 2; fs.gravityOrder = 0; fs.label = "+ J2";
        list.add(copy(fs));
        fs.gravityDegree = gravDeg; fs.gravityOrder = gravDeg; fs.label = "+ gravity " + gravDeg + "x" + gravDeg;
        list.add(copy(fs));
        fs.sun = true; fs.moon = true; fs.label = "+ Sun/Moon";
        list.add(copy(fs));
        fs.atmosphere = "nrlmsise00"; fs.label = "+ drag (NRLMSISE est. Cd)";
        list.add(copy(fs));
        fs.srp = true; fs.label = "+ SRP (est. Cr)";
        list.add(copy(fs));
        fs.solidTides = true; fs.label = "+ solid tides";
        list.add(copy(fs));
        fs.oceanTides = true; fs.label = "+ ocean tides";
        list.add(copy(fs));
        fs.relativity = true; fs.label = "+ relativity";
        list.add(copy(fs));
        fs.albedo = true; fs.label = "+ albedo/IR (FULL stack)";
        list.add(copy(fs));
        return list;
    }

    private static String activeModels(final OrbitFiles.ForceSet fs, final int gravDeg) {
        final List<String> m = new ArrayList<>();
        if (fs.gravityDegree <= 1) { m.add("central gravity"); }
        else if (fs.gravityDegree == 2 && fs.gravityOrder == 0) { m.add("gravity J2"); }
        else { m.add("gravity " + fs.gravityDegree + "x" + fs.gravityOrder); }
        if (fs.sun) { m.add("Sun"); }
        if (fs.moon) { m.add("Moon"); }
        if (fs.hasDrag()) { m.add("drag(" + fs.atmosphere + ")"); }
        if (fs.srp) { m.add("SRP"); }
        if (fs.solidTides) { m.add("solid tides"); }
        if (fs.oceanTides) { m.add("ocean tides"); }
        if (fs.relativity) { m.add("relativity"); }
        if (fs.albedo) { m.add("albedo/IR"); }
        return String.join(" + ", m);
    }

    private static OrbitFiles.ForceSet bare() {
        final OrbitFiles.ForceSet fs = new OrbitFiles.ForceSet();
        fs.gravityDegree = 1; fs.gravityOrder = 0;
        fs.sun = false; fs.moon = false; fs.atmosphere = "none";
        fs.srp = false; fs.solidTides = false; fs.oceanTides = false;
        fs.relativity = false; fs.albedo = false;
        return fs;
    }

    private static OrbitFiles.ForceSet copy(final OrbitFiles.ForceSet s) {
        final OrbitFiles.ForceSet fs = new OrbitFiles.ForceSet();
        fs.label = s.label; fs.gravityDegree = s.gravityDegree; fs.gravityOrder = s.gravityOrder;
        fs.sun = s.sun; fs.moon = s.moon; fs.atmosphere = s.atmosphere; fs.srp = s.srp;
        fs.solidTides = s.solidTides; fs.oceanTides = s.oceanTides; fs.relativity = s.relativity;
        fs.albedo = s.albedo;
        return fs;
    }

    private static String fmt(final AbsoluteDate d) {
        final double[] c = d.getComponents(TimeScalesFactory.getUTC());
        return String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d:%06.3f",
                (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], c[5]);
    }

    private LadderTables() {
    }
}
