package org.ursc.trajectory.app.command;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.term.Ansi;
import org.ursc.trajectory.app.term.AsciiChart;
import org.ursc.trajectory.app.term.Table;
import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.bodies.De440Ephemeris;
import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Predict the orbit from a single state typed in directly — the UTC epoch and the six Cartesian
 * elements (x, y, z, vx, vy, vz) — with no file required. This is the single-state workflow as a
 * self-contained terminal tool: enter the variables (as flags, or interactively with no flags),
 * choose the force model, and get the predicted state out; optionally supply precise-orbit truth
 * file(s) to print the 1-day and 8-day position error.
 *
 * <p>There is no orbit determination (a single state has no arc), so the drag coefficient is fixed,
 * not fitted — the predicted error is the fixed-Cd floor. Pass {@code --eop-file finals2000A.all}
 * for an accurate Earth-fixed frame, and {@code --sw-file} for realistic multi-day drag.</p>
 */
public final class PredictCommand implements Command {

    /** The atmosphere models selectable for drag. */
    static final String ATMO = "none|exponential|harris-priester|nrlmsise00|jb2008";

    @Override
    public String name() {
        return "predict";
    }

    @Override
    public String description() {
        return "predict from a single typed-in state (UTC + x,y,z,vx,vy,vz); optional truth -> error";
    }

    @Override
    public int run(final Args args) throws Exception {
        if (args.has("help") || args.has("h")) {
            usage();
            return 0;
        }

        // ---- gather inputs: flags, or interactive prompts when --utc is absent ----
        final boolean interactive = args.get("utc", null) == null && !args.has("pos");
        final In in = new In(interactive);

        final String utc = in.str(args, "utc", "epoch UTC (YYYY-MM-DDThh:mm:ss)", null);
        final String frame = in.str(args, "frame", "input frame itrf|gcrf", "itrf").toLowerCase(Locale.US);
        final double[] r = in.vec(args, "pos", "position x,y,z (m)", null);
        final double[] v = in.vec(args, "vel", "velocity vx,vy,vz (m/s)", null);
        final double days = in.dbl(args, "days", "prediction horizon (days)", 8.0);

        final OrbitFiles.Setup s = new OrbitFiles.Setup();
        final OrbitFiles.ForceSet fs = new OrbitFiles.ForceSet();
        double cd = 2.2;
        String swFile = args.get("sw-file", null);
        String eopFile = args.get("eop-file", null);
        String de440 = args.get("de440", null);

        // Each force model is an independent toggle: a flag (--sun / --no-sun, ...) or a yes/no
        // prompt. Pick any combination to see which one wins at which horizon.
        final boolean advanced = !interactive || in.yesNo("customise the force model (else full stack)?", true);
        if (advanced) {
            s.mass = in.dbl(args, "mass", "mass (kg)", s.mass);
            s.gravityDegree = in.intv(args, "gravity-degree", "gravity degree/order (saturates ~80)", 70);
            fs.gravityDegree = s.gravityDegree;
            fs.gravityOrder = s.gravityDegree;
            fs.sun = in.toggle(args, "sun", "Sun third-body gravity", true);
            fs.moon = in.toggle(args, "moon", "Moon third-body gravity", true);
            final boolean drag = in.toggle(args, "drag", "atmospheric drag", true);
            if (drag) {
                fs.atmosphere = in.str(args, "atmosphere", "  atmosphere model (" + ATMO + ")", "nrlmsise00");
                cd = in.dbl(args, "cd", "  drag coefficient Cd", cd);
            } else {
                fs.atmosphere = "none";
            }
            fs.srp = in.toggle(args, "srp", "solar radiation pressure", true);
            if (fs.srp) {
                s.cr = in.dbl(args, "cr", "  reflectivity Cr", s.cr);
            }
            fs.albedo = in.toggle(args, "albedo", "Earth albedo / IR", true);
            fs.solidTides = in.toggle(args, "solid-tides", "solid Earth tides", true);
            fs.oceanTides = in.toggle(args, "ocean-tides", "ocean tides", true);
            fs.relativity = in.toggle(args, "relativity", "relativity (Schwarzschild + Lense-Thirring)", true);
            s.boxWing = in.yesNoFlag(args, "box-wing", "CPOD box-wing geometry (else cannonball area)", false);
            if (!s.boxWing && fs.hasDrag()) {
                s.dragArea = in.dbl(args, "area", "  cannonball cross-section A (m^2)", s.dragArea);
            }
            s.srpArea = s.dragArea;
            eopFile = emptyToNull(in.opt(args, "eop-file", "EOP file (finals2000A.all) or blank", eopFile));
            swFile = emptyToNull(in.opt(args, "sw-file", "space-weather file or blank", swFile));
            de440 = emptyToNull(in.opt(args, "de440", "DE440 ephemeris file or blank", de440));
        } else {
            s.gravityDegree = 70;
            fs.gravityDegree = 70;
            fs.gravityOrder = 70;
        }
        s.atmosphere = fs.atmosphere;
        final List<String> truthFiles = pickTruth(args, in, interactive);
        final double step = args.getDouble("step", 300.0);
        in.close();

        // ---- frames, ephemeris, space weather ----
        PoeOrbReader.Ephemeris truth = null;
        if (!truthFiles.isEmpty()) {
            final List<Path> paths = new ArrayList<>();
            for (final String t : truthFiles) {
                paths.add(Paths.get(t));
            }
            truth = PoeOrbReader.readAll(paths);
        }
        final org.ursc.trajectory.frames.EopProvider eop = eopFile != null
                ? OrbitFiles.loadEop(eopFile, truth != null ? truth : singleOsv(utc, r)) : null;
        OrbitFiles.configureFrames(truth != null ? truth : singleOsv(utc, r), eop);

        if (swFile != null) {
            s.spaceWeather = OrbitFiles.loadSpaceWeather(swFile, s);
        }
        if ("jb2008".equalsIgnoreCase(s.atmosphere) || "jb".equalsIgnoreCase(s.atmosphere)) {
            s.jb2008Inputs = org.ursc.trajectory.forces.drag.SolfsmyDtcProvider.bundled();
        }
        if (de440 != null) {
            CelestialBodyFactory.setEphemeris(new De440Ephemeris(Paths.get(de440)));
        }
        try {
            return predict(utc, frame, r, v, days, step, s, fs, cd, truth, de440 != null);
        } finally {
            CelestialBodyFactory.setEphemeris(null);
        }
    }

