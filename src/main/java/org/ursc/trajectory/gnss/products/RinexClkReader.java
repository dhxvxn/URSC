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
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Reader for RINEX clock files (2.x / 3.x): satellite clock records ({@code AS}) for GPS.
 * Values are seconds; interpolation is linear between samples no more than three nominal
 * intervals apart.
 */
public final class RinexClkReader {

    private RinexClkReader() {
    }

    public static final class Data {
        private final AbsoluteDate reference;
        private final Map<Integer, double[]> times = new TreeMap<>();
        private final Map<Integer, double[]> biases = new TreeMap<>();
        private double interval = 30.0;

        Data(final AbsoluteDate reference) {
            this.reference = reference;
        }

        public java.util.Set<Integer> prns() {
            return times.keySet();
        }

        /** Median sampling interval (s). */
        public double interval() {
            return interval;
        }

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

        public boolean covers(final int prn, final AbsoluteDate date) {
            return !Double.isNaN(clock(prn, date));
        }

        /** Satellite clock (s) excluding the relativistic correction, NaN if not available. */
        public double clock(final int prn, final AbsoluteDate date) {
            final double[] t = times.get(prn);
            if (t == null || t.length == 0) {
                return Double.NaN;
            }
            final double[] b = biases.get(prn);
            final double s = date.durationFrom(reference);
            int i = java.util.Arrays.binarySearch(t, s);
            if (i >= 0) {
                return b[i];
            }
            i = -i - 2;
            if (i < 0 || i + 1 >= t.length || t[i + 1] - t[i] > 3 * interval) {
                return Double.NaN;
            }
            final double f = (s - t[i]) / (t[i + 1] - t[i]);
            return b[i] + f * (b[i + 1] - b[i]);
        }
    }

    public static Data read(final Path path) throws IOException {
        try (InputStream in = ProductInput.open(path)) {
            return read(in);
        }
    }

    public static Data read(final InputStream stream) throws IOException {
        final BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII));
        String line;
        String timeSys = "GPS";
        boolean header = true;
        boolean sawVersion = false;
        final Map<Integer, List<double[]>> rows = new TreeMap<>();
        AbsoluteDate reference = null;
        while ((line = br.readLine()) != null) {
            if (header) {
                if (line.contains("RINEX VERSION / TYPE")) {
                    sawVersion = true;
                    if (line.length() > 20 && Character.toUpperCase(line.charAt(20)) != 'C') {
                        throw new IOException("not a RINEX clock file: " + line.trim());
                    }
                } else if (line.contains("TIME SYSTEM ID")) {
                    timeSys = line.substring(0, Math.min(60, line.length())).trim();
                } else if (line.contains("END OF HEADER")) {
                    header = false;
                    if (!sawVersion) {
                        throw new IOException("RINEX clock header has no version line");
                    }
                }
                continue;
            }
            if (!line.startsWith("AS")) {
                continue;
            }
            final String[] t = line.substring(2).trim().split("\\s+");
            if (t.length < 9 || t[0].isEmpty() || t[0].charAt(0) != 'G') {
                continue;
            }
            final int prn = Integer.parseInt(t[0].substring(1));
            final AbsoluteDate d = new AbsoluteDate(Integer.parseInt(t[1]), Integer.parseInt(t[2]),
                    Integer.parseInt(t[3]), Integer.parseInt(t[4]), Integer.parseInt(t[5]),
                    Double.parseDouble(t[6]), Sp3Reader.scale(timeSys));
            if (reference == null) {
                reference = d;
            }
            final double bias = Double.parseDouble(t[8].replace('D', 'E'));
            rows.computeIfAbsent(prn, k -> new ArrayList<>())
                    .add(new double[] {d.durationFrom(reference), bias});
        }
        if (reference == null) {
            throw new IOException("RINEX clock file contains no GPS satellite records");
        }
        final Data data = new Data(reference);
        final List<Double> steps = new ArrayList<>();
        for (final Map.Entry<Integer, List<double[]>> e : rows.entrySet()) {
            final List<double[]> r = e.getValue();
            r.sort((a, b) -> Double.compare(a[0], b[0]));
            final double[] tt = new double[r.size()];
            final double[] bb = new double[r.size()];
            for (int i = 0; i < r.size(); i++) {
                tt[i] = r.get(i)[0];
                bb[i] = r.get(i)[1];
                if (i > 0 && steps.size() < 2000) {
                    steps.add(tt[i] - tt[i - 1]);
                }
            }
            data.times.put(e.getKey(), tt);
            data.biases.put(e.getKey(), bb);
        }
        if (!steps.isEmpty()) {
            steps.sort(Double::compare);
            data.interval = Math.max(1.0, steps.get(steps.size() / 2));
        }
        return data;
    }
}
