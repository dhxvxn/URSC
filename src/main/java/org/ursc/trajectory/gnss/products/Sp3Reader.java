package org.ursc.trajectory.gnss.products;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.ursc.trajectory.io.ProductInput;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScale;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Reader for IGS SP3-c / SP3-d precise orbit files (GPS satellites). Positions are
 * converted km -> m and clocks microseconds -> seconds; the SP3 "bad" sentinels
 * (zero position, 999999.999999 clock) become missing samples. Positions are
 * interpolated with an 11-point Lagrange polynomial on normalised time; clocks
 * linearly between valid samples.
 */
public final class Sp3Reader {

    private static final double BAD_CLOCK = 999999.0;
    /** Lagrange points used for orbit interpolation (order 10). */
    static final int LAGRANGE_POINTS = 11;

    private Sp3Reader() {
    }

    /** Parsed SP3 content. Times are seconds since {@link #reference()}. */
    public static final class Data {
        private final AbsoluteDate reference;
        private final double interval;
        private final String agency;
        private final String coordinateSystem;
        private final char version;
        private final Map<Integer, double[]> times = new TreeMap<>();
        private final Map<Integer, double[][]> positions = new TreeMap<>();
        private final Map<Integer, double[]> clocks = new TreeMap<>();

        Data(final AbsoluteDate reference, final double interval, final String agency,
             final String coordinateSystem, final char version) {
            this.reference = reference;
            this.interval = interval;
            this.agency = agency;
            this.coordinateSystem = coordinateSystem;
            this.version = version;
        }

        public AbsoluteDate reference() {
            return reference;
        }

        /** Nominal sampling interval (s). */
        public double interval() {
            return interval;
        }

        public String agency() {
            return agency;
        }

        public String coordinateSystem() {
            return coordinateSystem;
        }

        public char version() {
            return version;
        }

        public java.util.Set<Integer> prns() {
            return times.keySet();
        }

        /** First and last epoch with any valid GPS position, or null if none. */
        public AbsoluteDate[] span() {
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            for (final double[] t : times.values()) {
                if (t.length > 0) {
                    lo = Math.min(lo, t[0]);
                    hi = Math.max(hi, t[t.length - 1]);
                }
            }
            return lo > hi ? null : new AbsoluteDate[] {reference.shiftedBy(lo), reference.shiftedBy(hi)};
        }

        /** Index of the window start for an 11-point interpolation, or -1 if not interpolable. */
        private int window(final double[] t, final double s) {
            if (t.length < LAGRANGE_POINTS || s < t[0] || s > t[t.length - 1]) {
                return -1;
            }
            int i = java.util.Arrays.binarySearch(t, s);
            if (i < 0) {
                i = -i - 2;
            }
            int start = i - LAGRANGE_POINTS / 2 + 1;
            start = Math.max(0, Math.min(start, t.length - LAGRANGE_POINTS));
            // reject windows spanning a data gap
            final double maxSpan = (LAGRANGE_POINTS - 1) * interval * 1.5;
            if (t[start + LAGRANGE_POINTS - 1] - t[start] > maxSpan) {
                return -1;
            }
            return start;
        }

        public boolean coversOrbit(final int prn, final AbsoluteDate date) {
            final double[] t = times.get(prn);
            return t != null && window(t, date.durationFrom(reference)) >= 0;
        }

        public Vector3D position(final int prn, final AbsoluteDate date) {
            final double[] t = times.get(prn);
            final double s = date.durationFrom(reference);
            final int start = t == null ? -1 : window(t, s);
            if (start < 0) {
                throw new IllegalArgumentException("SP3 has no orbit for G" + prn + " at " + date);
            }
            final double[][] p = positions.get(prn);
            final double mid = t[start + LAGRANGE_POINTS / 2];
            final double[] out = new double[3];
            for (int k = 0; k < LAGRANGE_POINTS; k++) {
                double w = 1.0;
                final double xk = (t[start + k] - mid) / interval;
                final double x = (s - mid) / interval;
                for (int j = 0; j < LAGRANGE_POINTS; j++) {
                    if (j != k) {
                        final double xj = (t[start + j] - mid) / interval;
                        w *= (x - xj) / (xk - xj);
                    }
                }
                for (int c = 0; c < 3; c++) {
                    out[c] += w * p[start + k][c];
                }
            }
            return new Vector3D(out[0], out[1], out[2]);
        }

        public boolean coversClock(final int prn, final AbsoluteDate date) {
            return !Double.isNaN(clock(prn, date));
        }