    private int predict(final String utc, final String frame, final double[] r, final double[] v,
                        final double days, final double step, final OrbitFiles.Setup s,
                        final OrbitFiles.ForceSet fs, final double cd,
                        final PoeOrbReader.Ephemeris truth, final boolean usingDe440) {
        final AbsoluteDate epoch = parseUtc(utc);
        final Frame gcrf = FramesFactory.getGCRF();
        final Frame itrf = FramesFactory.getITRF();

        // build the seed in GCRF from the typed-in state
        final Vector3D rin = new Vector3D(r[0], r[1], r[2]);
        final Vector3D vin = new Vector3D(v[0], v[1], v[2]);
        final Vector3D rG;
        final Vector3D vG;
        if (frame.startsWith("gcrf") || frame.startsWith("eci") || frame.startsWith("j2000")) {
            rG = rin;
            vG = vin;
        } else { // ITRF / earth-fixed (the POEORB convention)
            final Transform i2g = FramesFactory.getTransform(itrf, gcrf, epoch);
            rG = i2g.transformPosition(rin);
            vG = i2g.transformVelocity(vin, rG); // positionDest is the GCRF (destination) position
        }
        final SpacecraftState seed = new SpacecraftState(
                new CartesianOrbit(new PVCoordinates(rG, vG), gcrf, epoch, Constants.EARTH_MU), s.mass);

        final KeplerianOrbit k0 = new KeplerianOrbit(seed.getOrbit());
        final double perigee0 = (k0.getA() * (1 - k0.getE()) - Constants.EARTH_EQUATORIAL_RADIUS) / 1000.0;

        System.out.println(Ansi.bold("=== predict ===") + "  single-state input");
        System.out.println("Epoch : " + epoch + "   input frame: " + frame.toUpperCase(Locale.US));
        System.out.printf(Locale.US, "Seed  : alt(perigee)=%.1f km, a=%.3f km, i=%.4f deg, e=%.6f%n",
                perigee0, k0.getA() / 1000.0, Math.toDegrees(k0.getI()), k0.getE());
        System.out.println("Model : " + activeForces(fs, s, cd, usingDe440));

        final NumericalPropagator prop =
                (NumericalPropagator) OrbitFiles.config(fs, seed, s, cd, s.cr).build();
        final EphemerisCollector col = new EphemerisCollector();
        prop.setStepHandler(step, col);
        final AbsoluteDate end = truth != null
                ? truth.dates.get(truth.size() - 1) : epoch.shiftedBy(days * 86400.0);
        final long wall = System.nanoTime();
        final SpacecraftState finalState = prop.propagate(end);
        final double secs = (System.nanoTime() - wall) / 1.0e9;

        // predicted final state, in the input frame convention (ITRF by default)
        final boolean outItrf = !(frame.startsWith("gcrf") || frame.startsWith("eci") || frame.startsWith("j2000"));
        final Transform g2i = FramesFactory.getTransform(gcrf, outItrf ? itrf : gcrf, finalState.getDate());
        final Vector3D rf = g2i.transformPosition(finalState.getPosition());
        final Vector3D vf = g2i.transformVelocity(finalState.getVelocity(), finalState.getPosition());
        final KeplerianOrbit kf = new KeplerianOrbit(finalState.getOrbit());

        System.out.printf(Locale.US, "%nPropagated %.2f days to %s in %.2f s%n",
                finalState.getDate().durationFrom(epoch) / 86400.0, finalState.getDate(), secs);
        final Table st = new Table("predicted state @ horizon", outItrf ? "ITRF" : "GCRF");
        st.addRow("x (m)", String.format(Locale.US, "%.3f", rf.getX()));
        st.addRow("y (m)", String.format(Locale.US, "%.3f", rf.getY()));
        st.addRow("z (m)", String.format(Locale.US, "%.3f", rf.getZ()));
        st.addRow("vx (m/s)", String.format(Locale.US, "%.6f", vf.getX()));
        st.addRow("vy (m/s)", String.format(Locale.US, "%.6f", vf.getY()));
        st.addRow("vz (m/s)", String.format(Locale.US, "%.6f", vf.getZ()));
        st.addRow("a (km)", String.format(Locale.US, "%.3f", kf.getA() / 1000.0));
        st.addRow("perigee alt (km)",
                String.format(Locale.US, "%.3f", (kf.getA() * (1 - kf.getE()) - Constants.EARTH_EQUATORIAL_RADIUS) / 1000.0));
        System.out.println();
        System.out.print(st.render());

        if (truth != null) {
            score(col, truth, epoch, gcrf, itrf);
        } else {
            System.out.printf(Locale.US, "%n(no truth file given — prediction only. "
                    + "Add --truth <poeorb.eof[,...]> to measure the 1-day / 8-day error.)%n");
        }
        return 0;
    }

