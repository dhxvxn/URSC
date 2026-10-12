package org.ursc.trajectory.app.command;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.term.Ansi;
import org.ursc.trajectory.app.term.AsciiChart;
import org.ursc.trajectory.app.term.Table;
import org.ursc.trajectory.estimation.BatchLeastSquares;
import org.ursc.trajectory.estimation.PositionObservation;
import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Force-model selection by out-of-sample ("hold-out") validation: determine the orbit from
 * a short training arc (e.g. one day of POEORB), predict forward over the remaining days,
 * and score the prediction against the held-out precise orbit. A sweep over force-model
 * combinations (an incremental ladder, or atmosphere variants) is ranked by held-out
 * prediction error, so the combination that predicts best is identified quantitatively.
 *
 * <p>Each combination's orbit determination estimates the 6-element state plus the drag
 * coefficient (when drag is active) and the SRP reflectivity (when SRP is active), so every
 * model gets a fair fit.</p>
 */
public final class BacktestCommand implements Command {

    @Override
    public String name() {
        return "backtest";
    }

    @Override
    public String description() {
        return "fit a training arc, predict the held-out days, rank force-model combinations";
    }

    @Override
    public int run(final Args args) throws Exception {
        final String trainFile = args.positional(0);
        if (trainFile == null || args.positionalCount() < 2) {
            System.err.println("usage: backtest <train.eof> <truth1.eof> [truth2.eof ...] "
                    + "[--sweep ladder|atmos|full] [--sw-file SW.csv] [--gravity-degree N] "
                    + "[--eop-file finals2000A.all] [--gravity-file model.gfc] "
                    + "[--sample-step S] [--mass kg] [--area m2] [--cd Cd] [--cr Cr] "
                    + "[--f107 v] [--f107a v] [--ap v] [--max-iter N]");
            return 2;
        }

        // all files (training + truth) form the full precise-orbit timeline
        final List<Path> allFiles = new ArrayList<>();
        for (final String f : args.allPositionals()) {
            allFiles.add(Paths.get(f));
        }
        final PoeOrbReader.Ephemeris trainEph = PoeOrbReader.read(Paths.get(trainFile));
        final PoeOrbReader.Ephemeris truthEph = PoeOrbReader.readAll(allFiles);
        final String eopFile = args.get("eop-file", null);
        OrbitFiles.configureFrames(truthEph,
                eopFile != null ? OrbitFiles.loadEop(eopFile, truthEph) : null);

        final OrbitFiles.Setup setup = new OrbitFiles.Setup();
        setup.mass = args.getDouble("mass", setup.mass);
        setup.dragArea = args.getDouble("area", setup.dragArea);
        setup.srpArea = args.getDouble("area", setup.srpArea);
        setup.cr = args.getDouble("cr", setup.cr);
        setup.f107 = args.getDouble("f107", setup.f107);
        setup.f107a = args.getDouble("f107a", setup.f107a);
        setup.ap = args.getDouble("ap", setup.ap);
        final String swFile = args.get("sw-file", null);
        if (swFile != null) {
            setup.spaceWeather = OrbitFiles.loadSpaceWeather(swFile, setup);
        }
        final int gravDeg = args.getInt("gravity-degree", 20);
        final String gravFile = args.get("gravity-file", null);
        if (gravFile != null) {
            setup.gravityOverride = org.ursc.trajectory.forces.gravity.GravityFieldFactory
                    .loadIcgem(Paths.get(gravFile), gravDeg, gravDeg);
        }
        final double cdGuess = args.getDouble("cd", 2.2);
        final double obsStep = args.getDouble("sample-step", 120.0);
        final int maxIter = args.getInt("max-iter", 8);
        final String sweep = args.get("sweep", "ladder");

        // timeline geometry
        final AbsoluteDate epoch = truthEph.dates.get(0);
        final double cadence = truthEph.dates.get(1).durationFrom(epoch);
        final int truthStride = Math.max(1, (int) Math.round(60.0 / cadence)); // ~60 s scoring grid
        final double scoreStep = truthStride * cadence;
        final double trainSpan = trainEph.dates.get(trainEph.size() - 1).durationFrom(trainEph.dates.get(0));
        final double totalSpan = truthEph.dates.get(truthEph.size() - 1).durationFrom(epoch);

        System.out.println(Ansi.bold("=== backtest (fit-then-predict force-model selection) ==="));
        System.out.printf(Locale.US, "Training arc: %.2f h (%s)%n", trainSpan / 3600.0, trainFile);
        System.out.printf(Locale.US, "Held-out truth: %.2f days total, %d files, %.0f s cadence%n",
                totalSpan / 86400.0, allFiles.size(), cadence);
        System.out.printf(Locale.US, "Space weather: %s%n", swFile != null ? "file " + swFile
                : "constant F10.7=" + setup.f107 + ", Ap=" + setup.ap);

        // training observations (GCRF positions) sampled from the training file
        final List<Vector3D> trainGcrf = OrbitFiles.gcrfPositions(trainEph);
        final double trainCadence = trainEph.dates.get(1).durationFrom(trainEph.dates.get(0));
        final int obsStride = Math.max(1, (int) Math.round(obsStep / trainCadence));
        final List<PositionObservation> obs = new ArrayList<>();
        for (int i = 0; i < trainEph.size(); i += obsStride) {
            obs.add(new PositionObservation(trainEph.dates.get(i), trainGcrf.get(i)));
        }
        final SpacecraftState seed = OrbitFiles.gcrfState(trainEph, 0, setup.mass);

        // pre-compute the held-out truth on the scoring grid (GCRF)
        final List<ScorePoint> grid = buildGrid(truthEph, truthStride, epoch, scoreStep, trainSpan);

        // build the sweep
        final List<OrbitFiles.ForceSet> combos = buildSweep(sweep, gravDeg);

        final List<Result> results = new ArrayList<>();
        double[] baselineSeries = null;
        double[] timeAxis = null;
        for (final OrbitFiles.ForceSet fs : combos) {
            final Result r = evaluate(fs, seed, setup, obs, cdGuess, maxIter, grid, epoch, scoreStep);
            results.add(r);
            if (fs.label.startsWith("two-body")) {
                baselineSeries = r.series;
                timeAxis = r.timeAxisDays;
            }
            System.out.printf(Locale.US, "  %-26s held-out RMS = %10.1f m%n", fs.label, r.heldRms);
        }

        // rank by held-out RMS
        results.sort((a, b) -> Double.compare(a.heldRms, b.heldRms));
        final Table table = new Table("rank", "force model", "est.Cd", "est.Cr",
                "fit RMS[m]", "pred RMS[m]", "pred max[m]", "final[m]");
        for (int i = 0; i < results.size(); i++) {
            final Result r = results.get(i);
            table.addRow(Integer.toString(i + 1), r.label,
                    r.cd > 0 ? String.format(Locale.US, "%.3f", r.cd) : "-",
                    r.cr > 0 ? String.format(Locale.US, "%.3f", r.cr) : "-",
                    String.format(Locale.US, "%.1f", r.fitRms),
                    String.format(Locale.US, "%.1f", r.heldRms),
                    String.format(Locale.US, "%.1f", r.heldMax),
                    String.format(Locale.US, "%.1f", r.heldFinal));
        }
        System.out.println();
        System.out.println(Ansi.bold("Ranked by held-out (out-of-sample) prediction error:"));
        System.out.print(table.render());

        final Result best = results.get(0);
        System.out.printf(Locale.US, "%nBest: %s  (held-out RMS %.1f m over %.1f days)%n",
                Ansi.green(best.label), best.heldRms, (totalSpan - trainSpan) / 86400.0);

        // plot best vs two-body baseline
        if (best.series != null && best.series.length >= 2) {
            System.out.println();
            if (baselineSeries != null && baselineSeries.length == best.series.length) {
                System.out.print(AsciiChart.render(best.timeAxisDays,
                        new double[][] {best.series, baselineSeries}, new char[] {'*', '.'},
                        AsciiChart.terminalWidth(), 14,
                        "held-out error: best (*) vs two-body (.)", "error (m)", "time (days)"));
            } else {
                System.out.print(AsciiChart.render(best.timeAxisDays, best.series,
                        AsciiChart.terminalWidth(), 14,
                        "held-out prediction error (" + best.label + ")", "error (m)", "time (days)"));
            }
        }
        return 0;
    }

