package org.ursc.trajectory.pipeline;

import java.util.Locale;

import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/** ISO-8601 UTC parsing and formatting for configs, OEM and the GUI. */
public final class TimeUtil {

    private TimeUtil() {
    }

    /** Parse {@code YYYY-MM-DD[THH:MM[:SS[.sss]]][Z]} (also a space instead of T) as UTC. */
    public static AbsoluteDate parseUtc(final String iso) {
        if (iso == null || iso.isBlank()) {
            throw new IllegalArgumentException("UTC time is empty");
        }
        String s = iso.trim().replace(' ', 'T');
        if (s.endsWith("Z")) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            final String[] dt = s.split("T");
            final String[] d = dt[0].split("-");
            int h = 0;
            int mi = 0;
            double sec = 0.0;
            if (dt.length > 1) {
                final String[] t = dt[1].split(":");
                h = Integer.parseInt(t[0]);
                mi = t.length > 1 ? Integer.parseInt(t[1]) : 0;
                sec = t.length > 2 ? Double.parseDouble(t[2]) : 0.0;
            }
            return new AbsoluteDate(Integer.parseInt(d[0]), Integer.parseInt(d[1]), Integer.parseInt(d[2]),
                    h, mi, sec, TimeScalesFactory.getUTC());
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("not an ISO-8601 UTC time: '" + iso + "'", e);
        }
    }

    /** Format as {@code YYYY-MM-DDTHH:MM:SS.ffffff} UTC (OEM/CCSDS style, no zone suffix). */
    public static String formatUtc(final AbsoluteDate date) {
        final double[] c = date.getComponents(TimeScalesFactory.getUTC());
        return String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d:%09.6f",
                (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], c[5]);
    }
}
