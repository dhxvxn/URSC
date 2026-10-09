package org.ursc.trajectory.app.command;

import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.ProgressStepHandler;
import org.ursc.trajectory.app.StateSeries;
import org.ursc.trajectory.app.term.AsciiChart;
import org.ursc.trajectory.app.term.Ansi;
import org.ursc.trajectory.app.term.Table;
import org.ursc.trajectory.config.ScenarioLoader;
import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.io.CsvEphemerisWriter;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;

/** Propagate a scenario and show a summary table + an in-terminal decay plot. */
public final class PropagateCommand implements Command {

    @Override
    public String name() {
        return "propagate";
    }

    @Override
    public String description() {
        return "propagate a scenario; print a summary and an ASCII plot";
    }

    @Override
    public int run(final Args args) throws Exception {
        final String scenarioFile = args.positional(0);
        if (scenarioFile == null) {
            System.err.println("usage: propagate <scenario.properties> [--plot alt|sma|ecc|none] "
                    + "[--csv out.csv] [--quiet]");
            return 2;
        }
        final boolean quiet = args.has("quiet");
        final String plot = args.get("plot", "alt");

        final ScenarioLoader.Scenario scenario =
                ScenarioLoader.fromFile(Paths.get(scenarioFile)).build();
        final NumericalPropagator propagator = scenario.propagator;

        final EphemerisCollector collector = new EphemerisCollector();
        propagator.addStepHandler(collector);
        propagator.addStepHandler(new ProgressStepHandler(!quiet));
        if (args.has("csv")) {
            propagator.addStepHandler(new CsvEphemerisWriter(Paths.get(args.get("csv", "ephemeris.csv"))));
        }

        System.out.println(Ansi.bold("=== propagate ==="));
        System.out.println("Start : " + scenario.startDate);
        System.out.println("End   : " + scenario.endDate);
        System.out.println("Forces: ");
        for (final ForceModel f : propagator.getForceModels()) {
            System.out.println("  - " + f.getName());
        }

        final long wall = System.nanoTime();
        final SpacecraftState finalState = propagator.propagate(scenario.endDate);
        final double seconds = (System.nanoTime() - wall) / 1.0e9;

        final KeplerianOrbit k0 = new KeplerianOrbit(scenario.initialState.getOrbit());
        final KeplerianOrbit kf = new KeplerianOrbit(finalState.getOrbit());

        final Table table = new Table("Element", "Initial", "Final", "Delta");
        table.addRow("a (km)", km(k0.getA()), km(kf.getA()), delta(kf.getA() - k0.getA(), 1000.0));
        table.addRow("e", f6(k0.getE()), f6(kf.getE()), String.format(Locale.US, "%+.6e", kf.getE() - k0.getE()));
        table.addRow("i (deg)", deg(k0.getI()), deg(kf.getI()), deltaDeg(kf.getI() - k0.getI()));
        table.addRow("RAAN (deg)", deg(k0.getRightAscensionOfAscendingNode()),
                deg(kf.getRightAscensionOfAscendingNode()),
                deltaDeg(kf.getRightAscensionOfAscendingNode() - k0.getRightAscensionOfAscendingNode()));
        table.addRow("argPerigee (deg)", deg(k0.getPerigeeArgument()), deg(kf.getPerigeeArgument()),
                deltaDeg(kf.getPerigeeArgument() - k0.getPerigeeArgument()));
        System.out.println();
        System.out.print(table.render());

        if (!plot.equalsIgnoreCase("none")) {
            final List<SpacecraftState> states = collector.getStates();
            if (states.size() >= 2) {
                final double[] t = StateSeries.timeHours(states);
                final double[] y = StateSeries.series(plot, states);
                System.out.println();
                System.out.print(AsciiChart.render(t, y, AsciiChart.terminalWidth(), 16,
                        StateSeries.seriesLabel(plot) + " vs time",
                        StateSeries.seriesLabel(plot), "time (hours)"));
            }
        }

        System.out.printf(Locale.US, "%nWall-clock: %.2f s%n", seconds);
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

    private static String delta(final double m, final double scale) {
        return String.format(Locale.US, "%+.3f", m / scale);
    }

    private static String deltaDeg(final double rad) {
        return String.format(Locale.US, "%+.4f", Math.toDegrees(rad));
    }
}
