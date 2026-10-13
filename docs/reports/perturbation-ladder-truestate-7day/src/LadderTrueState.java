package org.ursc.trajectory.app.command;

import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Perturbation ladder from the TRUE Sentinel-3A Oct-7 state, NO fitting (single state).
 * Adds each force cumulatively, propagates the exact true state ~8 days, and tabulates the six
 * Cartesian elements (model vs actual) + error over the Oct-15/16 (~8-day) and Oct-7/8 (~1-day)
 * windows. Drag uses a FIXED Cd=2.2 (a single state cannot be fitted).
 *
 *   args[0] = S3A Oct-7/8 POEORB (seed source + near truth)
 *   args[1] = S3A Oct-15/16 POEORB (far truth)
 *   args[2] = space-weather file
 *   args[3] = output directory
 *   args[4] = gravity degree (20)
 *   args[5] = EOP file (finals2000A.all) [optional]
 */
public final class LadderTrueState {

    public static void main(final String[] a) throws Exception {
        final String nearPath = a[0];
        final String farPath = a[1];
        final String swPath = a[2];
        final String outDir = a[3];
        final int gravDeg = Integer.parseInt(a[4]);
        final String eopPath = a.length > 5 && !a[5].isEmpty() ? a[5] : null;

        final List<java.nio.file.Path> files = new ArrayList<>();
        files.add(Paths.get(nearPath)); files.add(Paths.get(farPath));
        final PoeOrbReader.Ephemeris truth = PoeOrbReader.readAll(files);
        final PoeOrbReader.Ephemeris nearEph = PoeOrbReader.read(Paths.get(nearPath));

        final org.ursc.trajectory.frames.EopProvider eop =
                eopPath != null ? OrbitFiles.loadEop(eopPath, truth) : null;
        OrbitFiles.configureFrames(truth, eop);

        final OrbitFiles.Setup setup = new OrbitFiles.Setup();
        setup.gravityDegree = gravDeg;
        setup.spaceWeather = OrbitFiles.loadSpaceWeather(swPath, setup);

        final SpacecraftState seed = OrbitFiles.gcrfState(nearEph, 0, setup.mass);
        final AbsoluteDate epoch = seed.getDate();

        final Map<Long, Integer> tIdx = new HashMap<>();
        for (int i = 0; i < truth.size(); i++) {
            tIdx.put(Math.round(truth.dates.get(i).durationFrom(epoch)), i);
        }
        final AbsoluteDate lastDate = truth.dates.get(truth.size() - 1);
        final double farStart = 100.0 * 3600.0;
        final Frame gcrf = FramesFactory.getGCRF();
        final Frame itrf = FramesFactory.getITRF();

        System.out.printf(Locale.US, "TRUE-state ladder from %s (no fit, fixed Cd=2.2); predict to %s%n",
                fmt(epoch), fmt(lastDate));

        new java.io.File(outDir).mkdirs();
        final PrintWriter sum = new PrintWriter(outDir + "/ladder_summary.csv");
        sum.println("step,label,active_models,rms_1day_m,rms_8day_m,max_8day_m,final_8day_m");

        int step = 0;
        for (final OrbitFiles.ForceSet fs : ladder(gravDeg)) {
            step++;
            final NumericalPropagator prop =
                    (NumericalPropagator) OrbitFiles.config(fs, seed, setup, 2.2, 1.3).build();
            final EphemerisCollector col = new EphemerisCollector();
            prop.setStepHandler(300.0, col);
            prop.propagate(lastDate);

            final String safe = fs.label.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
            final PrintWriter tw = new PrintWriter(String.format(Locale.US, "%s/ladder_%02d_%s.csv", outDir, step, safe));
            tw.println("epoch_utc,days_from_seed,"
                    + "model_x_m,model_y_m,model_z_m,model_vx_ms,model_vy_ms,model_vz_ms,"
                    + "actual_x_m,actual_y_m,actual_z_m,actual_vx_ms,actual_vy_ms,actual_vz_ms,dPos_m,dVel_ms");
            double sqN = 0, sqF = 0, maxF = 0, lastF = 0; int nN = 0, nF = 0;
            for (final SpacecraftState m : col.getStates()) {
                final long off = Math.round(m.getDate().durationFrom(epoch));
                final Integer ti = tIdx.get(off);
                if (ti == null || off == 0) {
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
                if (off < farStart) {
                    sqN += dPos * dPos; nN++;
                } else {
                    sqF += dPos * dPos; maxF = Math.max(maxF, dPos); lastF = dPos; nF++;
                    if (off % 14400 == 0) {
                        tw.printf(Locale.US, "%s,%.3f,%.3f,%.3f,%.3f,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f,%.4f,%.4f,%.4f,%.3f,%.4f%n",
                                fmt(d), off / 86400.0,
                                rM.getX(), rM.getY(), rM.getZ(), vM.getX(), vM.getY(), vM.getZ(),
                                rA.getX(), rA.getY(), rA.getZ(), vA.getX(), vA.getY(), vA.getZ(), dPos, dVel);
                    }
                }
            }
            tw.close();
            final double rms1 = nN > 0 ? Math.sqrt(sqN / nN) : Double.NaN;
            final double rms8 = nF > 0 ? Math.sqrt(sqF / nF) : Double.NaN;
            sum.printf(Locale.US, "%d,%s,%s,%.3f,%.3f,%.3f,%.3f%n", step, fs.label, activeModels(fs, gravDeg),
                    rms1, rms8, maxF, lastF);
            System.out.printf(Locale.US, "%2d. %-28s 1-day RMS=%,12.1f m   8-day RMS=%,14.1f m (%,.1f km)%n",
                    step, fs.label, rms1, rms8, rms8 / 1000.0);
        }
        sum.close();
        System.out.println("tables -> " + outDir);
    }

    private static List<OrbitFiles.ForceSet> ladder(final int gravDeg) {
        final List<OrbitFiles.ForceSet> list = new ArrayList<>();
        OrbitFiles.ForceSet fs = bare();
        fs.gravityDegree = 1; fs.gravityOrder = 0; fs.label = "two-body (central)"; list.add(copy(fs));
        fs.gravityDegree = 2; fs.gravityOrder = 0; fs.label = "+ J2"; list.add(copy(fs));
        fs.gravityDegree = gravDeg; fs.gravityOrder = gravDeg; fs.label = "+ gravity " + gravDeg + "x" + gravDeg; list.add(copy(fs));
        fs.sun = true; fs.moon = true; fs.label = "+ Sun/Moon"; list.add(copy(fs));
        fs.atmosphere = "nrlmsise00"; fs.label = "+ drag (NRLMSISE fixed Cd)"; list.add(copy(fs));
        fs.srp = true; fs.label = "+ SRP"; list.add(copy(fs));
        fs.solidTides = true; fs.label = "+ solid tides"; list.add(copy(fs));
        fs.oceanTides = true; fs.label = "+ ocean tides"; list.add(copy(fs));
        fs.relativity = true; fs.label = "+ relativity"; list.add(copy(fs));
        fs.albedo = true; fs.label = "+ albedo/IR (FULL stack)"; list.add(copy(fs));
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

    private LadderTrueState() {
    }
}
