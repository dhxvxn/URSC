package org.ursc.trajectory.app.command;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.ProgressStepHandler;
import org.ursc.trajectory.app.StateSeries;
import org.ursc.trajectory.app.term.AsciiChart;
import org.ursc.trajectory.app.term.Ansi;
import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.config.PropagationConfig;
import org.ursc.trajectory.forces.drag.ExponentialAtmosphere;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.io.TLE;
import org.ursc.trajectory.ode.DormandPrince54Integrator;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;

/** Seed a propagation from a TLE and (optionally) propagate it. */
public final class TleCommand implements Command {

    @Override
    public String name() {
        return "tle";
    }

    @Override
    public String description() {
        return "parse a TLE (approximate osculating seed) and optionally propagate it";
    }

    @Override
    public int run(final Args args) throws Exception {
        final String file = args.positional(0);
        if (file == null) {
            System.err.println("usage: tle <tle-file> [--duration SECONDS] [--gravity-degree 10] "
                    + "[--mass 500] [--plot alt|sma|ecc|none] [--quiet]");
            return 2;
        }
        final List<String> lines = Files.readAllLines(Paths.get(file));
        String l1 = null;
        String l2 = null;
        for (final String raw : lines) {
            final String s = raw.strip();
            if (s.startsWith("1 ") && l1 == null) {
                l1 = s;
            } else if (s.startsWith("2 ") && l2 == null) {
                l2 = s;
            }
        }
        if (l1 == null || l2 == null) {
            System.err.println("could not find TLE lines 1 and 2 in " + file);
            return 2;
        }

        final TLE tle = TLE.parse(l1, l2);
        final KeplerianOrbit orbit = tle.toKeplerianOrbit();
        System.out.println(Ansi.bold("=== tle ==="));
        System.out.println("Epoch: " + tle.getEpoch());
        System.out.println("Seed (approx osculating, GCRF): " + orbit);
        System.out.println(Ansi.dim("(note: approximate — no SGP4 mean-to-osculating recovery)"));

        if (!args.has("duration")) {
            return 0;
        }
        final double duration = args.getDouble("duration", 86400.0);
        final int degree = args.getInt("gravity-degree", 10);
        final double mass = args.getDouble("mass", 500.0);
        final boolean quiet = args.has("quiet");
        final String plot = args.get("plot", "alt");

        final SpacecraftState initial = new SpacecraftState(orbit, mass);
        final NumericalPropagator propagator = new PropagationConfig()
                .initialState(initial)
                .integrator(new DormandPrince54Integrator(1e-6, 1e-9, 1e-3, 300.0), 60.0)
                .gravityField(GravityFieldFactory.getEgm96(degree), degree, degree)
                .thirdBody(CelestialBodyFactory.getSun())
                .thirdBody(CelestialBodyFactory.getMoon())
                .drag(new ExponentialAtmosphere(), 2.2, 2.0)
                .solarRadiationPressure(CelestialBodyFactory.getSun(), 1.5, 2.0)
                .outputType(OrbitType.KEPLERIAN)
                .outputStep(Math.max(60.0, duration / 2000.0))
                .build();

        final EphemerisCollector collector = new EphemerisCollector();
        propagator.addStepHandler(collector);
        propagator.addStepHandler(new ProgressStepHandler(!quiet));

        final AbsoluteDate target = tle.getEpoch().shiftedBy(duration);
        final SpacecraftState fin = propagator.propagate(target);
        System.out.println("\nFinal: " + new KeplerianOrbit(fin.getOrbit()));

        if (!plot.equalsIgnoreCase("none") && collector.size() >= 2) {
            final double[] t = StateSeries.timeHours(collector.getStates());
            final double[] y = StateSeries.series(plot, collector.getStates());
            System.out.println();
            System.out.print(AsciiChart.render(t, y, AsciiChart.terminalWidth(), 16,
                    StateSeries.seriesLabel(plot) + " vs time", StateSeries.seriesLabel(plot),
                    "time (hours)"));
        }
        return 0;
    }
}
