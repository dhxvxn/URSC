package org.ursc.trajectory.app.command;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;

import org.ursc.trajectory.analysis.DecayPredictor;
import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.ProgressStepHandler;
import org.ursc.trajectory.app.StateSeries;
import org.ursc.trajectory.app.term.AsciiChart;
import org.ursc.trajectory.app.term.Ansi;
import org.ursc.trajectory.config.ScenarioLoader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.time.AbsoluteDate;

/** Predict orbital decay / re-entry, optionally with Monte-Carlo uncertainty. */
public final class DecayCommand implements Command {

    @Override
    public String name() {
        return "decay";
    }

    @Override
    public String description() {
        return "propagate to re-entry; report decay date (optionally Monte-Carlo)";
    }

    @Override
    public int run(final Args args) throws Exception {
        final String scenarioFile = args.positional(0);
        if (scenarioFile == null) {
            System.err.println("usage: decay <scenario.properties> [--reentry-altitude 120000] "
                    + "[--max-years 25] [--monte-carlo N] [--seed S] [--cd-sigma 0.1] "
                    + "[--f107-range 70,220] [--quiet]");
            return 2;
        }
        final double reentry = args.getDouble("reentry-altitude", 120000.0);
        final double maxYears = args.getDouble("max-years", 25.0);
        final int trials = args.getInt("monte-carlo", 0);
        final boolean quiet = args.has("quiet");

        final Properties base = ScenarioLoader.loadProperties(Paths.get(scenarioFile));

        System.out.println(Ansi.bold("=== decay ==="));
        System.out.printf(Locale.US, "Re-entry altitude: %.1f km   Max window: %.1f years%n",
                reentry / 1000.0, maxYears);

        if (trials <= 0) {
            return runDeterministic(base, reentry, maxYears, quiet);
        }
        return runMonteCarlo(args, base, reentry, maxYears, trials);
    }

    private int runDeterministic(final Properties base, final double reentry,
                                 final double maxYears, final boolean quiet) {
        final ScenarioLoader.Scenario scn = new ScenarioLoader(base).build();
        final NumericalPropagator propagator = scn.propagator;
        propagator.addStepHandler(new ProgressStepHandler(!quiet));

        final DecayPredictor.Result r =
                DecayPredictor.predict(propagator, reentry, maxYears, Constants.JULIAN_DAY);

        if (r.decayed) {
            System.out.printf(Locale.US, "%nDecay: %s  (%.1f days = %.2f years from epoch)%n",
                    r.decayDate, r.daysToDecay, r.daysToDecay / 365.25);
        } else {
            System.out.printf(Locale.US,
                    "%nNo decay within %.1f years (altitude still above re-entry).%n", maxYears);
        }
        if (r.timeDays.length >= 2) {
            System.out.println();
            System.out.print(AsciiChart.render(r.timeDays, r.altitudeKm,
                    AsciiChart.terminalWidth(), 16, "altitude vs time",
                    "altitude (km)", "time (days)"));
        }
        return 0;
    }