    /**
     * Headless evaluation of a sweep (for tests / embedding): returns the held-out
     * prediction RMS (m) for each force set, in input order.
     */
    static double[] scoreCombos(final PoeOrbReader.Ephemeris trainEph,
                                final PoeOrbReader.Ephemeris truthEph, final OrbitFiles.Setup setup,
                                final List<OrbitFiles.ForceSet> combos, final double cdGuess,
                                final int maxIter, final double obsStep) {
        OrbitFiles.configureFrames(truthEph);
        final AbsoluteDate epoch = truthEph.dates.get(0);
        final double cadence = truthEph.dates.get(1).durationFrom(epoch);
        final int stride = Math.max(1, (int) Math.round(60.0 / cadence));
        final double scoreStep = stride * cadence;
        final double trainSpan =
                trainEph.dates.get(trainEph.size() - 1).durationFrom(trainEph.dates.get(0));
        final List<Vector3D> trainGcrf = OrbitFiles.gcrfPositions(trainEph);
        final double trainCad = trainEph.dates.get(1).durationFrom(trainEph.dates.get(0));
        final int obsStride = Math.max(1, (int) Math.round(obsStep / trainCad));
        final List<PositionObservation> obs = new ArrayList<>();
        for (int i = 0; i < trainEph.size(); i += obsStride) {
            obs.add(new PositionObservation(trainEph.dates.get(i), trainGcrf.get(i)));
        }
        final SpacecraftState seed = OrbitFiles.gcrfState(trainEph, 0, setup.mass);
        final List<ScorePoint> grid = buildGrid(truthEph, stride, epoch, scoreStep, trainSpan);
        final double[] out = new double[combos.size()];
        for (int c = 0; c < combos.size(); c++) {
            out[c] = evaluate(combos.get(c), seed, setup, obs, cdGuess, maxIter, grid, epoch, scoreStep).heldRms;
        }
        return out;
    }