    /** Score the prediction against precise-orbit truth: 1-day and 8-day position RMS + max. */
    private void score(final EphemerisCollector col, final PoeOrbReader.Ephemeris truth,
                       final AbsoluteDate epoch, final Frame gcrf, final Frame itrf) {
        final Map<Long, Integer> tIdx = new HashMap<>();
        for (int i = 0; i < truth.size(); i++) {
            tIdx.put(Math.round(truth.dates.get(i).durationFrom(epoch)), i);
        }
        final double split = 100.0 * 3600.0; // < 100 h = near (~1 day), >= = far (~8 day)
        double sqN = 0, sqF = 0, mxN = 0, mxF = 0;
        int nN = 0, nF = 0;
        final List<double[]> curve = new ArrayList<>(); // [hours, error_m]
        for (final SpacecraftState m : col.getStates()) {
            final long off = Math.round(m.getDate().durationFrom(epoch));
            final Integer ti = tIdx.get(off);
            if (ti == null || off == 0) {
                continue;
            }
            final Transform g2i = FramesFactory.getTransform(gcrf, itrf, m.getDate());
            final double d = g2i.transformPosition(m.getPosition())
                    .subtract(truth.positionItrf.get(ti)).getNorm();
            curve.add(new double[] {off / 3600.0, d});
            if (off < split) {
                sqN += d * d; mxN = Math.max(mxN, d); nN++;
            } else {
                sqF += d * d; mxF = Math.max(mxF, d); nF++;
            }
        }
        System.out.println();
        final Table e = new Table("horizon", "points", "RMS (m)", "max (m)");
        if (nN > 0) {
            e.addRow("~1 day", String.valueOf(nN),
                    String.format(Locale.US, "%.2f", Math.sqrt(sqN / nN)),
                    String.format(Locale.US, "%.2f", mxN));
        }
        if (nF > 0) {
            e.addRow("~8 day", String.valueOf(nF),
                    String.format(Locale.US, "%.2f", Math.sqrt(sqF / nF)),
                    String.format(Locale.US, "%.2f", mxF));
        }
        System.out.println(Ansi.bold("error vs precise-orbit truth (ITRF position):"));
        System.out.print(e.render());
        if (curve.size() >= 2) {
            final double[] t = new double[curve.size()];
            final double[] y = new double[curve.size()];
            for (int i = 0; i < curve.size(); i++) {
                t[i] = curve.get(i)[0];
                y[i] = curve.get(i)[1];
            }
            System.out.println();
            System.out.print(AsciiChart.render(t, y, AsciiChart.terminalWidth(), 14,
                    "position error vs time", "error (m)", "time (hours)"));
        }
    }

