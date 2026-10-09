package org.ursc.trajectory.time;

/**
 * Coordinated Universal Time, maintained within one second of UT1 by the
 * insertion of leap seconds. {@code UTC = TAI - (TAI-UTC)}, so the offset from
 * TAI is the negated, accumulated leap-second count.
 *
 * <p>The full modern leap-second history (1972-2017) is embedded. Dates after
 * the last entry use the latest value (37 s), which is current as of 2026.</p>
 */
public final class UTCScale implements TimeScale {

    /** Leap-second insertion dates (UTC midnight) and the resulting TAI-UTC value. */
    private static final int[][] LEAP_DATES = {
            {1972, 1, 1}, {1972, 7, 1}, {1973, 1, 1}, {1974, 1, 1}, {1975, 1, 1},
            {1976, 1, 1}, {1977, 1, 1}, {1978, 1, 1}, {1979, 1, 1}, {1980, 1, 1},
            {1981, 7, 1}, {1982, 7, 1}, {1983, 7, 1}, {1985, 7, 1}, {1988, 1, 1},
            {1990, 1, 1}, {1991, 1, 1}, {1992, 7, 1}, {1993, 7, 1}, {1994, 7, 1},
            {1996, 1, 1}, {1997, 7, 1}, {1999, 1, 1}, {2006, 1, 1}, {2009, 1, 1},
            {2012, 7, 1}, {2015, 7, 1}, {2017, 1, 1}
    };

    /** TAI-UTC value (seconds) taking effect at the corresponding LEAP_DATES entry. */
    private static final int[] LEAP_VALUES = {
            10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
            30, 31, 32, 33, 34, 35, 36, 37
    };

    /** Threshold instants, as UTC seconds since the J2000 reference, for each entry. */
    private final double[] thresholdsUtcSeconds;

    UTCScale() {
        thresholdsUtcSeconds = new double[LEAP_DATES.length];
        for (int i = 0; i < LEAP_DATES.length; i++) {
            thresholdsUtcSeconds[i] = DateUtils.secondsSinceReference(
                    LEAP_DATES[i][0], LEAP_DATES[i][1], LEAP_DATES[i][2], 0, 0, 0.0);
        }
    }

    @Override
    public double offsetFromTAI(final AbsoluteDate date) {
        // taiOffset is seconds on the TAI timeline since the reference instant.
        final double tai = date.getTAIOffset();
        // Iterate once or twice to resolve the circular dependency UTC <-> leap count.
        double leap = LEAP_VALUES[LEAP_VALUES.length - 1];
        for (int iter = 0; iter < 3; iter++) {
            final double utc = tai - leap;
            final double newLeap = leapForUtcSeconds(utc);
            if (newLeap == leap) {
                break;
            }
            leap = newLeap;
        }
        return -leap;
    }

    @Override
    public String getName() {
        return "UTC";
    }

    /** TAI-UTC (seconds) applicable to a reading expressed as UTC seconds since reference. */
    double leapForUtcSeconds(final double utcSeconds) {
        int leap = 10; // value before 1972 is treated as the 1972 baseline
        for (int i = 0; i < thresholdsUtcSeconds.length; i++) {
            if (utcSeconds >= thresholdsUtcSeconds[i]) {
                leap = LEAP_VALUES[i];
            } else {
                break;
            }
        }
        return leap;
    }

    /** TAI-UTC (seconds) applicable to a UTC calendar date, used at construction time. */
    double leapForUtcDate(final int year, final int month, final int day) {
        final double utc = DateUtils.secondsSinceReference(year, month, day, 0, 0, 0.0);
        return leapForUtcSeconds(utc);
    }
}
