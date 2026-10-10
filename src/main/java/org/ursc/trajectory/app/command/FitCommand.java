package org.ursc.trajectory.app.command;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.term.Ansi;
import org.ursc.trajectory.estimation.BatchLeastSquares;
import org.ursc.trajectory.estimation.PositionObservation;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Orbit determination by batch least squares: fit the initial state (and,
 * optionally, the drag coefficient) to the position observations taken from a
 * POEORB precise orbit. Reports pre-fit vs post-fit residual RMS and the
 * estimated parameters.
 */
public final class FitCommand implements Command {

    @Override
    public String name() {
        return "fit";
    }

    @Override
    public String description() {
        return "batch least-squares orbit determination against a POEORB precise orbit";
    }

    @Override
    public int run(final Args args) throws Exception {
        final String file = args.positional(0);
        if (file == null) {
            System.err.println("usage: fit <poeorb.eof> [--estimate-cd] [--hours H] "
                    + "[--sample-step S] [--gravity-degree N] [--mass kg] [--area m2] [--cd Cd] "
                    + "[--cr Cr] [--f107 v] [--f107a v] [--ap v] [--max-iter N]");
            return 2;
        }
        final PoeOrbReader.Ephemeris eph = PoeOrbReader.read(Paths.get(file));
        OrbitFiles.configureFrames(eph);

        final OrbitFiles.Setup setup = new OrbitFiles.Setup();
        setup.mass = args.getDouble("mass", setup.mass);
        setup.dragArea = args.getDouble("area", setup.dragArea);
        setup.srpArea = args.getDouble("area", setup.srpArea);
        setup.cr = args.getDouble("cr", setup.cr);
        setup.gravityDegree = args.getInt("gravity-degree", 20);
        setup.f107 = args.getDouble("f107", setup.f107);
        setup.f107a = args.getDouble("f107a", setup.f107a);
        setup.ap = args.getDouble("ap", setup.ap);
        final double fixedCd = args.getDouble("cd", 2.2);
        final boolean estimateCd = args.has("estimate-cd");
        final int maxIter = args.getInt("max-iter", 10);

        final double recordStep = eph.dates.get(1).durationFrom(eph.dates.get(0));
        final double sampleStep = args.getDouble("sample-step", 60.0);
        final double hours = args.getDouble("hours", 6.0);
        final int stride = Math.max(1, (int) Math.round(sampleStep / recordStep));
        final int lastIndex = Math.min(eph.size() - 1, (int) Math.round(hours * 3600.0 / recordStep));

        final List<Vector3D> truth = OrbitFiles.gcrfPositions(eph);
        final List<PositionObservation> obs = new ArrayList<>();
        for (int i = 0; i <= lastIndex; i += stride) {
            obs.add(new PositionObservation(eph.dates.get(i), truth.get(i)));
        }

        final SpacecraftState s0 = OrbitFiles.gcrfState(eph, 0, setup.mass);
        final Vector3D r0 = s0.getPosition();
        final Vector3D v0 = s0.getVelocity();
        final int p = estimateCd ? 7 : 6;
        final double[] guess = new double[p];
        guess[0] = r0.getX(); guess[1] = r0.getY(); guess[2] = r0.getZ();
        guess[3] = v0.getX(); guess[4] = v0.getY(); guess[5] = v0.getZ();
        if (estimateCd) {
            guess[6] = fixedCd;
        }
        final double[] scale = estimateCd
                ? new double[] {1, 1, 1, 1e-3, 1e-3, 1e-3, 1e-2}
                : new double[] {1, 1, 1, 1e-3, 1e-3, 1e-3};

        final BatchLeastSquares.PropagatorFactory factory = params -> {
            final Vector3D r = new Vector3D(params[0], params[1], params[2]);
            final Vector3D v = new Vector3D(params[3], params[4], params[5]);
            final double cd = estimateCd ? params[6] : fixedCd;
            final SpacecraftState st = new SpacecraftState(
                    new CartesianOrbit(new PVCoordinates(r, v), s0.getFrame(), s0.getDate(),
                            Constants.EARTH_MU), setup.mass);
            return (SampledPropagator) OrbitFiles.fullConfig(st, setup, cd).build();
        };

        System.out.println(Ansi.bold("=== fit (batch least squares) ===") + "  " + file);
        System.out.printf(Locale.US, "Observations: %d positions @ %.0f s over %.1f h%n",
                obs.size(), sampleStep, hours);
        System.out.printf(Locale.US, "Estimating: initial state%s   (gravity %dx%d, full forces)%n",
                estimateCd ? " + drag coefficient" : "", setup.gravityDegree, setup.gravityDegree);

        final long wall = System.nanoTime();
        final BatchLeastSquares.Result res =
                new BatchLeastSquares(factory, obs, scale).estimate(guess, maxIter);
        final double secs = (System.nanoTime() - wall) / 1.0e9;

        System.out.printf(Locale.US, "%nIterations: %d (%s) in %.1f s%n",
                res.iterations, res.converged ? "converged" : "max-iter", secs);
        System.out.printf(Locale.US, "Pre-fit  position RMS: %10.2f m%n", res.preFitRms);
        System.out.printf(Locale.US, "Post-fit position RMS: %10.2f m%n", res.postFitRms);
        System.out.printf(Locale.US, "Improvement: %.1fx%n", res.preFitRms / Math.max(res.postFitRms, 1e-9));
        if (estimateCd) {
            System.out.printf(Locale.US, "Estimated Cd: %.4f (prior %.4f)%n", res.params[6], fixedCd);
        }
        final double dr = Math.sqrt(sq(res.params[0] - guess[0]) + sq(res.params[1] - guess[1])
                + sq(res.params[2] - guess[2]));
        final double dv = Math.sqrt(sq(res.params[3] - guess[3]) + sq(res.params[4] - guess[4])
                + sq(res.params[5] - guess[5]));
        System.out.printf(Locale.US, "State correction from seed: |dr|=%.2f m, |dv|=%.4f m/s%n", dr, dv);
        return 0;
    }

    private static double sq(final double x) {
        return x * x;
    }
}
