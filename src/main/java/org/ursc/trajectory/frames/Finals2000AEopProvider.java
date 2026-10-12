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
 * Earth Orientation Parameters read from the IERS <b>finals2000A.all</b> file distributed
 * with orekit-data (IAU&nbsp;2000A rapid-service / prediction series). Each data line carries
 * the Bulletin&nbsp;A polar motion and UT1&minus;UTC, tagged by an {@code I} (IERS) or
 * {@code P} (prediction) flag:
 *
 * <pre>
 *   yy mm dd  MJD  &lt;flag&gt; PM-x errx PM-y erry  &lt;flag&gt; UT1-UTC err  ...
 * </pre>
 *
 * Only the Bulletin&nbsp;A columns are read (they are present for every observed and predicted
 * day, which is all a LEO frame transform needs); rows past the prediction span have blank
 * fields and are skipped. Daily values are linearly interpolated; dates outside the file fall
 * back to a configurable provider (typically the POEORB's own UT1 with zero polar motion).
 *
 * <p>This complements {@link IersEopProvider}, which reads the whitespace EOP&nbsp;C04 series;
 * the two cover the two EOP file layouts orekit-data ships.</p>
 */
public final class Finals2000AEopProvider implements EopProvider {

    private static final double ARCSEC_TO_RAD = Math.PI / (180.0 * 3600.0);

    private final double[] mjd;
    private final double[] dut1;
    private final double[] xpRad;
    private final double[] ypRad;
    private final EopProvider fallback;

    public Finals2000AEopProvider(final Path file, final EopProvider fallback) throws IOException {
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

    public Finals2000AEopProvider(final InputStream in, final EopProvider fallback) throws IOException {
        this.fallback = fallback;
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

    /**
     * True if the first few non-empty lines look like finals2000A (a Bulletin A/P flag
     * sits just after the MJD field), so a loader can auto-select this over the C04 reader.
     */
    public static boolean looksLikeFinals2000A(final InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int checked = 0;
            while ((line = reader.readLine()) != null && checked < 8) {
                final double[] row = parseLine(line);
                if (row != null) {
                    return true;
                }
                if (!line.trim().isEmpty()) {
                    checked++;
                }
            }
        }
        return false;
    }

    private static List<double[]> parse(final InputStream in) throws IOException {
        final List<double[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            double lastMjd = Double.NEGATIVE_INFINITY;
            while ((line = reader.readLine()) != null) {
                final double[] row = parseLine(line);
                if (row != null && row[0] > lastMjd) {
                    rows.add(row);
                    lastMjd = row[0];
                }
            }
        }
        return rows;
    }

    /** @return {mjd, pmx(arcsec), pmy(arcsec), ut1MinusUtc(s)} or null if not a Bulletin A line. */
    private static double[] parseLine(final String line) {
        if (line == null) {
            return null;
        }
        final String s = line.trim();
        if (s.isEmpty()) {
            return null;
        }
        final String[] t = s.split("\\s+");
        if (t.length < 11) {
            return null;
        }
        // MJD is the 4th field (after yy mm dd)
        final Double m = tryDouble(t[3]);
        if (m == null || m < 15000.0 || m > 100000.0) {
            return null;
        }
        // first two I/P flags mark the Bulletin A polar-motion and UT1-UTC blocks
        int f1 = -1;
        int f2 = -1;
        for (int i = 4; i < t.length; i++) {
            if ("I".equals(t[i]) || "P".equals(t[i])) {
                if (f1 < 0) {
                    f1 = i;
                } else {
                    f2 = i;
                    break;
                }
            }
        }
        if (f1 < 0 || f2 < 0 || f2 + 1 >= t.length || f1 + 3 >= t.length) {
            return null;
        }
        final Double pmx = tryDouble(t[f1 + 1]);
        final Double pmy = tryDouble(t[f1 + 3]);
        final Double ut1 = tryDouble(t[f2 + 1]);
        if (pmx == null || pmy == null || ut1 == null) {
            return null;
        }
        // sanity: polar motion < 1", |UT1-UTC| < 1 s
        if (Math.abs(pmx) > 1.0 || Math.abs(pmy) > 1.0 || Math.abs(ut1) > 1.0) {
            return null;
        }
        return new double[] {m, pmx, pmy, ut1};
    }

    private double mjdOf(final AbsoluteDate date) {
        return date.julianDate(TimeScalesFactory.getUTC()) - 2400000.5;
    }

    private double interpolate(final AbsoluteDate date, final double[] values) {
        if (mjd.length == 0) {
            return Double.NaN;
        }
        final double m = mjdOf(date);
        if (m <= mjd[0] || m >= mjd[mjd.length - 1]) {
            return Double.NaN; // out of range -> signal fallback
        }
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
        final double v = interpolate(date, dut1);
        return Double.isNaN(v) ? fallback.getUT1MinusUTC(date) : v;
    }

    @Override
    public double getXp(final AbsoluteDate date) {
        final double v = interpolate(date, xpRad);
        return Double.isNaN(v) ? fallback.getXp(date) : v;
    }

    @Override
    public double getYp(final AbsoluteDate date) {
        final double v = interpolate(date, ypRad);
        return Double.isNaN(v) ? fallback.getYp(date) : v;
    }

    private static Double tryDouble(final String s) {
        try {
            return Double.valueOf(s);
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