    // ---------- helpers ----------

    /** One-line summary of the active force models for the run header. */
    private static String activeForces(final OrbitFiles.ForceSet fs, final OrbitFiles.Setup s,
                                       final double cd, final boolean usingDe440) {
        final List<String> on = new ArrayList<>();
        on.add("gravity " + fs.gravityDegree + "x" + fs.gravityDegree);
        if (fs.sun) {
            on.add("Sun");
        }
        if (fs.moon) {
            on.add("Moon");
        }
        if (fs.sun || fs.moon) {
            on.add("(" + (usingDe440 ? "DE440" : "analytic") + ")");
        }
        if (fs.hasDrag()) {
            on.add(String.format(Locale.US, "%s drag (Cd=%.2f, %s)", fs.atmosphere, cd,
                    s.boxWing ? "box-wing" : String.format(Locale.US, "A=%.2f m^2", s.dragArea)));
        } else {
            on.add("no drag");
        }
        if (fs.srp) {
            on.add(String.format(Locale.US, "SRP(Cr=%.2f)", s.cr));
        }
        if (fs.albedo) {
            on.add("albedo");
        }
        if (fs.solidTides) {
            on.add("solid-tides");
        }
        if (fs.oceanTides) {
            on.add("ocean-tides");
        }
        if (fs.relativity) {
            on.add("relativity");
        }
        return String.join(" + ", on);
    }

    private List<String> pickTruth(final Args args, final In in, final boolean interactive) {
        final List<String> flagged = args.getList("truth");
        if (!flagged.isEmpty()) {
            return flagged;
        }
        if (interactive) {
            final String t = in.raw("truth POEORB file(s), comma-separated, or blank for prediction-only");
            if (t != null && !t.isBlank()) {
                final List<String> out = new ArrayList<>();
                for (final String p : t.split(",")) {
                    out.add(p.trim());
                }
                return out;
            }
        }
        return new ArrayList<>();
    }

    private static PoeOrbReader.Ephemeris singleOsv(final String utc, final double[] r) {
        final List<AbsoluteDate> d = new ArrayList<>();
        final List<Vector3D> p = new ArrayList<>();
        final List<Vector3D> v = new ArrayList<>();
        d.add(parseUtc(utc));
        p.add(new Vector3D(r[0], r[1], r[2]));
        v.add(Vector3D.ZERO);
        return PoeOrbReader.of(d, p, v, 0.0);
    }

