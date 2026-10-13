package org.ursc.trajectory.app.command;

import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Perturbation-model combination sweep. For each force-model combination, fit the orbit
 * (state + Cd + Cr) on a short training arc of the real Sentinel-3A precise orbit, predict
 * forward, and score the position error on the HELD-OUT remainder. Ranks combinations by
 * held-out RMS to find the least-error perturbation set.
 *
 *   args[0] = POEORB truth file (ITRF)
 *   args[1] = space-weather file (orekit CSSI txt)
 *   args[2] = output CSV
 *   args[3] = training window hours (e.g. 6)
 *   args[4] = gravity degree for the "full gravity" rung (e.g. 20)
 *
 * In package org.ursc.trajectory.app.command so it can call the package-private
 * BacktestCommand.scoreCombos(...) and build OrbitFiles.ForceSet directly.
 */
public final class ForceSweep {

    public static void main(final String[] a) throws Exception {
        final String truthPath = a[0];
        final String swPath = a[1];
        final String outCsv = a[2];
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

        // training sub-arc = first trainHours of records
        final double cadence = truth.dates.get(1).durationFrom(truth.dates.get(0));
        final int nTrain = (int) Math.round(trainHours * 3600.0 / cadence) + 1;
        final List<AbsoluteDate> td = new ArrayList<>(truth.dates.subList(0, nTrain));
        final List<Vector3D> tr = new ArrayList<>(truth.positionItrf.subList(0, nTrain));
        final List<Vector3D> tv = new ArrayList<>(truth.velocityItrf.subList(0, nTrain));
        final PoeOrbReader.Ephemeris trainEph = PoeOrbReader.of(td, tr, tv, truth.ut1MinusUtc);

        final double totalHours = truth.dates.get(truth.size() - 1)
                .durationFrom(truth.dates.get(0)) / 3600.0;
        System.out.printf(Locale.US,
                "Force sweep: train = first %.1f h (%d obs), held-out = remaining %.1f h%n",
                trainHours, nTrain, totalHours - trainHours);
        System.out.printf(Locale.US, "space weather: real (uploaded); gravity rung = %dx%d%n%n",
                gravDeg, gravDeg);

        // ---- build combination sets ----
        final List<OrbitFiles.ForceSet> combos = new ArrayList<>();
        final List<String> groups = new ArrayList<>();

        // 1) cumulative ladder (marginal contribution of each force)
        OrbitFiles.ForceSet fs = bare();
        fs.gravityDegree = 1; fs.gravityOrder = 0; fs.label = "two-body (central)";
        add(combos, groups, copy(fs), "ladder");
        fs.gravityDegree = 2; fs.gravityOrder = 0; fs.label = "+ J2";
        add(combos, groups, copy(fs), "ladder");
        fs.gravityDegree = gravDeg; fs.gravityOrder = gravDeg; fs.label = "+ gravity " + gravDeg + "x" + gravDeg;
        add(combos, groups, copy(fs), "ladder");
        fs.sun = true; fs.moon = true; fs.label = "+ Sun/Moon";
        add(combos, groups, copy(fs), "ladder");
        fs.atmosphere = "nrlmsise00"; fs.label = "+ drag (NRLMSISE, est. Cd)";
        add(combos, groups, copy(fs), "ladder");
        fs.srp = true; fs.label = "+ SRP (est. Cr)";
        add(combos, groups, copy(fs), "ladder");
        fs.solidTides = true; fs.label = "+ solid tides";
        add(combos, groups, copy(fs), "ladder");
        fs.oceanTides = true; fs.label = "+ ocean tides";
        add(combos, groups, copy(fs), "ladder");
        fs.relativity = true; fs.label = "+ relativity";
        add(combos, groups, copy(fs), "ladder");
        fs.albedo = true; fs.label = "+ albedo/IR (FULL stack)";
        add(combos, groups, copy(fs), "ladder");

        // 2) atmosphere variants at the full stack
        for (final String atm : new String[] {"none", "exponential", "harris-priester", "nrlmsise00"}) {
            final OrbitFiles.ForceSet f = full(gravDeg);
            f.atmosphere = atm;
            f.label = "full, drag=" + atm;
            add(combos, groups, f, "atmos");
        }

        // 3) leave-one-out from the full stack (which single force removal hurts most)
        add(combos, groups, labelled(full(gravDeg), "FULL (reference)"), "leaveout");
        add(combos, groups, drop(full(gravDeg), "drag"), "leaveout");
        add(combos, groups, drop(full(gravDeg), "sunmoon"), "leaveout");
        add(combos, groups, drop(full(gravDeg), "srp"), "leaveout");
        add(combos, groups, drop(full(gravDeg), "solid"), "leaveout");
        add(combos, groups, drop(full(gravDeg), "ocean"), "leaveout");
        add(combos, groups, drop(full(gravDeg), "relativity"), "leaveout");
        add(combos, groups, drop(full(gravDeg), "albedo"), "leaveout");

        // ---- run ----
        final long t0 = System.currentTimeMillis();
        System.out.printf(Locale.US, "EOP: %s%n", eopPath != null ? eopPath : "none (zero polar motion)");
        final double[] rms = BacktestCommand.scoreCombos(trainEph, truth, setup, combos, 2.2, 10, 30.0, eop);
        System.out.printf(Locale.US, "swept %d combinations in %.1f s%n%n",
                combos.size(), (System.currentTimeMillis() - t0) / 1000.0);

        // ---- write CSV + print grouped and ranked ----
        try (PrintWriter w = new PrintWriter(outCsv)) {
            w.println("group,combination,heldout_rms_m");
            for (int i = 0; i < combos.size(); i++) {
                w.printf(Locale.US, "%s,%s,%.6f%n", groups.get(i), combos.get(i).label, rms[i]);
            }
        }

        printGroup("CUMULATIVE LADDER (marginal contribution)", combos, groups, rms, "ladder", true);
        printGroup("ATMOSPHERE MODEL AT FULL STACK", combos, groups, rms, "atmos", false);
        printGroup("LEAVE-ONE-OUT FROM FULL STACK", combos, groups, rms, "leaveout", false);

        // overall winner
        int best = 0;
        for (int i = 1; i < rms.length; i++) {
            if (!Double.isNaN(rms[i]) && (Double.isNaN(rms[best]) || rms[i] < rms[best])) {
                best = i;
            }
        }
        System.out.printf(Locale.US, "%n==> LEAST-ERROR COMBINATION: \"%s\"  (held-out RMS = %.2f m)%n",
                combos.get(best).label, rms[best]);
        System.out.println("    active perturbation models: " + activeModels(combos.get(best), gravDeg));
        System.out.printf(Locale.US, "CSV written: %s%n", outCsv);
    }