    /** Fit one force set on the training arc, predict, and score on the held-out grid. */
    private static Result evaluate(final OrbitFiles.ForceSet fs, final SpacecraftState seed,
                            final OrbitFiles.Setup setup, final List<PositionObservation> obs,
                            final double cdGuess, final int maxIter, final List<ScorePoint> grid,
                            final AbsoluteDate epoch, final double scoreStep) {
        final boolean drag = fs.hasDrag();
        final boolean srp = fs.srp;
        final int idxCd = drag ? 6 : -1;
        final int idxCr = srp ? (drag ? 7 : 6) : -1;
        final int p = 6 + (drag ? 1 : 0) + (srp ? 1 : 0);

        final Vector3D r0 = seed.getPosition();
        final Vector3D v0 = seed.getVelocity();
        final double[] guess = new double[p];
        guess[0] = r0.getX(); guess[1] = r0.getY(); guess[2] = r0.getZ();
        guess[3] = v0.getX(); guess[4] = v0.getY(); guess[5] = v0.getZ();
        final double[] scale = new double[p];
        scale[0] = scale[1] = scale[2] = 1.0;
        scale[3] = scale[4] = scale[5] = 1.0e-3;
        if (drag) {
            guess[idxCd] = cdGuess;
            scale[idxCd] = 1.0e-2;
        }
        if (srp) {
            guess[idxCr] = setup.cr;
            scale[idxCr] = 1.0e-2;
        }

        final BatchLeastSquares.PropagatorFactory factory = params -> {
            final Vector3D r = new Vector3D(params[0], params[1], params[2]);
            final Vector3D v = new Vector3D(params[3], params[4], params[5]);
            final double cd = drag ? params[idxCd] : cdGuess;
            final double cr = srp ? params[idxCr] : setup.cr;
            final SpacecraftState st = new SpacecraftState(
                    new CartesianOrbit(new PVCoordinates(r, v), seed.getFrame(), seed.getDate(),
                            Constants.EARTH_MU), setup.mass);
            return (SampledPropagator) OrbitFiles.config(fs, st, setup, cd, cr).build();
        };

        final BatchLeastSquares.Result fit =
                new BatchLeastSquares(factory, obs, scale).estimate(guess, maxIter);

        final Result out = new Result();
        out.label = fs.label;
        out.fitRms = fit.postFitRms;
        out.cd = drag ? fit.params[idxCd] : -1;
        out.cr = srp ? fit.params[idxCr] : -1;

        // predict forward over the full timeline with the fitted parameters and score
        final NumericalPropagator predictor = (NumericalPropagator) factory.build(fit.params);
        final EphemerisCollector col = new EphemerisCollector();
        predictor.setStepHandler(scoreStep, col);
        predictor.propagate(grid.get(grid.size() - 1).date);
        final List<SpacecraftState> comp = col.getStates();

        final int n = Math.min(comp.size(), grid.size());
        final List<Double> heldErr = new ArrayList<>();
        final List<Double> heldDays = new ArrayList<>();
        double sumSq = 0.0;
        double max = 0.0;
        double last = 0.0;
        for (int k = 0; k < n; k++) {
            final ScorePoint sp = grid.get(k);
            final double err = comp.get(k).getPosition().subtract(sp.truth).getNorm();
            if (sp.heldOut) {
                sumSq += err * err;
                max = Math.max(max, err);
                last = err;
                heldErr.add(err);
                heldDays.add(sp.tSec / 86400.0);
            }
        }
        final int m = heldErr.size();
        out.heldRms = m > 0 ? Math.sqrt(sumSq / m) : Double.NaN;
        out.heldMax = max;
        out.heldFinal = last;
        out.series = new double[m];
        out.timeAxisDays = new double[m];
        for (int i = 0; i < m; i++) {
            out.series[i] = heldErr.get(i);
            out.timeAxisDays[i] = heldDays.get(i);
        }
        return out;
    }

