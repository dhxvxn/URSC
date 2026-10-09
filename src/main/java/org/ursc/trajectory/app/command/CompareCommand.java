package org.ursc.trajectory.app.command;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.ursc.trajectory.analysis.ModelComparison;
import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.term.Ansi;
import org.ursc.trajectory.app.term.Table;
import org.ursc.trajectory.config.ScenarioLoader;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;

/** Run several model variants of one scenario side by side and tabulate divergence. */
public final class CompareCommand implements Command {

    @Override
    public String name() {
        return "compare";
    }

    @Override
    public String description() {
        return "run model variants side by side; tabulate element & position divergence";
    }

    @Override
    public int run(final Args args) throws Exception {
        final String scenarioFile = args.positional(0);
        if (scenarioFile == null || !args.has("vary")) {
            System.err.println("usage: compare <scenario.properties> --vary <key>=v1,v2,... "
                    + "[--duration SECONDS] [--sample-step SECONDS]");
            System.err.println("  e.g. --vary atmosphere=exponential,harris-priester,nrlmsise00");
            System.err.println("       --vary gravity.order=0,8,20");
            return 2;
        }

        final String vary = args.get("vary", "");
        final int eq = vary.indexOf('=');
        if (eq < 0) {
            System.err.println("--vary must be <key>=v1,v2,...");
            return 2;
        }
        final String rawKey = vary.substring(0, eq).trim();
        final String[] values = vary.substring(eq + 1).split(",");
        final String propKey = aliasToProperty(rawKey);

        final Properties base = ScenarioLoader.loadProperties(Paths.get(scenarioFile));
        final double duration = args.getDouble("duration",
                Double.parseDouble(base.getProperty("propagation.duration", "86400")));
        final double sampleStep = args.getDouble("sample-step",
                Double.parseDouble(base.getProperty("output.step", "60")));

        final List<ModelComparison.Variant> variants = new ArrayList<>();
        for (final String vRaw : values) {
            final String v = vRaw.trim();
            final Properties p = (Properties) base.clone();
            p.setProperty(propKey, v);
            if (propKey.equals("drag.model")) {
                p.setProperty("force.drag", "true");
            }
            final NumericalPropagator propagator = new ScenarioLoader(p).build().propagator;
            variants.add(new ModelComparison.Variant(rawKey + "=" + v, propagator));
        }

        System.out.println(Ansi.bold("=== compare ==="));
        System.out.printf(Locale.US, "Varying %s over %d variants; span %.0f s; reference = first.%n",
                propKey, values.length, duration);

        final List<ModelComparison.Row> rows = ModelComparison.compare(variants, duration, sampleStep);

        final Table table = new Table("Variant", "a (km)", "e", "i (deg)", "RAAN (deg)", "max dPos vs ref (m)");
        for (final ModelComparison.Row row : rows) {
            table.addRow(row.name,
                    String.format(Locale.US, "%.3f", row.finalOrbit.getA() / 1000.0),
                    String.format(Locale.US, "%.6f", row.finalOrbit.getE()),
                    String.format(Locale.US, "%.4f", Math.toDegrees(row.finalOrbit.getI())),
                    String.format(Locale.US, "%.4f", Math.toDegrees(row.finalOrbit.getRightAscensionOfAscendingNode())),
                    row.maxPositionDiffM == 0.0 ? "(reference)"
                            : String.format(Locale.US, "%.1f", row.maxPositionDiffM));
        }
        System.out.println();
        System.out.print(table.render());
        return 0;
    }

    private static String aliasToProperty(final String key) {
        switch (key.toLowerCase()) {
            case "atmosphere":
            case "drag":
                return "drag.model";
            case "gravity":
                return "gravity.model";
            default:
                return key; // treat as a literal scenario property key
        }
    }
}
