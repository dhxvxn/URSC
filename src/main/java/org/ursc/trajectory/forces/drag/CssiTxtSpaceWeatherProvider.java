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
 * Space-weather provider backed by the AGI/Orekit CSSI space-weather text file
 * ({@code SpaceWeather-All-v1.2.txt}, header {@code DATATYPE CssiSpaceWeather}).
 *
 * <p>This is the fixed-column format distributed with orekit-data, distinct from the
 * CelesTrak {@code SW-All.csv} handled by {@link CssiSpaceWeatherProvider}. Only the
 * daily-cadence {@code OBSERVED} and {@code DAILY_PREDICTED} sections are read. Each data
 * line is whitespace-separated as {@code yy mm dd BSRN ND Kp(8) KpSum ap(8) ApAvg Cp C9
 * ISN F10.7adj Q Ctr81adj Lst81adj F10.7obs Ctr81obs Lst81obs}; per NRLMSISE-00 convention
 * the daily F10.7 is the <em>previous</em> day's observed flux, the average is the observed
 * 81-day centred flux, and the magnetic index is the daily Ap average. Missing dates fall
 * back to a configurable constant.</p>
 */
public final class CssiTxtSpaceWeatherProvider implements SpaceWeatherProvider {

    // zero-based token indices (see class javadoc)
    private static final int I_YEAR = 0;
    private static final int I_MONTH = 1;
    private static final int I_DAY = 2;
    private static final int I_AP_AVG = 22;
    private static final int I_F107_OBS = 30;
    private static final int I_F107_CTR81_OBS = 31;
    private static final int MIN_TOKENS = 33;

    private static final class Entry {
        double f107Obs = Double.NaN;
        double f107Center81 = Double.NaN;
        double apAvg = Double.NaN;
    }

    private final Map<String, Entry> byDate = new HashMap<>();
    private final SpaceWeatherProvider fallback;

    public CssiTxtSpaceWeatherProvider(final Path file, final SpaceWeatherProvider fallback)
            throws IOException {
        this.fallback = fallback;
        try (InputStream in = Files.newInputStream(file)) {
            parse(in);
        }
    }

    public CssiTxtSpaceWeatherProvider(final InputStream in, final SpaceWeatherProvider fallback)
            throws IOException {
        this.fallback = fallback;
        parse(in);
    }

    private void parse(final InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean inDaily = false;
            while ((line = reader.readLine()) != null) {
                final String t = line.trim();
                if (t.startsWith("BEGIN OBSERVED") || t.startsWith("BEGIN DAILY_PREDICTED")) {
                    inDaily = true;
                    continue;
                }
                if (t.startsWith("END ")) {
                    inDaily = false;
                    continue;
                }
                if (!inDaily || t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                final String[] f = t.split("\\s+");
                if (f.length < MIN_TOKENS) {
                    continue;
                }
                final Integer y = asInt(f[I_YEAR]);
                final Integer mo = asInt(f[I_MONTH]);
                final Integer d = asInt(f[I_DAY]);
                if (y == null || mo == null || d == null) {
                    continue;
                }
                final Entry e = new Entry();
                e.apAvg = num(f[I_AP_AVG]);
                e.f107Obs = num(f[I_F107_OBS]);
                e.f107Center81 = num(f[I_F107_CTR81_OBS]);
                byDate.put(String.format(Locale.US, "%04d-%02d-%02d", y, mo, d), e);
            }
        }
    }

    private static Integer asInt(final String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    private static double num(final String s) {
        try {
            return Double.parseDouble(s.trim());
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
