package org.ursc.trajectory.forces.drag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Space-weather provider backed by a CelesTrak CSSI {@code SW-All.csv} /
 * {@code SW-Last5Years.csv} file, giving historical and near-term F10.7 and Ap
 * for realistic long-term LEO decay. This is the key input that makes
 * multi-year lifetime predictions track reality.
 *
 * <p>Columns are located by their header names, so either CelesTrak CSV variant
 * works. Per NRLMSISE-00 convention the daily F10.7 is the <em>previous</em> day's
 * observed flux, the average is the 81-day centred observed flux, and the
 * magnetic index is the daily Ap average. Dates outside the file, or missing
 * values, fall back to a configurable constant.</p>
 */
public final class CssiSpaceWeatherProvider implements SpaceWeatherProvider {

    private static final class Entry {
        double f107Obs = Double.NaN;
        double f107Center81 = Double.NaN;
        double apAvg = Double.NaN;
    }

    private final Map<String, Entry> byDate = new HashMap<>();
    private final SpaceWeatherProvider fallback;

    public CssiSpaceWeatherProvider(final Path csvFile, final SpaceWeatherProvider fallback)
            throws IOException {
        this.fallback = fallback;
        try (InputStream in = Files.newInputStream(csvFile)) {
            parse(in);
        }
    }

    public CssiSpaceWeatherProvider(final InputStream csv, final SpaceWeatherProvider fallback)
            throws IOException {
        this.fallback = fallback;
        parse(csv);
    }

    private void parse(final InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            final String header = reader.readLine();
            if (header == null) {
                return;
            }
            final String[] cols = header.split(",");
            int cDate = -1;
            int cF107Obs = -1;
            int cF107C81 = -1;
            int cApAvg = -1;
            for (int i = 0; i < cols.length; i++) {
                final String name = cols[i].trim().toUpperCase(Locale.US);
                switch (name) {
                    case "DATE": cDate = i; break;
                    case "F10.7_OBS": cF107Obs = i; break;
                    case "F10.7_OBS_CENTER81": cF107C81 = i; break;
                    case "AP_AVG": cApAvg = i; break;
                    default: break;
                }
            }
            if (cDate < 0) {
                throw new IOException("space-weather CSV missing a DATE column");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                final String[] f = line.split(",", -1);
                if (f.length <= cDate) {
                    continue;
                }
                final String date = f[cDate].trim();
                if (date.isEmpty()) {
                    continue;
                }
                final Entry e = new Entry();
                e.f107Obs = num(f, cF107Obs);
                e.f107Center81 = num(f, cF107C81);
                e.apAvg = num(f, cApAvg);
                byDate.put(date, e);
            }
        }
    }

    private static double num(final String[] f, final int idx) {
        if (idx < 0 || idx >= f.length) {
            return Double.NaN;
        }
        final String s = f[idx].trim();
        if (s.isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(s);
        } catch (final NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private static String key(final AbsoluteDate date) {
        final double[] c = date.getComponents(TimeScalesFactory.getUTC());
        return String.format(Locale.US, "%04d-%02d-%02d", (int) c[0], (int) c[1], (int) c[2]);
    }

    @Override
    public double getDailyF107(final AbsoluteDate date) {
        // previous day's observed flux
        final Entry e = byDate.get(key(date.shiftedBy(-86400.0)));
        if (e != null && !Double.isNaN(e.f107Obs)) {
            return e.f107Obs;
        }
        return fallback.getDailyF107(date);
    }

    @Override
    public double getAverageF107(final AbsoluteDate date) {
        final Entry e = byDate.get(key(date));
        if (e != null && !Double.isNaN(e.f107Center81)) {
            return e.f107Center81;
        }
        return fallback.getAverageF107(date);
    }

    @Override
    public double getDailyAp(final AbsoluteDate date) {
        final Entry e = byDate.get(key(date));
        if (e != null && !Double.isNaN(e.apAvg)) {
            return e.apAvg;
        }
        return fallback.getDailyAp(date);
    }
}
