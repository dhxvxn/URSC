package org.ursc.trajectory.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Compares a run's trajectory with POEORB reference orbits, for display only (the reference
 * never feeds a run). The reference is Lagrange-interpolated (8 points) to each run epoch it
 * covers without gaps, converted to GCRF with the full Earth rotation rate, and the error is
 * resolved in the reference's radial / along-track / cross-track axes.
 */
public final class ReferenceComparison {

    private static final int POINTS = 8;

    private ReferenceComparison() {
    }

    public static final class Result {
        public final List<Integer> runIndex = new ArrayList<>();
        public final List<Double> err3d = new ArrayList<>();
        public final List<Double> radial = new ArrayList<>();
        public final List<Double> along = new ArrayList<>();
        public final List<Double> cross = new ArrayList<>();
        /** Interpolated reference states (GCRF) at the compared run epochs. */
        public final List<RunResult.State> reference = new ArrayList<>();
    }

    public static Result compare(final List<Path> files, final List<RunResult.State> states) throws IOException {
        final TreeMap<Double, double[]> rows = new TreeMap<>();
        AbsoluteDate origin = null;
        for (final Path f : files) {
            final PoeOrbReader.Ephemeris e = PoeOrbReader.read(f);
            for (int i = 0; i < e.size(); i++) {
                if (origin == null) {
                    origin = e.dates.get(i);
                }
                final Vector3D r = e.positionItrf.get(i);
                final Vector3D v = e.velocityItrf.get(i);
                rows.putIfAbsent(e.dates.get(i).durationFrom(origin),
                        new double[] {r.getX(), r.getY(), r.getZ(), v.getX(), v.getY(), v.getZ()});
            }
        }
        final Result out = new Result();
        if (origin == null || rows.size() < POINTS) {
            return out;
        }
        final double[] t = new double[rows.size()];
        final double[][] y = new double[rows.size()][];
        int k = 0;
        for (final var en : rows.entrySet()) {
            t[k] = en.getKey();
            y[k++] = en.getValue();
        }
        final double step = (t[t.length - 1] - t[0]) / (t.length - 1);
        for (int i = 0; i < states.size(); i++) {
            final RunResult.State s = states.get(i);
            final double x = s.date.durationFrom(origin);
            if (x < t[0] || x > t[t.length - 1]) {
                continue;
            }
            int j = java.util.Arrays.binarySearch(t, x);
            if (j < 0) {
                j = -j - 2;
            }
            int start = Math.max(0, Math.min(j - POINTS / 2 + 1, t.length - POINTS));
            if (t[start + POINTS - 1] - t[start] > (POINTS - 1) * step * 1.5 + 1e-6) {
                continue; // gap in the reference
            }
            final double[] p = new double[6];
            for (int a = 0; a < POINTS; a++) {
                double w = 1.0;
                for (int b = 0; b < POINTS; b++) {
                    if (b != a) {
                        w *= (x - t[start + b]) / (t[start + a] - t[start + b]);
                    }
                }
                for (int c = 0; c < 6; c++) {
                    p[c] += w * y[start + a][c];
                }
            }
            final FrameUtil.PV ref = FrameUtil.itrfToGcrf(new Vector3D(p[0], p[1], p[2]),
                    new Vector3D(p[3], p[4], p[5]), s.date);
            final Vector3D e = s.position.subtract(ref.r);
            final Vector3D rh = ref.r.normalize();
            final Vector3D nh = ref.r.crossProduct(ref.v).normalize();
            final Vector3D th = nh.crossProduct(rh);
            out.runIndex.add(i);
            out.err3d.add(e.getNorm());
            out.radial.add(e.dotProduct(rh));
            out.along.add(e.dotProduct(th));
            out.cross.add(e.dotProduct(nh));
            out.reference.add(new RunResult.State(s.date, ref.r, ref.v, null));
        }
        return out;
    }
}
