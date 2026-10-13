package org.ursc.trajectory.forces.drag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * {@link Jb2008Inputs} backed by the Space Environment Technologies SOLFSMY and DTCFILE text files
 * (as distributed with orekit-data).
 *
 * <p>SOLFSMY rows are {@code YYYY DDD JulianDay F10 F81c S10 S81c M10 M81c Y10 Y81c Ssrc}, reported
 * at 12&nbsp;UT; the JB2008 tabular lags are applied by shifting the query date (F10/S10 −1 day,
 * M10 −2 days, Y10 −5 days) and linearly interpolating between daily entries. DTCFILE rows are
 * {@code DTC YYYY DDD} followed by 24 hourly ΔTc values; the correction is linearly interpolated
 * between hourly entries with no lag. This mirrors the Orekit JB2008 data loaders.</p>
 */
public final class SolfsmyDtcProvider implements Jb2008Inputs {

    private static final double DAY = 86400.0;

    /** Reference epoch for keying (arbitrary, fixed). */
    private final AbsoluteDate ref;

    // SOLFSMY: sorted times (s from ref) and 8 index columns (F10,F81,S10,S81,M10,M81,Y10,Y81)
    private final double[] solT;
    private final double[][] solV;

    // DTCFILE: sorted times and hourly dTc
    private final double[] dtcT;
    private final double[] dtcV;

    public SolfsmyDtcProvider(final InputStream solfsmy, final InputStream dtc) throws IOException {
        this.ref = new AbsoluteDate(2000, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());

        final List<double[]> sol = parseSolfsmy(solfsmy);
        solT = new double[sol.size()];
        solV = new double[8][sol.size()];
        for (int i = 0; i < sol.size(); i++) {
            final double[] row = sol.get(i);
            solT[i] = row[0];
            for (int c = 0; c < 8; c++) {
                solV[c][i] = row[1 + c];
            }
        }

        final List<double[]> dt = parseDtc(dtc);
        dtcT = new double[dt.size()];
        dtcV = new double[dt.size()];
        for (int i = 0; i < dt.size(); i++) {
            dtcT[i] = dt.get(i)[0];
            dtcV[i] = dt.get(i)[1];
        }
    }

    public static SolfsmyDtcProvider fromPaths(final Path solfsmy, final Path dtc) throws IOException {
        try (InputStream s = Files.newInputStream(solfsmy); InputStream d = Files.newInputStream(dtc)) {
            return new SolfsmyDtcProvider(s, d);
        }
    }

    /** Load from the gzipped SOLFSMY/DTCFILE snapshots bundled on the classpath under {@code /drag/}. */
    public static SolfsmyDtcProvider bundled() {
        final String sol = "/drag/SOLFSMY.TXT.gz";
        final String dtc = "/drag/DTCFILE.TXT.gz";
        try (InputStream sRaw = SolfsmyDtcProvider.class.getResourceAsStream(sol);
             InputStream dRaw = SolfsmyDtcProvider.class.getResourceAsStream(dtc)) {
            if (sRaw == null || dRaw == null) {
                throw new IllegalStateException("bundled JB2008 data not found: " + sol + " / " + dtc);
            }
            try (InputStream s = new GZIPInputStream(sRaw); InputStream d = new GZIPInputStream(dRaw)) {
                return new SolfsmyDtcProvider(s, d);
            }
        } catch (final IOException e) {
            throw new IllegalStateException("cannot read bundled JB2008 data", e);
        }
    }

    // ---- parsing ----

    /** @return list of {key, F10, F81, S10, S81, M10, M81, Y10, Y81}, sorted by time. */
    private List<double[]> parseSolfsmy(final InputStream in) throws IOException {
        final List<double[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                final String t = line.trim();
                if (t.isEmpty() || t.charAt(0) == '#') {
                    continue;
                }
                final String[] f = t.split("\\s+");
                if (f.length < 11) {
                    continue;
                }
                final Integer year = asInt(f[0]);
                final Integer doy = asInt(f[1]);
                if (year == null || doy == null) {
                    continue;
                }
                // entry stamped at 12 UT on (year, doy)
                final AbsoluteDate epoch = new AbsoluteDate(year, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC())
                        .shiftedBy((doy - 1) * DAY);
                out.add(new double[] {
                    epoch.durationFrom(ref),
                    Double.parseDouble(f[3]), Double.parseDouble(f[4]),
                    Double.parseDouble(f[5]), Double.parseDouble(f[6]),
                    Double.parseDouble(f[7]), Double.parseDouble(f[8]),
                    Double.parseDouble(f[9]), Double.parseDouble(f[10])
                });
            }
        }
        out.sort((a, b) -> Double.compare(a[0], b[0]));
        return out;
    }

    /** @return list of {key, dTc}, sorted by time (one per hour). */
    private List<double[]> parseDtc(final InputStream in) throws IOException {
        final List<double[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                final String t = line.trim();
                if (t.isEmpty() || !t.startsWith("DTC")) {
                    continue;
                }
                final String[] f = t.split("\\s+");
                if (f.length < 27) {
                    continue;
                }
                final Integer year = asInt(f[1]);
                final Integer doy = asInt(f[2]);
                if (year == null || doy == null) {
                    continue;
                }
                final AbsoluteDate day0 = new AbsoluteDate(year, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC())
                        .shiftedBy((doy - 1) * DAY);
                for (int hr = 0; hr < 24; hr++) {
                    out.add(new double[] {day0.shiftedBy(hr * 3600.0).durationFrom(ref),
                                          Double.parseDouble(f[3 + hr])});
                }
            }
        }
        out.sort((a, b) -> Double.compare(a[0], b[0]));
        return out;
    }

    private static Integer asInt(final String s) {
        try {
            return Integer.valueOf(s);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    // ---- interpolation ----

    private double interp(final double[] times, final double[] values, final double key) {
        final int n = times.length;
        if (n == 0) {
            return Double.NaN;
        }
        if (key <= times[0]) {
            return values[0];
        }
        if (key >= times[n - 1]) {
            return values[n - 1];
        }
        int lo = 0;
        int hi = n - 1;
        while (hi - lo > 1) {
            final int mid = (lo + hi) >>> 1;
            if (times[mid] <= key) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        final double w = (key - times[lo]) / (times[hi] - times[lo]);
        return values[lo] * (1.0 - w) + values[hi] * w;
    }

    private double sol(final int col, final AbsoluteDate date, final double lagDays) {
        final double key = date.shiftedBy(-lagDays * DAY).durationFrom(ref);
        return interp(solT, solV[col], key);
    }

    @Override public double getF10(final AbsoluteDate d)   { return sol(0, d, 1.0); }
    @Override public double getF10B(final AbsoluteDate d)  { return sol(1, d, 1.0); }
    @Override public double getS10(final AbsoluteDate d)   { return sol(2, d, 1.0); }
    @Override public double getS10B(final AbsoluteDate d)  { return sol(3, d, 1.0); }
    @Override public double getXM10(final AbsoluteDate d)  { return sol(4, d, 2.0); }
    @Override public double getXM10B(final AbsoluteDate d) { return sol(5, d, 2.0); }
    @Override public double getY10(final AbsoluteDate d)   { return sol(6, d, 5.0); }
    @Override public double getY10B(final AbsoluteDate d)  { return sol(7, d, 5.0); }

    @Override
    public double getDSTDTC(final AbsoluteDate d) {
        return interp(dtcT, dtcV, d.durationFrom(ref));
    }
}