    /** Parse "YYYY-MM-DDThh:mm:ss(.sss)" or with a space, in UTC. */
    static AbsoluteDate parseUtc(final String utc) {
        final String norm = utc.trim().replace('T', ' ').replace('Z', ' ').trim();
        final String[] dt = norm.split("[ ]+");
        final String[] ymd = dt[0].split("-");
        final String[] hms = dt.length > 1 ? dt[1].split(":") : new String[] {"0", "0", "0"};
        return new AbsoluteDate(
                Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]), Integer.parseInt(ymd[2]),
                Integer.parseInt(hms[0]), hms.length > 1 ? Integer.parseInt(hms[1]) : 0,
                hms.length > 2 ? Double.parseDouble(hms[2]) : 0.0,
                TimeScalesFactory.getUTC());
    }

    private static String emptyToNull(final String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    private void usage() {
        System.out.println("usage: predict [--utc YYYY-MM-DDThh:mm:ss] [--pos x,y,z] [--vel vx,vy,vz]");
        System.out.println("               [--frame itrf|gcrf] [--days N] [--step s]");
        System.out.println();
        System.out.println("  state           --utc, --pos x,y,z (m), --vel vx,vy,vz (m/s), --frame itrf|gcrf");
        System.out.println("  spacecraft      --mass kg, --cd Cd, --cr Cr, --area m2, --box-wing");
        System.out.println("  gravity         --gravity-degree N   (saturates ~80)");
        System.out.println("  force toggles   --[no-]sun --[no-]moon --[no-]drag --[no-]srp");
        System.out.println("                  --[no-]albedo --[no-]solid-tides --[no-]ocean-tides --[no-]relativity");
        System.out.println("  atmosphere      --atmosphere " + ATMO);
        System.out.println("  environment     --eop-file finals2000A.all, --sw-file FILE, --de440 lnxp1990.440");
        System.out.println("  scoring         --truth a.eof[,b.eof]   (prints 1-day / 8-day position error)");
        System.out.println();
        System.out.println("Run with no --utc/--pos to enter every value interactively (force models as yes/no).");
    }

    /** Tiny input helper: a flag when present, else an interactive prompt with a default. */
    private static final class In {
        private final boolean interactive;
        private final BufferedReader br;

        In(final boolean interactive) {
            this.interactive = interactive;
            this.br = interactive
                    ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)) : null;
            if (interactive) {
                System.out.println(Ansi.bold("URSC predict — enter the state (press Enter to accept a default)"));
            }
        }

        String raw(final String prompt) {
            if (!interactive) {
                return null;
            }
            System.out.print("  " + prompt + ": ");
            System.out.flush();
            try {
                final String line = br.readLine();
                return line == null ? "" : line.trim();
            } catch (final Exception e) {
                return "";
            }
        }

        String str(final Args a, final String key, final String prompt, final String def) {
            final String flag = a.get(key, null);
            if (flag != null) {
                return flag;
            }
            if (interactive) {
                final String p = def != null ? prompt + " [" + def + "]" : prompt;
                final String line = raw(p);
                if (line != null && !line.isEmpty()) {
                    return line;
                }
            }
            if (def == null) {
                throw new IllegalArgumentException("missing required value: --" + key);
            }
            return def;
        }

        /** Optional value: flag if present, else an interactive prompt, else the current value (may be null). */
        String opt(final Args a, final String key, final String prompt, final String current) {
            final String flag = a.get(key, null);
            if (flag != null) {
                return flag;
            }
            if (interactive) {
                final String p = current != null ? prompt + " [" + current + "]" : prompt;
                final String line = raw(p);
                if (line != null && !line.isEmpty()) {
                    return line;
                }
            }
            return current;
        }

        double dbl(final Args a, final String key, final String prompt, final double def) {
            return Double.parseDouble(str(a, key, prompt, fmt(def)));
        }

        int intv(final Args a, final String key, final String prompt, final int def) {
            return Integer.parseInt(str(a, key, prompt, String.valueOf(def)));
        }

        double[] vec(final Args a, final String key, final String prompt, final double[] def) {
            final List<String> flag = a.getList(key);
            final List<String> parts;
            if (!flag.isEmpty()) {
                parts = flag;
            } else if (interactive) {
                final String line = raw(prompt + " (comma-separated)");
                parts = new ArrayList<>();
                if (line != null && !line.isBlank()) {
                    for (final String p : line.split(",")) {
                        parts.add(p.trim());
                    }
                }
            } else {
                parts = new ArrayList<>();
            }
            if (parts.size() != 3) {
                if (def != null) {
                    return def;
                }
                throw new IllegalArgumentException("--" + key + " needs three comma-separated numbers");
            }
            return new double[] {
                Double.parseDouble(parts.get(0)), Double.parseDouble(parts.get(1)), Double.parseDouble(parts.get(2))};
        }

        boolean yesNo(final String prompt, final boolean def) {
            if (!interactive) {
                return def;
            }
            final String line = raw(prompt + (def ? " [Y/n]" : " [y/N]"));
            if (line == null || line.isEmpty()) {
                return def;
            }
            return line.toLowerCase(Locale.US).startsWith("y");
        }

        boolean yesNoFlag(final Args a, final String key, final String prompt, final boolean def) {
            if (a.has(key)) {
                return true;
            }
            return interactive ? yesNo(prompt, def) : def;
        }

        /** A force-model toggle: --key on, --no-key off, else an interactive yes/no, else the default. */
        boolean toggle(final Args a, final String key, final String prompt, final boolean def) {
            if (a.has("no-" + key)) {
                return false;
            }
            if (a.has(key)) {
                return true;
            }
            return interactive ? yesNo(prompt + "?", def) : def;
        }

        private static String fmt(final double d) {
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }

        void close() {
            // leave System.in open for the host
        }
    }
}