    private int runMonteCarlo(final Args args, final Properties base, final double reentry,
                              final double maxYears, final int trials) {
        final long seed = args.getLong("seed", 42);
        final double cdSigma = args.getDouble("cd-sigma", 0.1);
        final List<String> range = args.getList("f107-range");
        final double f107Lo = range.size() == 2 ? Double.parseDouble(range.get(0)) : 70.0;
        final double f107Hi = range.size() == 2 ? Double.parseDouble(range.get(1)) : 220.0;
        final double baseCd = Double.parseDouble(base.getProperty("spacecraft.dragCoefficient", "2.2"));

        final Random rng = new Random(seed);
        final AbsoluteDate epoch = new ScenarioLoader(base).build().startDate;

        final List<Double> decayDays = new ArrayList<>();
        int censored = 0;
        System.out.printf(Locale.US, "Running %d Monte-Carlo trials (seed %d, Cd sigma %.0f%%, "
                + "F10.7 in [%.0f, %.0f])...%n", trials, seed, cdSigma * 100, f107Lo, f107Hi);

        for (int i = 0; i < trials; i++) {
            final Properties p = (Properties) base.clone();
            final double cd = Math.max(0.1, baseCd * (1.0 + cdSigma * rng.nextGaussian()));
            final double f107 = f107Lo + rng.nextDouble() * (f107Hi - f107Lo);
            p.setProperty("spacecraft.dragCoefficient", Double.toString(cd));
            p.setProperty("spaceWeather.f107", Double.toString(f107));
            p.setProperty("spaceWeather.f107a", Double.toString(f107));

            final NumericalPropagator propagator = new ScenarioLoader(p).build().propagator;
            final DecayPredictor.Result r = DecayPredictor.predict(propagator, reentry, maxYears, 0);
            if (r.decayed) {
                decayDays.add(r.daysToDecay);
            } else {
                censored++;
            }
            System.err.printf(Locale.US, "\rtrial %d/%d  (decayed %d, censored %d)   ",
                    i + 1, trials, decayDays.size(), censored);
            System.err.flush();
        }
        System.err.println();

        if (decayDays.isEmpty()) {
            System.out.printf(Locale.US, "No trial decayed within %.1f years.%n", maxYears);
            return 0;
        }
        Collections.sort(decayDays);
        final double p05 = percentile(decayDays, 5);
        final double p50 = percentile(decayDays, 50);
        final double p95 = percentile(decayDays, 95);

        System.out.printf(Locale.US, "%nDecayed %d/%d trials (%d censored at %.0f y)%n",
                decayDays.size(), trials, censored, maxYears);
        System.out.println("Time-to-decay from epoch:");
        System.out.printf(Locale.US, "  5th  pct: %8.1f days (%.2f y)  ->  %s%n",
                p05, p05 / 365.25, epoch.shiftedBy(p05 * Constants.JULIAN_DAY));
        System.out.printf(Locale.US, "  median : %8.1f days (%.2f y)  ->  %s%n",
                p50, p50 / 365.25, epoch.shiftedBy(p50 * Constants.JULIAN_DAY));
        System.out.printf(Locale.US, "  95th pct: %8.1f days (%.2f y)  ->  %s%n",
                p95, p95 / 365.25, epoch.shiftedBy(p95 * Constants.JULIAN_DAY));
        System.out.println();
        System.out.print(histogram(decayDays, 20));
        return 0;
    }

    private static double percentile(final List<Double> sorted, final double pct) {
        if (sorted.isEmpty()) {
            return Double.NaN;
        }
        final double rank = pct / 100.0 * (sorted.size() - 1);
        final int lo = (int) Math.floor(rank);
        final int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sorted.get(lo);
        }
        final double w = rank - lo;
        return sorted.get(lo) * (1 - w) + sorted.get(hi) * w;
    }

    private static String histogram(final List<Double> days, final int bins) {
        final double min = days.get(0);
        final double max = days.get(days.size() - 1);
        final double width = (max - min) / bins == 0 ? 1 : (max - min) / bins;
        final int[] counts = new int[bins];
        for (final double d : days) {
            int b = (int) ((d - min) / width);
            if (b >= bins) {
                b = bins - 1;
            }
            counts[b]++;
        }
        int maxCount = 1;
        for (final int c : counts) {
            maxCount = Math.max(maxCount, c);
        }
        final StringBuilder sb = new StringBuilder(Ansi.bold("decay-time histogram (days)") + "\n");
        final int barMax = 40;
        for (int b = 0; b < bins; b++) {
            final double lo = min + b * width;
            final int len = (int) Math.round((double) counts[b] / maxCount * barMax);
            sb.append(String.format(Locale.US, "%8.0f | %s %d%n",
                    lo, "#".repeat(len), counts[b]));
        }
        return sb.toString();
    }
}
