package org.ursc.trajectory.frames;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Earth Orientation Parameters read from an IERS EOP&nbsp;C04 (IAU2000)
 * whitespace-column file: {@code year month day MJD x(") y(") UT1-UTC(s) ...}.
 * Daily values are linearly interpolated; dates outside the file fall back to a
 * configurable provider (default zero).
 */
public final class IersEopProvider implements EopProvider {

    private static final double ARCSEC_TO_RAD = Math.PI / (180.0 * 3600.0);

    private final double[] mjd;
    private final double[] dut1;
    private final double[] xpRad;
    private final double[] ypRad;
    private final EopProvider fallback;

    public IersEopProvider(final Path file, final EopProvider fallback) throws IOException {
        this.fallback = fallback;
        try (InputStream in = Files.newInputStream(file)) {
            final List<double[]> rows = parse(in);
            final int n = rows.size();
            mjd = new double[n];
            dut1 = new double[n];
            xpRad = new double[n];
            ypRad = new double[n];
            for (int i = 0; i < n; i++) {
                mjd[i] = rows.get(i)[0];
                xpRad[i] = rows.get(i)[1] * ARCSEC_TO_RAD;
                ypRad[i] = rows.get(i)[2] * ARCSEC_TO_RAD;
                dut1[i] = rows.get(i)[3];
            }
        }
    }

    private static List<double[]> parse(final InputStream in) throws IOException {
        final List<double[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            double lastMjd = Double.NEGATIVE_INFINITY;
            while ((line = reader.readLine()) != null) {
                final String s = line.trim();
                if (s.isEmpty()) {
                    continue;
                }
                final String[] t = s.split("\\s+");
                if (t.length < 7) {
                    continue;
                }
                // data lines begin with a plausible 4-digit year
                final Integer year = tryInt(t[0]);
                if (year == null || year < 1962 || year > 2100) {
                    continue;
                }
                final Double m = tryDouble(t[3]);
                final Double x = tryDouble(t[4]);
                final Double y = tryDouble(t[5]);
                final Double u = tryDouble(t[6]);
                if (m == null || x == null || y == null || u == null || m <= lastMjd) {
                    continue;
                }
                rows.add(new double[] {m, x, y, u});
                lastMjd = m;
            }
        }
        return rows;
    }

    private double mjdOf(final AbsoluteDate date) {
        return date.julianDate(TimeScalesFactory.getUTC()) - 2400000.5;
    }

    private double interpolate(final AbsoluteDate date, final double[] values, final double fb) {
        if (mjd.length == 0) {
            return fb;
        }
        final double m = mjdOf(date);
        if (m <= mjd[0] || m >= mjd[mjd.length - 1]) {
            return Double.NaN; // out of range -> signal fallback
        }
        // binary search for the bracketing interval
        int lo = 0;
        int hi = mjd.length - 1;
        while (hi - lo > 1) {
            final int mid = (lo + hi) / 2;
            if (mjd[mid] > m) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        final double w = (m - mjd[lo]) / (mjd[hi] - mjd[lo]);
        return values[lo] * (1 - w) + values[hi] * w;
    }

    @Override
    public double getUT1MinusUTC(final AbsoluteDate date) {
        final double v = interpolate(date, dut1, Double.NaN);
        return Double.isNaN(v) ? fallback.getUT1MinusUTC(date) : v;
    }

    @Override
    public double getXp(final AbsoluteDate date) {
        final double v = interpolate(date, xpRad, Double.NaN);
        return Double.isNaN(v) ? fallback.getXp(date) : v;
    }

    @Override
    public double getYp(final AbsoluteDate date) {
        final double v = interpolate(date, ypRad, Double.NaN);
        return Double.isNaN(v) ? fallback.getYp(date) : v;
    }

    private static Integer tryInt(final String s) {
        try {
            return Integer.valueOf(s);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static Double tryDouble(final String s) {
        try {
            return Double.valueOf(s);
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
