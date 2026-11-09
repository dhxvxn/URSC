package org.ursc.trajectory.pipeline;

import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.io.Oem;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Position difference of one OEM trajectory relative to another, decomposed in the radial /
 * transverse / normal frame of the base trajectory. Epochs that match exactly are compared
 * directly; otherwise the other trajectory is interpolated with an 8-point Lagrange polynomial
 * (sub-millimetre for LEO at a 60 s step). Both files must use the same reference frame.
 */
public final class RunCompare {

    private RunCompare() {
    }

    /** Differences (other - base), metres (the OEM model holds metres), at base epochs inside the other's span. */
    public static final class Result {
        public final List<Double> t = new ArrayList<>();      // s from the base's first state
        public final List<String> epochs = new ArrayList<>();
        public final List<Double> err3d = new ArrayList<>();
        public final List<Double> radial = new ArrayList<>();
        public final List<Double> along = new ArrayList<>();
        public final List<Double> cross = new ArrayList<>();
        public String frame;
        public double max3d;
        public double rms3d;
        public double final3d;
    }

    private static List<Oem.StateLine> states(final Oem o) {
        final List<Oem.StateLine> s = new ArrayList<>();
        for (final Oem.Segment seg : o.segments) {
            s.addAll(seg.states);
        }
        return s;
    }

    private static String frame(final Oem o) {
        return o.segments.isEmpty() ? "" : o.segments.get(0).get("REF_FRAME");
    }

    public static Result compare(final Oem base, final Oem other) {
        if (!frame(base).equals(frame(other))) {
            throw new IllegalArgumentException("runs use different output frames (" + frame(base) + " vs "
                    + frame(other) + "); set the same Output frame to compare");
        }
        final List<Oem.StateLine> a = states(base);
        final List<Oem.StateLine> b = states(other);
        if (a.isEmpty() || b.isEmpty()) {
            throw new IllegalArgumentException("a run has no states");
        }
        final AbsoluteDate t0 = TimeUtil.parseUtc(a.get(0).epoch);
        final double[] tb = new double[b.size()];
        for (int i = 0; i < b.size(); i++) {
            tb[i] = TimeUtil.parseUtc(b.get(i).epoch).durationFrom(t0);
        }
        final Result r = new Result();
        r.frame = frame(base);
        double ss = 0;
        int j = 0;
        for (final Oem.StateLine s : a) {
            final double t = TimeUtil.parseUtc(s.epoch).durationFrom(t0);
            if (t < tb[0] - 1e-3 || t > tb[tb.length - 1] + 1e-3) {
                continue;
            }
            while (j < tb.length - 1 && tb[j + 1] <= t + 1e-3) {
                j++;
            }
            final double[] p = Math.abs(tb[j] - t) < 1e-3 ? b.get(j).pv : interpolate(b, tb, t, j);
            final double[] pv = s.pv;
            final double[] d = {p[0] - pv[0], p[1] - pv[1], p[2] - pv[2]}; // OEM model is in metres
            final double[] rr = unit(new double[] {pv[0], pv[1], pv[2]});
            final double[] nn = unit(cross(new double[] {pv[0], pv[1], pv[2]}, new double[] {pv[3], pv[4], pv[5]}));
            final double[] tt = cross(nn, rr);
            final double e = Math.sqrt(dot(d, d));
            r.t.add(t);
            r.epochs.add(s.epoch);
            r.err3d.add(e);
            r.radial.add(dot(d, rr));
            r.along.add(dot(d, tt));
            r.cross.add(dot(d, nn));
            r.max3d = Math.max(r.max3d, e);
            ss += e * e;
            r.final3d = e;
        }
        if (r.t.isEmpty()) {
            throw new IllegalArgumentException("the runs do not overlap in time");
        }
        r.rms3d = Math.sqrt(ss / r.t.size());
        return r;
    }

    private static double[] interpolate(final List<Oem.StateLine> b, final double[] tb, final double t, final int j) {
        final int n = Math.min(8, tb.length);
        int lo = Math.max(0, Math.min(j - n / 2 + 1, tb.length - n));
        final double[] out = new double[3];
        for (int k = lo; k < lo + n; k++) {
            double w = 1;
            for (int m = lo; m < lo + n; m++) {
                if (m != k) {
                    w *= (t - tb[m]) / (tb[k] - tb[m]);
                }
            }
            for (int c = 0; c < 3; c++) {
                out[c] += w * b.get(k).pv[c];
            }
        }
        return out;
    }

    private static double dot(final double[] a, final double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(final double[] a, final double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] unit(final double[] a) {
        final double n = Math.sqrt(dot(a, a));
        return new double[] {a[0] / n, a[1] / n, a[2] / n};
    }
}
