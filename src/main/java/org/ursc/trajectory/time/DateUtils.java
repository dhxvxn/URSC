package org.ursc.trajectory.time;

/**
 * Low-level proleptic-Gregorian calendar arithmetic shared by the time classes.
 *
 * <p>Everything is expressed relative to the reference instant
 * {@code 2000-01-01T12:00:00} (the J2000 noon epoch) so that integer day
 * arithmetic stays exact.</p>
 */
final class DateUtils {

    /** Julian Day Number of 2000-01-01 (noon), i.e. J2000.0. */
    static final long J2000_JDN = 2451545L;

    private DateUtils() {
    }

    /** Julian Day Number at noon of a proleptic-Gregorian date. */
    static long julianDayNumber(final int year, final int month, final int day) {
        final long y = year;
        final long m = month;
        final long d = day;
        return (1461 * (y + 4800 + (m - 14) / 12)) / 4
                + (367 * (m - 2 - 12 * ((m - 14) / 12))) / 12
                - (3 * ((y + 4900 + (m - 14) / 12) / 100)) / 4
                + d - 32075;
    }

    /**
     * Seconds elapsed from the reference 2000-01-01T12:00:00 to the given
     * calendar reading (treated as a reading on a continuous scale).
     */
    static double secondsSinceReference(final int year, final int month, final int day,
                                        final int hour, final int minute, final double second) {
        final long days = julianDayNumber(year, month, day) - J2000_JDN;
        return days * 86400.0 + (hour - 12) * 3600.0 + minute * 60.0 + second;
    }

    /**
     * Convert seconds-since-reference back into calendar components. The result
     * array is {year, month, day, hour, minute} as integers plus the fractional
     * seconds returned separately via {@code secondsOut[0]}.
     */
    static int[] toComponents(final double secondsSinceReference, final double[] secondsOut) {
        // Split into an integer day count (from noon) and seconds within the day.
        long dayFromNoon = (long) Math.floor(secondsSinceReference / 86400.0);
        double secInDay = secondsSinceReference - dayFromNoon * 86400.0;

        // secInDay is measured from noon; shift so that 0 == midnight of the civil day.
        double secFromMidnight = secInDay + 43200.0; // +12h
        if (secFromMidnight >= 86400.0) {
            secFromMidnight -= 86400.0;
            dayFromNoon += 1;
        }

        final long jdn = J2000_JDN + dayFromNoon;
        final int[] ymd = fromJulianDayNumber(jdn);

        final int hour = (int) Math.floor(secFromMidnight / 3600.0);
        double rem = secFromMidnight - hour * 3600.0;
        final int minute = (int) Math.floor(rem / 60.0);
        final double seconds = rem - minute * 60.0;

        secondsOut[0] = seconds;
        return new int[] {ymd[0], ymd[1], ymd[2], hour, minute};
    }

    /** Civil (year, month, day) from a Julian Day Number (noon-based). */
    static int[] fromJulianDayNumber(final long jdn) {
        long l = jdn + 68569L;
        final long n = (4 * l) / 146097L;
        l = l - (146097L * n + 3L) / 4L;
        final long i = (4000L * (l + 1)) / 1461001L;
        l = l - (1461L * i) / 4L + 31L;
        final long j = (80L * l) / 2447L;
        final long day = l - (2447L * j) / 80L;
        l = j / 11L;
        final long month = j + 2L - 12L * l;
        final long year = 100L * (n - 49L) + i + l;
        return new int[] {(int) year, (int) month, (int) day};
    }
}
