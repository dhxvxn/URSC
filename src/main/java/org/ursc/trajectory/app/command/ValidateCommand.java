package org.ursc.trajectory.app.command;

import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.term.Ansi;
import org.ursc.trajectory.app.term.AsciiChart;
import org.ursc.trajectory.app.term.Table;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;

/**
 * Validate the propagator against a POEORB precise orbit: seed from the first
 * precise state, propagate with the full force model, and report the
 * radial/along/cross position error vs the precise ephemeris.
 */
public final class ValidateCommand implements Command {

    @Override
    public String name() {
        return "validate";
    }

    @Override
    public String description() {
        return "propagate from a POEORB precise orbit and report position error vs truth";
    }

    @Override
    public int run(final Args args) throws Exception {
        final String file = args.positional(0);
        if (file == null) {
            System.err.println("usage: validate <poeorb.eof> [--hours H] [--gravity-degree N] "
                    + "[--mass kg] [--area m2] [--cd Cd] [--cr Cr] [--f107 v] [--f107a v] [--ap v] "
                    + "[--plot total|none]");
            return 2;
        }
        final PoeOrbReader.Ephemeris eph = PoeOrbReader.read(Paths.get(file));
        OrbitFiles.configureFrames(eph);

        final OrbitFiles.Setup s = new OrbitFiles.Setup();
        s.mass = args.getDouble("mass", s.mass);
        s.dragArea = args.getDouble("area", s.dragArea);
        s.srpArea = args.getDouble("area", s.srpArea);
        s.cr = args.getDouble("cr", s.cr);
        s.gravityDegree = args.getInt("gravity-degree", s.gravityDegree);
        s.f107 = args.getDouble("f107", s.f107);
        s.f107a = args.getDouble("f107a", s.f107a);
        s.ap = args.getDouble("ap", s.ap);
        final double cd = args.getDouble("cd", 2.2);

        final double stepS = eph.dates.get(1).durationFrom(eph.dates.get(0)); // record cadence
        final double totalHours = eph.dates.get(eph.size() - 1).durationFrom(eph.dates.get(0)) / 3600.0;
        final double hours = Math.min(args.getDouble("hours", totalHours), totalHours);
        final int maxN = Math.min(eph.size(), (int) Math.round(hours * 3600.0 / stepS) + 1);

        final List<Vector3D> truth = OrbitFiles.gcrfPositions(eph);
        final SpacecraftState s0 = OrbitFiles.gcrfState(eph, 0, s.mass);
        final KeplerianOrbit k0 = new KeplerianOrbit(s0.getOrbit());

        System.out.println(Ansi.bold("=== validate ===") + "  " + file);
        System.out.printf(Locale.US, "Records: %d @ %.0f s  (dUT1=%.4f s)%n", eph.size(), stepS, eph.ut1MinusUtc);
        System.out.printf(Locale.US, "Seed: alt(perigee)=%.1f km, i=%.3f deg, e=%.5f%n",
                (k0.getA() * (1 - k0.getE()) - Constants.EARTH_EQUATORIAL_RADIUS) / 1000.0,
                Math.toDegrees(k0.getI()), k0.getE());
        System.out.printf(Locale.US, "Model: gravity %dx%d + Sun/Moon + NRLMSISE drag (Cd=%.1f,A=%.1f) "
                + "+ SRP + tides + relativity%n", s.gravityDegree, s.gravityDegree, cd, s.dragArea);

        final NumericalPropagator prop = (NumericalPropagator) OrbitFiles.fullConfig(s0, s, cd).build();
        final EphemerisCollector col = new EphemerisCollector();
        prop.setStepHandler(stepS, col);
        final long wall = System.nanoTime();
        prop.propagate(eph.dates.get(maxN - 1));
        final double secs = (System.nanoTime() - wall) / 1.0e9;
        final List<SpacecraftState> comp = col.getStates();

        final int count = Math.min(maxN, comp.size());
        final Table table = new Table("t[h]", "radial[m]", "along[m]", "cross[m]", "total[m]");
        final double[] totalSeries = new double[count];
        double sumSq = 0.0;
        double maxTot = 0.0;
        final int perHour = (int) Math.round(3600.0 / stepS);
        for (int i = 0; i < count; i++) {
            final boolean ahead = i + 1 < truth.size();
            final double[] e = OrbitFiles.rtn(truth.get(i), truth.get(ahead ? i + 1 : i - 1),
                    comp.get(i).getPosition(), ahead);
            totalSeries[i] = e[3];
            sumSq += e[3] * e[3];
            maxTot = Math.max(maxTot, e[3]);
            if (i % perHour == 0) {
                table.addRow(String.format(Locale.US, "%.1f", i * stepS / 3600.0),
                        fmt(e[0]), fmt(e[1]), fmt(e[2]), fmt(e[3]));
            }
        }
        System.out.printf(Locale.US, "Propagated %.1f h in %.2f s%n%n", hours, secs);
        System.out.print(table.render());
        System.out.printf(Locale.US, "%nRMS total = %.1f m   MAX total = %.1f m%n",
                Math.sqrt(sumSq / count), maxTot);

        if (!args.get("plot", "total").equalsIgnoreCase("none") && count >= 2) {
            final double[] th = new double[count];
            for (int i = 0; i < count; i++) {
                th[i] = i * stepS / 3600.0;
            }
            System.out.println();
            System.out.print(AsciiChart.render(th, totalSeries, AsciiChart.terminalWidth(), 14,
                    "position error vs precise orbit", "error (m)", "time (hours)"));
        }
        return 0;
    }

    private static String fmt(final double v) {
        return String.format(Locale.US, "%.1f", v);
    }
}
