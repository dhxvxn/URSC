import org.ursc.trajectory.frames.*;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.*;
import org.ursc.trajectory.orbits.*;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.analytical.KeplerianPropagator;
import org.ursc.trajectory.time.*;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Two-body (unperturbed Keplerian) baseline: propagate the single Oct-1 seed with a
 * BARE propagator -- NO perturbation models at all (no gravity harmonics, no drag,
 * no third body, no SRP/tides/relativity) -- and compare (in ITRF) against the actual
 * Oct-7/8 POEORB truth.
 *
 *   args[0] = seed EOF (single OSV, ITRF)
 *   args[1] = truth POEORB file (ITRF)
 *   args[2] = output CSV
 *   args[3] = sample step seconds (e.g. 60)
 */
public final class ErrorAnalysisKepler {

    public static void main(final String[] a) throws Exception {
        final String seedPath = a[0];
        final String truthPath = a[1];
        final String outCsv = a[2];
        final double step = Double.parseDouble(a[3]);

        final PoeOrbReader.Ephemeris truth = PoeOrbReader.read(java.nio.file.Paths.get(truthPath));
        FramesFactory.setModel(FramesFactory.Model.IAU_2006);
        FramesFactory.setEopProvider(new ConstantEop(truth.ut1MinusUtc, 0.0, 0.0));

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

        // BARE propagator: unperturbed two-body. No forces.
        final KeplerianPropagator prop = new KeplerianPropagator(seed);

        final AbsoluteDate firstTruth = truth.dates.get(0);
        final AbsoluteDate lastTruth = truth.dates.get(truth.size() - 1);
        System.out.printf(Locale.US, "TWO-BODY propagator (no perturbations)%n");
        System.out.printf(Locale.US, "seed  epoch: %s  (a=%.3f km, e=%.6f, i=%.4f deg)%n",
                fmt(t0), new KeplerianOrbit(seed.getOrbit()).getA() / 1000,
                new KeplerianOrbit(seed.getOrbit()).getE(),
                Math.toDegrees(new KeplerianOrbit(seed.getOrbit()).getI()));
        System.out.printf(Locale.US, "truth span : %s  ->  %s  (%d records)%n",
                fmt(firstTruth), fmt(lastTruth), truth.size());

        final int everyN = (int) Math.round(step / 10.0); // truth cadence is 10 s
        final List<double[]> rows = new ArrayList<>();
        double sumP2 = 0, sumV2 = 0, maxP = 0, maxV = 0;
        int n = 0;
        final double startOff = truth.dates.get(0).durationFrom(t0);
        for (int i = 0; i < truth.size(); i += Math.max(1, everyN)) {
            final AbsoluteDate epoch = truth.dates.get(i);
            final SpacecraftState m = prop.propagate(epoch);
            final Transform g2i = FramesFactory.getTransform(
                    FramesFactory.getGCRF(), FramesFactory.getITRF(), epoch);
            final Vector3D rModel = g2i.transformPosition(m.getPosition());
            final Vector3D vModel = g2i.transformVelocity(m.getVelocity(), rModel);
            final Vector3D rAct = truth.positionItrf.get(i);
            final Vector3D vAct = truth.velocityItrf.get(i);

            final double dPos = rModel.subtract(rAct).getNorm();
            final double dVel = vModel.subtract(vAct).getNorm();
            final double off = epoch.durationFrom(t0);
            rows.add(new double[] {off, (off - startOff) / 3600.0, dPos, dVel,
                    rModel.getX(), rModel.getY(), rModel.getZ(),
                    vModel.getX(), vModel.getY(), vModel.getZ(),
                    rAct.getX(), rAct.getY(), rAct.getZ(),
                    vAct.getX(), vAct.getY(), vAct.getZ()});
            sumP2 += dPos * dPos; sumV2 += dVel * dVel;
            maxP = Math.max(maxP, dPos); maxV = Math.max(maxV, dVel);
            n++;
        }

        final double rmsP = Math.sqrt(sumP2 / n);
        final double rmsV = Math.sqrt(sumV2 / n);
        final double[] last = rows.get(rows.size() - 1);

        try (PrintWriter w = new PrintWriter(outCsv)) {
            w.println("epoch_utc,hours_from_start,dPos_m,dVel_ms,"
                    + "model_x_m,model_y_m,model_z_m,model_vx_ms,model_vy_ms,model_vz_ms,"
                    + "actual_x_m,actual_y_m,actual_z_m,actual_vx_ms,actual_vy_ms,actual_vz_ms");
            for (final double[] r : rows) {
                final AbsoluteDate epoch = t0.shiftedBy(r[0]);
                w.printf(Locale.US, "%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                        fmt(epoch), r[1], r[2], r[3],
                        r[4], r[5], r[6], r[7], r[8], r[9],
                        r[10], r[11], r[12], r[13], r[14], r[15]);
            }
        }

        System.out.println();
        System.out.printf(Locale.US, "samples            : %d (every %.0f s)%n", n, step);
        System.out.printf(Locale.US, "POSITION error (km): RMS=%.3f  max=%.3f  final=%.3f%n",
                rmsP / 1000.0, maxP / 1000.0, last[2] / 1000.0);
        System.out.printf(Locale.US, "VELOCITY error     : RMS=%.4f  max=%.4f  final=%.4f  m/s%n", rmsV, maxV, last[3]);
        System.out.printf(Locale.US, "CSV written        : %s%n", outCsv);
    }

    static String fmt(final AbsoluteDate d) {
        final double[] c = d.getComponents(TimeScalesFactory.getUTC());
        return String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d:%06.3f",
                (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], c[5]);
    }
}