    // ---- helpers ----
    private static void add(final List<OrbitFiles.ForceSet> c, final List<String> g,
                            final OrbitFiles.ForceSet fs, final String group) {
        c.add(fs); g.add(group);
    }

    private static void printGroup(final String title, final List<OrbitFiles.ForceSet> combos,
                                   final List<String> groups, final double[] rms, final String group,
                                   final boolean showDelta) {
        System.out.println(title);
        double prev = Double.NaN;
        for (int i = 0; i < combos.size(); i++) {
            if (!groups.get(i).equals(group)) {
                continue;
            }
            final String delta = (showDelta && !Double.isNaN(prev))
                    ? String.format(Locale.US, "   (Δ %+.1f m)", rms[i] - prev) : "";
            System.out.printf(Locale.US, "  %-30s held-out RMS = %10.2f m%s%n",
                    combos.get(i).label, rms[i], delta);
            prev = rms[i];
        }
        System.out.println();
    }

    private static String activeModels(final OrbitFiles.ForceSet fs, final int gravDeg) {
        final List<String> m = new ArrayList<>();
        if (fs.gravityDegree <= 1) {
            m.add("central gravity (two-body)");
        } else if (fs.gravityDegree == 2 && fs.gravityOrder == 0) {
            m.add("gravity J2");
        } else {
            m.add("gravity " + fs.gravityDegree + "x" + fs.gravityOrder + " (EGM96)");
        }
        if (fs.sun) { m.add("Sun third-body"); }
        if (fs.moon) { m.add("Moon third-body"); }
        if (fs.hasDrag()) { m.add("drag (" + fs.atmosphere + ", est. Cd)"); }
        if (fs.srp) { m.add("SRP (est. Cr)"); }
        if (fs.solidTides) { m.add("solid tides"); }
        if (fs.oceanTides) { m.add("ocean tides"); }
        if (fs.relativity) { m.add("relativity"); }
        if (fs.albedo) { m.add("Earth albedo/IR"); }
        return String.join(", ", m);
    }

    private static OrbitFiles.ForceSet bare() {
        final OrbitFiles.ForceSet fs = new OrbitFiles.ForceSet();
        fs.gravityDegree = 1; fs.gravityOrder = 0;
        fs.sun = false; fs.moon = false; fs.atmosphere = "none";
        fs.srp = false; fs.solidTides = false; fs.oceanTides = false;
        fs.relativity = false; fs.albedo = false;
        return fs;
    }

    private static OrbitFiles.ForceSet full(final int gravDeg) {
        final OrbitFiles.ForceSet fs = new OrbitFiles.ForceSet();
        fs.gravityDegree = gravDeg; fs.gravityOrder = gravDeg;
        fs.sun = true; fs.moon = true; fs.atmosphere = "nrlmsise00";
        fs.srp = true; fs.solidTides = true; fs.oceanTides = true;
        fs.relativity = true; fs.albedo = true;
        return fs;
    }

    private static OrbitFiles.ForceSet labelled(final OrbitFiles.ForceSet fs, final String label) {
        fs.label = label;
        return fs;
    }

    private static OrbitFiles.ForceSet drop(final OrbitFiles.ForceSet fs, final String which) {
        switch (which) {
            case "drag": fs.atmosphere = "none"; fs.label = "full - drag"; break;
            case "sunmoon": fs.sun = false; fs.moon = false; fs.label = "full - Sun/Moon"; break;
            case "srp": fs.srp = false; fs.label = "full - SRP"; break;
            case "solid": fs.solidTides = false; fs.label = "full - solid tides"; break;
            case "ocean": fs.oceanTides = false; fs.label = "full - ocean tides"; break;
            case "relativity": fs.relativity = false; fs.label = "full - relativity"; break;
            case "albedo": fs.albedo = false; fs.label = "full - albedo/IR"; break;
            default: break;
        }
        return fs;
    }

    private static OrbitFiles.ForceSet copy(final OrbitFiles.ForceSet s) {
        final OrbitFiles.ForceSet fs = new OrbitFiles.ForceSet();
        fs.label = s.label;
        fs.gravityDegree = s.gravityDegree;
        fs.gravityOrder = s.gravityOrder;
        fs.sun = s.sun; fs.moon = s.moon; fs.atmosphere = s.atmosphere;
        fs.srp = s.srp; fs.solidTides = s.solidTides; fs.oceanTides = s.oceanTides;
        fs.relativity = s.relativity; fs.albedo = s.albedo;
        return fs;
    }

    private ForceSweep() {
    }
}
