package org.ursc.trajectory.app.command;

import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.StateSeries;
import org.ursc.trajectory.app.term.Ansi;
import org.ursc.trajectory.app.term.AsciiChart;
import org.ursc.trajectory.app.term.Table;
import org.ursc.trajectory.io.CsvEphemerisWriter;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;

/**
 * Forecast: seed from a single precise state (the first OSV of a POEORB file) and predict
 * the orbit forward with the full force model and (optionally) real space weather, writing
 * the predicted ephemeris to CSV and printing an element summary and an altitude plot.
 *
 * <p>This is the predict-only path: there is no orbit determination (a single state has no
 * arc), so the drag coefficient is fixed rather than fitted. Supply a real space-weather
 * file with {@code --sw-file} for the most realistic drag over multi-day spans.</p>
 */
public final class ForecastCommand implements Command {

    @Override
    public String name() {
        return "forecast";
    }

    @Override
    public String description() {
        return "predict the orbit forward from a single precise state (seed .eof)";
    }

    @Override
    public int run(final Args args) throws Exception {
        final String seedFile = args.positional(0);
        if (seedFile == null) {
            System.err.println("usage: forecast <seed.eof> [--days N] [--sw-file FILE] [--cd Cd] "
                    + "[--gravity-degree N] [--mass kg] [--area m2] [--cr Cr] "
                    + "[--f107 v] [--f107a v] [--ap v] [--eop-file finals2000A.all] "
                    + "[--gravity-file model.gfc] [--step s] [--csv out.csv] [--plot alt|sma|ecc|none]");
            return 2;
        }

        final PoeOrbReader.Ephemeris eph = PoeOrbReader.read(Paths.get(seedFile));

        final OrbitFiles.Setup s = new OrbitFiles.Setup();
        s.mass = args.getDouble("mass", s.mass);
        s.dragArea = args.getDouble("area", s.dragArea);
        s.srpArea = args.getDouble("area", s.srpArea);
        s.cr = args.getDouble("cr", s.cr);
        s.gravityDegree = args.getInt("gravity-degree", s.gravityDegree);
        s.f107 = args.getDouble("f107", s.f107);
        s.f107a = args.getDouble("f107a", s.f107a);
        s.ap = args.getDouble("ap", s.ap);
        final String swFile = args.get("sw-file", null);
        if (swFile != null) {
            s.spaceWeather = OrbitFiles.loadSpaceWeather(swFile, s);
        }
        final String gravFile = args.get("gravity-file", null);
        if (gravFile != null) {
            s.gravityOverride = org.ursc.trajectory.forces.gravity.GravityFieldFactory
                    .loadIcgem(Paths.get(gravFile), s.gravityDegree, s.gravityDegree);
        }
        final String eopFile = args.get("eop-file", null);
        OrbitFiles.configureFrames(eph, eopFile != null ? OrbitFiles.loadEop(eopFile, eph) : null);
        final double cd = args.getDouble("cd", 2.2);
        final double days = args.getDouble("days", 7.0);
        final double step = args.getDouble("step", 300.0);
        final String csv = args.get("csv", "forecast_ephemeris.csv");
        final String plot = args.get("plot", "alt");

        final SpacecraftState seed = OrbitFiles.gcrfState(eph, 0, s.mass);
        final KeplerianOrbit k0 = new KeplerianOrbit(seed.getOrbit());
        final double perigeeAlt0 =
                (k0.getA() * (1 - k0.getE()) - Constants.EARTH_EQUATORIAL_RADIUS) / 1000.0;

        System.out.println(Ansi.bold("=== forecast ===") + "  seed " + seedFile);
        System.out.println("Epoch : " + seed.getDate());
        System.out.printf(Locale.US, "Seed  : alt(perigee)=%.1f km, a=%.3f km, i=%.3f deg, e=%.5f%n",
                perigeeAlt0, k0.getA() / 1000.0, Math.toDegrees(k0.getI()), k0.getE());
        System.out.printf(Locale.US, "Model : gravity %dx%d + Sun/Moon + NRLMSISE drag (Cd=%.1f,A=%.1f) "
                + "+ SRP + albedo/IR + tides + relativity%n", s.gravityDegree, s.gravityDegree, cd, s.dragArea);
        System.out.printf(Locale.US, "Space weather: %s%n", swFile != null
                ? "file " + swFile + " (fallback F10.7=" + s.f107 + ", Ap=" + s.ap + ")"
                : "constant F10.7=" + s.f107 + ", F10.7a=" + s.f107a + ", Ap=" + s.ap);
        System.out.printf(Locale.US, "Forecast span: %.1f days, output every %.0f s -> %s%n",
                days, step, csv);

        final NumericalPropagator prop = (NumericalPropagator) OrbitFiles.fullConfig(seed, s, cd).build();
        final EphemerisCollector col = new EphemerisCollector();
        prop.setStepHandler(step, col);
        prop.addStepHandler(new CsvEphemerisWriter(Paths.get(csv)));

        final long wall = System.nanoTime();
        final SpacecraftState finalState = prop.propagate(seed.getDate().shiftedBy(days * 86400.0));
        final double secs = (System.nanoTime() - wall) / 1.0e9;

        final KeplerianOrbit kf = new KeplerianOrbit(finalState.getOrbit());
        final double perigeeAltF =
                (kf.getA() * (1 - kf.getE()) - Constants.EARTH_EQUATORIAL_RADIUS) / 1000.0;

        final Table table = new Table("Element", "Initial", "Final", "Delta");
        table.addRow("a (km)", km(k0.getA()), km(kf.getA()),
                String.format(Locale.US, "%+.3f", (kf.getA() - k0.getA()) / 1000.0));
        table.addRow("perigee alt (km)", String.format(Locale.US, "%.3f", perigeeAlt0),
                String.format(Locale.US, "%.3f", perigeeAltF),
                String.format(Locale.US, "%+.3f", perigeeAltF - perigeeAlt0));
        table.addRow("e", f6(k0.getE()), f6(kf.getE()),
                String.format(Locale.US, "%+.6e", kf.getE() - k0.getE()));
        table.addRow("i (deg)", deg(k0.getI()), deg(kf.getI()),
                deltaDeg(kf.getI() - k0.getI()));
        table.addRow("RAAN (deg)", deg(k0.getRightAscensionOfAscendingNode()),
                deg(kf.getRightAscensionOfAscendingNode()),
                deltaDeg(kf.getRightAscensionOfAscendingNode() - k0.getRightAscensionOfAscendingNode()));
        System.out.println();
        System.out.print(table.render());

        final double revs = days * 86400.0 / (2.0 * Math.PI * Math.sqrt(
                Math.pow(k0.getA(), 3) / Constants.EARTH_MU));
        System.out.printf(Locale.US, "%nPropagated %.1f days (~%.0f revolutions) in %.2f s%n",
                days, revs, secs);

        if (!plot.equalsIgnoreCase("none")) {
            final List<SpacecraftState> states = col.getStates();
            if (states.size() >= 2) {
                final double[] t = StateSeries.timeHours(states);
                final double[] tDays = new double[t.length];
                for (int i = 0; i < t.length; i++) {
                    tDays[i] = t[i] / 24.0;
                }
                final double[] y = StateSeries.series(plot, states);
                System.out.println();
                System.out.print(AsciiChart.render(tDays, y, AsciiChart.terminalWidth(), 16,
                        StateSeries.seriesLabel(plot) + " vs time (forecast)",
                        StateSeries.seriesLabel(plot), "time (days)"));
            }
        }
        System.out.printf(Locale.US, "%nPredicted ephemeris written to %s "
                + "(score against truth later with: validate <truth.eof>)%n", csv);
        return 0;
    }

    private static String km(final double m) {
        return String.format(Locale.US, "%.3f", m / 1000.0);
    }

    private static String deg(final double rad) {
        return String.format(Locale.US, "%.4f", Math.toDegrees(rad));
    }

    private static String f6(final double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    private static String deltaDeg(final double rad) {
        return String.format(Locale.US, "%+.4f", Math.toDegrees(rad));
    }
}
