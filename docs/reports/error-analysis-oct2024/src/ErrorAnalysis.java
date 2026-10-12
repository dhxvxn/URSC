import org.ursc.trajectory.bodies.*;
import org.ursc.trajectory.config.PropagationConfig;
import org.ursc.trajectory.forces.drag.*;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.frames.*;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.*;
import org.ursc.trajectory.ode.DormandPrince54Integrator;
import org.ursc.trajectory.orbits.*;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.propagation.sampling.EphemerisCollector;
import org.ursc.trajectory.time.*;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Error analysis: propagate the single Oct-1 seed forward with the full force model
 * and real space weather, then compare (in ITRF, the data frame) against the actual
 * Oct-7/8 POEORB truth at each epoch.
 *
 *   args[0] = seed EOF (single OSV, ITRF)
 *   args[1] = truth POEORB file (ITRF)
 *   args[2] = space-weather file (orekit CSSI txt)
 *   args[3] = output CSV
 *   args[4] = sample step seconds (e.g. 60)
 */
public final class ErrorAnalysis {

    static SpaceWeatherProvider sw;

    public static void main(final String[] a) throws Exception {
        final String seedPath = a[0];
        final String truthPath = a[1];
        final String swPath = a[2];
        final String outCsv = a[3];
        final double step = Double.parseDouble(a[4]);

        // Read the truth first so we can use ITS UT1-UTC for the frame transform.
        final PoeOrbReader.Ephemeris truth = PoeOrbReader.read(java.nio.file.Paths.get(truthPath));
        FramesFactory.setModel(FramesFactory.Model.IAU_2006);
        FramesFactory.setEopProvider(new ConstantEop(truth.ut1MinusUtc, 0.0, 0.0));
        System.out.printf(Locale.US, "truth: %d records, UT1-UTC=%.6f s%n", truth.size(), truth.ut1MinusUtc);

        sw = new CssiTxtSpaceWeatherProvider(java.nio.file.Paths.get(swPath),
                new ConstantSpaceWeather(220, 205, 12));

        // Seed GCRF state from the Oct-1 ITRF OSV.
        final PoeOrbReader.Ephemeris seedEph = PoeOrbReader.read(java.nio.file.Paths.get(seedPath));
        final AbsoluteDate t0 = seedEph.dates.get(0);
        final Transform i2g0 = FramesFactory.getTransform(
                FramesFactory.getITRF(), FramesFactory.getGCRF(), t0);
        final Vector3D r0 = i2g0.transformPosition(seedEph.positionItrf.get(0));
        final Vector3D v0 = i2g0.transformVelocity(seedEph.velocityItrf.get(0), r0);
        final SpacecraftState seed = new SpacecraftState(
                new CartesianOrbit(new PVCoordinates(r0, v0), FramesFactory.getGCRF(), t0, Constants.EARTH_MU),
                1130.0);

        final AbsoluteDate firstTruth = truth.dates.get(0);
        final AbsoluteDate lastTruth = truth.dates.get(truth.size() - 1);
        System.out.printf(Locale.US, "seed  epoch: %s%n", fmt(t0));
        System.out.printf(Locale.US, "truth span : %s  ->  %s%n", fmt(firstTruth), fmt(lastTruth));
        System.out.printf(Locale.US, "lead time  : %.3f days (seed -> first truth)%n",
                firstTruth.durationFrom(t0) / 86400.0);

        // Propagate ONCE from the seed to the last truth epoch, sampling on a `step` grid.
        final EphemerisCollector collector = new EphemerisCollector();
        final NumericalPropagator prop = build(seed, step, collector);
        final long wall0 = System.currentTimeMillis();
        prop.propagate(lastTruth);
        System.out.printf(Locale.US, "propagated %d grid states in %.1f s%n",
                collector.size(), (System.currentTimeMillis() - wall0) / 1000.0);

        // Index collected GCRF states by integer seconds-from-seed.
        final Map<Long, SpacecraftState> grid = new HashMap<>();
        for (final SpacecraftState s : collector.getStates()) {
            grid.put(Math.round(s.getDate().durationFrom(t0)), s);
        }

        // For each truth epoch that lands on the grid, compute ITRF errors.
        final List<double[]> rows = new ArrayList<>(); // [offsetSec, dPos, dVel, mx,my,mz,mvx,mvy,mvz, ax,ay,az,avx,avy,avz]
        double sumP2 = 0, sumV2 = 0, maxP = 0, maxV = 0;
        int n = 0;
        for (int i = 0; i < truth.size(); i++) {
            final long off = Math.round(truth.dates.get(i).durationFrom(t0));
            final SpacecraftState m = grid.get(off);
            if (m == null) {
                continue; // not a grid epoch
            }
            final AbsoluteDate epoch = truth.dates.get(i);
            final Transform g2i = FramesFactory.getTransform(
                    FramesFactory.getGCRF(), FramesFactory.getITRF(), epoch);
            final Vector3D rModel = g2i.transformPosition(m.getPosition());
            final Vector3D vModel = g2i.transformVelocity(m.getVelocity(), rModel);
            final Vector3D rAct = truth.positionItrf.get(i);
            final Vector3D vAct = truth.velocityItrf.get(i);

            final double dPos = rModel.subtract(rAct).getNorm();
            final double dVel = vModel.subtract(vAct).getNorm();

            rows.add(new double[] {off, dPos, dVel,
                    rModel.getX(), rModel.getY(), rModel.getZ(),
                    vModel.getX(), vModel.getY(), vModel.getZ(),
                    rAct.getX(), rAct.getY(), rAct.getZ(),
                    vAct.getX(), vAct.getY(), vAct.getZ()});
            sumP2 += dPos * dPos;
            sumV2 += dVel * dVel;
            maxP = Math.max(maxP, dPos);
            maxV = Math.max(maxV, dVel);
            n++;
        }

        final double rmsP = Math.sqrt(sumP2 / n);
        final double rmsV = Math.sqrt(sumV2 / n);
        final double[] last = rows.get(rows.size() - 1);

        // Write the full CSV (one row per sampled epoch).
        try (PrintWriter w = new PrintWriter(outCsv)) {
            w.println("epoch_utc,hours_from_start,dPos_m,dVel_ms,"
                    + "model_x_m,model_y_m,model_z_m,model_vx_ms,model_vy_ms,model_vz_ms,"
                    + "actual_x_m,actual_y_m,actual_z_m,actual_vx_ms,actual_vy_ms,actual_vz_ms");
            final double startOff = rows.get(0)[0];
            for (final double[] r : rows) {
                final AbsoluteDate epoch = t0.shiftedBy(r[0]);
                final double hrs = (r[0] - startOff) / 3600.0;
                w.printf(Locale.US, "%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                        fmt(epoch), hrs, r[1], r[2],
                        r[3], r[4], r[5], r[6], r[7], r[8],
                        r[9], r[10], r[11], r[12], r[13], r[14]);
            }
        }

        System.out.println();
        System.out.printf(Locale.US, "samples            : %d (every %.0f s)%n", n, step);
        System.out.printf(Locale.US, "POSITION error (m) : RMS=%.2f  max=%.2f  final=%.2f%n", rmsP, maxP, last[1]);
        System.out.printf(Locale.US, "POSITION error (km): RMS=%.3f  max=%.3f  final=%.3f%n",
                rmsP / 1000.0, maxP / 1000.0, last[1] / 1000.0);
        System.out.printf(Locale.US, "VELOCITY error     : RMS=%.4f  max=%.4f  final=%.4f  m/s%n", rmsV, maxV, last[2]);
        System.out.printf(Locale.US, "CSV written        : %s%n", outCsv);
    }

    static NumericalPropagator build(final SpacecraftState s0, final double step,
                                     final EphemerisCollector collector) {
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(
                Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING);
        final NumericalPropagator p = (NumericalPropagator) new PropagationConfig()
                .initialState(s0)
                .integrator(new DormandPrince54Integrator(1e-7, 1e-9, 1e-3, 120.0), 60.0)
                .gravityField(GravityFieldFactory.getEgm96(30), 30, 30)
                .thirdBody(CelestialBodyFactory.getSun()).thirdBody(CelestialBodyFactory.getMoon())
                .drag(new NRLMSISE00Atmosphere(earth, sw), 2.2, 8.0)
                .solarRadiationPressure(CelestialBodyFactory.getSun(), 1.3, 8.0)
                .earthRadiation(CelestialBodyFactory.getSun(), 1.3, 8.0)
                .solidTides(true).oceanTides(true).relativity(true, true)
                .outputType(OrbitType.CARTESIAN).build();
        p.setOutputStep(step);
        p.addStepHandler(collector);
        return p;
    }

    static String fmt(final AbsoluteDate d) {
        final double[] c = d.getComponents(TimeScalesFactory.getUTC());
        return String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d:%06.3f",
                (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], c[5]);
    }
}