    /** Pre-compute the GCRF truth on the ~60 s scoring grid, tagging the held-out portion. */
    private static List<ScorePoint> buildGrid(final PoeOrbReader.Ephemeris truthEph, final int stride,
                                       final AbsoluteDate epoch, final double scoreStep,
                                       final double trainSpan) {
        final Frame itrf = FramesFactory.getITRF();
        final Frame gcrf = FramesFactory.getGCRF();
        final List<ScorePoint> grid = new ArrayList<>();
        for (int j = 0, k = 0; j < truthEph.size(); j += stride, k++) {
            final ScorePoint sp = new ScorePoint();
            sp.date = truthEph.dates.get(j);
            sp.tSec = sp.date.durationFrom(epoch);
            sp.truth = FramesFactory.getTransform(itrf, gcrf, sp.date)
                    .transformPosition(truthEph.positionItrf.get(j));
            sp.heldOut = sp.tSec > trainSpan + 1.0;
            grid.add(sp);
        }
        return grid;
    }

    /** Build the force-model sweep. */
    private List<OrbitFiles.ForceSet> buildSweep(final String sweep, final int gravDeg) {
        final List<OrbitFiles.ForceSet> list = new ArrayList<>();
        if ("atmos".equalsIgnoreCase(sweep)) {
            for (final String atm : new String[] {"none", "exponential", "harris-priester", "nrlmsise00"}) {
                final OrbitFiles.ForceSet fs = full(gravDeg);
                fs.atmosphere = atm;
                fs.label = "full, drag=" + atm;
                list.add(fs);
            }
            return list;
        }
        if ("full".equalsIgnoreCase(sweep)) {
            list.add(full(gravDeg));
            return list;
        }
        // default: incremental ladder (cumulative) - shows each force's marginal contribution
        OrbitFiles.ForceSet fs = bare();
        fs.gravityDegree = 1; fs.gravityOrder = 0; fs.label = "two-body (central)";
        list.add(copy(fs));
        fs.gravityDegree = 2; fs.gravityOrder = 0; fs.label = "+ J2";
        list.add(copy(fs));
        fs.gravityDegree = gravDeg; fs.gravityOrder = gravDeg;
        fs.label = "+ gravity " + gravDeg + "x" + gravDeg;
        list.add(copy(fs));
        fs.sun = true; fs.moon = true; fs.label = "+ Sun/Moon";
        list.add(copy(fs));
        fs.atmosphere = "nrlmsise00"; fs.label = "+ drag (NRLMSISE, est. Cd)";
        list.add(copy(fs));
        fs.srp = true; fs.label = "+ SRP (est. Cr)";
        list.add(copy(fs));
        fs.solidTides = true; fs.label = "+ solid tides";
        list.add(copy(fs));
        fs.oceanTides = true; fs.label = "+ ocean tides";
        list.add(copy(fs));
        fs.relativity = true; fs.label = "+ relativity";
        list.add(copy(fs));
        fs.albedo = true; fs.label = "+ albedo/IR (full)";
        list.add(copy(fs));
        return list;
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
        fs.label = "full (" + gravDeg + "x" + gravDeg + ")";
        return fs;
    }

    private static OrbitFiles.ForceSet copy(final OrbitFiles.ForceSet s) {
        final OrbitFiles.ForceSet c = new OrbitFiles.ForceSet();
        c.label = s.label; c.gravityDegree = s.gravityDegree; c.gravityOrder = s.gravityOrder;
        c.sun = s.sun; c.moon = s.moon; c.atmosphere = s.atmosphere; c.srp = s.srp;
        c.solidTides = s.solidTides; c.oceanTides = s.oceanTides;
        c.relativity = s.relativity; c.albedo = s.albedo;
        return c;
    }

    private static final class ScorePoint {
        AbsoluteDate date;
        double tSec;
        Vector3D truth;
        boolean heldOut;
    }

    private static final class Result {
        String label;
        double cd = -1;
        double cr = -1;
        double fitRms;
        double heldRms;
        double heldMax;
        double heldFinal;
        double[] series;
        double[] timeAxisDays;
    }
}
