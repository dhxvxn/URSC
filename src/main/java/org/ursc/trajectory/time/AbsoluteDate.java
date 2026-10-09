package org.ursc.trajectory.time;

import java.util.Locale;

/**
 * An absolute point on the time line, stored to sub-microsecond precision as a
 * count of SI seconds from the {@code 2000-01-01T12:00:00 TAI} reference instant.
 *
 * <p>The internal representation is a uniform atomic timeline, so durations
 * between dates are leap-second-free. Calendar readings are obtained for any
 * {@link TimeScale} on demand.</p>
 */
public final class AbsoluteDate implements Comparable<AbsoluteDate> {

    /** The J2000.0 epoch: 2000-01-01T12:00:00 TT. */
    public static final AbsoluteDate J2000_EPOCH = new AbsoluteDate(-32.184);

    /** Seconds on the TAI timeline since 2000-01-01T12:00:00 TAI. */
    private final double taiOffset;

    private AbsoluteDate(final double taiOffset) {
        this.taiOffset = taiOffset;
    }

    /**
     * Build a date from a calendar reading in a given time scale.
     *
     * @param year   proleptic-Gregorian year
     * @param month  month, 1-12
     * @param day    day of month, 1-31
     * @param hour   hour, 0-23
     * @param minute minute, 0-59
     * @param second seconds (may include a fractional part and leap second 60)
     * @param scale  the scale in which the reading is expressed
     */
    public AbsoluteDate(final int year, final int month, final int day,
                        final int hour, final int minute, final double second,
                        final TimeScale scale) {
        final double secondsInScale =
                DateUtils.secondsSinceReference(year, month, day, hour, minute, second);
        final double offset;
        if (scale instanceof UTCScale) {
            // Resolve leap seconds directly from the UTC reading. The generic path
            // below would mis-resolve the count within one minute of a leap-second
            // boundary, because it interprets the calendar reading as a TAI offset.
            offset = -((UTCScale) scale).leapForUtcSeconds(secondsInScale);
        } else {
            // Provisional date to evaluate the (possibly instant-dependent) scale offset.
            offset = scale.offsetFromTAI(new AbsoluteDate(secondsInScale));
        }
        this.taiOffset = secondsInScale - offset;
    }

    /** Build a date at midnight (00:00:00) of a calendar day in the given scale. */
    public AbsoluteDate(final int year, final int month, final int day, final TimeScale scale) {
        this(year, month, day, 0, 0, 0.0, scale);
    }

    /** @return a new date shifted by {@code dt} SI seconds (may be negative). */
    public AbsoluteDate shiftedBy(final double dt) {
        return new AbsoluteDate(taiOffset + dt);
    }

    /** @return the SI seconds elapsed from {@code other} to this date. */
    public double durationFrom(final AbsoluteDate other) {
        return taiOffset - other.taiOffset;
    }

    /** Package-private accessor for time scales. */
    double getTAIOffset() {
        return taiOffset;
    }

    /** @return seconds elapsed since the J2000.0 (TT) epoch on the uniform timeline. */
    public double durationFromJ2000() {
        return durationFrom(J2000_EPOCH);
    }

    /** @return Julian centuries of Terrestrial Time elapsed since J2000.0. */
    public double julianCenturiesTT() {
        return durationFromJ2000() / (86400.0 * 36525.0);
    }

    /**
     * @param scale the scale in which the Julian Date is expressed
     * @return the Julian Date (days) of this instant in the given scale
     */
    public double julianDate(final TimeScale scale) {
        final double secondsInScale = taiOffset + scale.offsetFromTAI(this);
        return 2451545.0 + secondsInScale / 86400.0;
    }

    /**
     * @param scale the output scale
     * @return calendar components {year, month, day, hour, minute, seconds(as whole), fracMillis}
     *         — see {@link #toString(TimeScale)} for a formatted reading
     */
    public double[] getComponents(final TimeScale scale) {
        final double secondsInScale = taiOffset + scale.offsetFromTAI(this);
        final double[] secOut = new double[1];
        final int[] ymdhm = DateUtils.toComponents(secondsInScale, secOut);
        return new double[] {ymdhm[0], ymdhm[1], ymdhm[2], ymdhm[3], ymdhm[4], secOut[0]};
    }

    @Override
    public int compareTo(final AbsoluteDate o) {
        return Double.compare(taiOffset, o.taiOffset);
    }

    public boolean isBefore(final AbsoluteDate o) {
        return taiOffset < o.taiOffset;
    }

    public boolean isAfter(final AbsoluteDate o) {
        return taiOffset > o.taiOffset;
    }

    /** ISO-8601 formatted reading in the given scale. */
    public String toString(final TimeScale scale) {
        final double[] c = getComponents(scale);
        return String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d:%06.3f %s",
                (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], c[5], scale.getName());
    }

    @Override
    public String toString() {
        return toString(TimeScalesFactory.getUTC());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbsoluteDate)) {
            return false;
        }
        return Double.compare(((AbsoluteDate) o).taiOffset, taiOffset) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(taiOffset);
    }
}