        /** Linearly interpolated SP3 clock (s), NaN if no bracketing valid samples within 2 intervals. */
        public double clock(final int prn, final AbsoluteDate date) {
            final double[] t = times.get(prn);
            final double[] c = clocks.get(prn);
            if (t == null || t.length == 0) {
                return Double.NaN;
            }
            final double s = date.durationFrom(reference);
            int i = java.util.Arrays.binarySearch(t, s);
            if (i >= 0) {
                return c[i];
            }
            i = -i - 2;
            if (i < 0 || i + 1 >= t.length) {
                return Double.NaN;
            }
            if (t[i + 1] - t[i] > 2 * interval || Double.isNaN(c[i]) || Double.isNaN(c[i + 1])) {
                return Double.NaN;
            }
            final double f = (s - t[i]) / (t[i + 1] - t[i]);
            return c[i] + f * (c[i + 1] - c[i]);
        }
    }

    public static Data read(final Path path) throws IOException {
        try (InputStream in = ProductInput.open(path)) {
            return read(in);
        }
    }

    public static Data read(final InputStream stream) throws IOException {
        final BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII));
        final String first = br.readLine();
        if (first == null || first.length() < 3 || first.charAt(0) != '#'
                || "cd".indexOf(Character.toLowerCase(first.charAt(1))) < 0) {
            throw new IOException("not an SP3-c/d file (first line: " + first + ")");
        }
        final char version = Character.toLowerCase(first.charAt(1));
        final String[] h = first.substring(3).trim().split("\\s+");
        final String coord = h.length > 8 ? h[8] : "";
        final String agency = h.length > 10 ? h[10] : (h.length > 9 ? h[h.length - 1] : "");
        double interval = 900.0;
        String timeSys = "GPS";
        final Map<Integer, List<double[]>> rows = new TreeMap<>(); // [t, x, y, z, clk]
        AbsoluteDate reference = null;
        double current = Double.NaN;
        String line;
        boolean firstPercentC = true;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("##")) {
                final String[] t = line.substring(2).trim().split("\\s+");
                if (t.length >= 3) {
                    interval = Double.parseDouble(t[2]);
                }
            } else if (line.startsWith("%c") && firstPercentC) {
                firstPercentC = false;
                if (line.length() >= 12) {
                    final String ts = line.substring(9, 12).trim();
                    if (!ts.isEmpty() && !ts.startsWith("cc")) {
                        timeSys = ts;
                    }
                }
            } else if (line.startsWith("*")) {
                final String[] t = line.substring(1).trim().split("\\s+");
                final double sec = Double.parseDouble(t[5]);
                final AbsoluteDate d = new AbsoluteDate(Integer.parseInt(t[0]), Integer.parseInt(t[1]),
                        Integer.parseInt(t[2]), Integer.parseInt(t[3]), Integer.parseInt(t[4]), sec,
                        scale(timeSys));
                if (reference == null) {
                    reference = d;
                }
                current = d.durationFrom(reference);
            } else if (line.startsWith("P") && !Double.isNaN(current) && line.length() >= 46) {
                final char sys = line.charAt(1) == ' ' ? 'G' : line.charAt(1);
                if (sys != 'G') {
                    continue;
                }
                final int prn = Integer.parseInt(line.substring(2, 4).trim());
                final double x = Double.parseDouble(line.substring(4, 18).trim());
                final double y = Double.parseDouble(line.substring(18, 32).trim());
                final double z = Double.parseDouble(line.substring(32, 46).trim());
                double clk = Double.NaN;
                if (line.length() >= 60) {
                    final String cs = line.substring(46, 60).trim();
                    if (!cs.isEmpty()) {
                        final double v = Double.parseDouble(cs);
                        if (Math.abs(v) < BAD_CLOCK) {
                            clk = v * 1e-6;
                        }
                    }
                }
                if (x == 0.0 && y == 0.0 && z == 0.0) {
                    continue; // bad/missing position
                }
                rows.computeIfAbsent(prn, k -> new ArrayList<>())
                        .add(new double[] {current, x * 1000.0, y * 1000.0, z * 1000.0, clk});
            } else if (line.startsWith("EOF")) {
                break;
            }
        }
        if (reference == null) {
            throw new IOException("SP3 file contains no epochs");
        }
        final Data data = new Data(reference, interval, agency, coord, version);
        for (final Map.Entry<Integer, List<double[]>> e : rows.entrySet()) {
            final List<double[]> r = e.getValue();
            final double[] t = new double[r.size()];
            final double[][] p = new double[r.size()][];
            final double[] c = new double[r.size()];
            for (int i = 0; i < r.size(); i++) {
                t[i] = r.get(i)[0];
                p[i] = new double[] {r.get(i)[1], r.get(i)[2], r.get(i)[3]};
                c[i] = r.get(i)[4];
            }
            data.times.put(e.getKey(), t);
            data.positions.put(e.getKey(), p);
            data.clocks.put(e.getKey(), c);
        }
        return data;
    }

    static TimeScale scale(final String sys) {
        switch (sys.toUpperCase(java.util.Locale.US)) {
            case "UTC":
                return TimeScalesFactory.getUTC();
            case "TAI":
                return TimeScalesFactory.getTAI();
            case "GPS":
            default:
                return TimeScalesFactory.getGPS();
        }
    }
}
