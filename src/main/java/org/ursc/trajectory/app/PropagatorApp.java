package org.ursc.trajectory.app;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.ursc.trajectory.config.ScenarioLoader;
import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.io.CsvEphemerisWriter;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;

/**
 * Command-line entry point: load a scenario {@code .properties} file, run the
 * propagation, stream the ephemeris to CSV and print a summary.
 *
 * <pre>
 *   java -cp target/classes org.ursc.trajectory.app.PropagatorApp scenario.properties
 * </pre>
 */
public final class PropagatorApp {

    private PropagatorApp() {
    }

    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: PropagatorApp <scenario.properties>");
            System.exit(2);
            return;
        }

        final Path configPath = Paths.get(args[0]);
        final ScenarioLoader.Scenario scenario = ScenarioLoader.fromFile(configPath).build();
        final NumericalPropagator propagator = scenario.propagator;

        final Path outputPath = Paths.get(scenario.outputFile);
        propagator.setStepHandler(scenario.outputStep, new CsvEphemerisWriter(outputPath));

        System.out.println("=== URSC LEO Trajectory Predictor ===");
        System.out.println("Start : " + scenario.startDate);
        System.out.println("End   : " + scenario.endDate);
        System.out.println("Force models:");
        for (final ForceModel f : propagator.getForceModels()) {
            System.out.println("  - " + f.getName());
        }

        final long wallStart = System.nanoTime();
        final SpacecraftState finalState = propagator.propagate(scenario.endDate);
        final double wallSeconds = (System.nanoTime() - wallStart) / 1.0e9;

        final KeplerianOrbit k0 = new KeplerianOrbit(scenario.initialState.getOrbit());
        final KeplerianOrbit kf = new KeplerianOrbit(finalState.getOrbit());

        System.out.println("\nInitial orbit: " + k0);
        System.out.println("Final orbit  : " + kf);
        System.out.printf("Semi-major axis change: %.3f m%n", kf.getA() - k0.getA());
        System.out.printf("Eccentricity change   : %.6e%n", kf.getE() - k0.getE());
        System.out.printf("RAAN change           : %.4f deg%n",
                Math.toDegrees(kf.getRightAscensionOfAscendingNode()
                        - k0.getRightAscensionOfAscendingNode()));
        System.out.println("Ephemeris written to: " + outputPath.toAbsolutePath());
        System.out.printf("Wall-clock time: %.2f s%n", wallSeconds);
    }
}
