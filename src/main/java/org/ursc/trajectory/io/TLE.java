package org.ursc.trajectory.io;

import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.PositionAngle;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Parser for the classical Two-Line Element (TLE) set, used to seed a propagation
 * from a catalogue element set.
 *
 * <p>Note: the mean elements in a TLE are defined in the SGP4/TEME system. This
 * class extracts those elements and builds an osculating {@link KeplerianOrbit}
 * in GCRF as an <em>approximate</em> initial state (no SGP4 recovery of the
 * Brouwer mean-to-osculating transformation). That is adequate for seeding a
 * numerical propagation whose own force models then take over; for high accuracy,
 * supply a precise osculating state instead.</p>
 */
public final class TLE {

    private final double inclination;   // rad
    private final double raan;          // rad
    private final double eccentricity;
    private final double argOfPerigee;  // rad
    private final double meanAnomaly;   // rad
    private final double meanMotion;    // rad/s
    private final AbsoluteDate epoch;

    private TLE(final double inclination, final double raan, final double eccentricity,
                final double argOfPerigee, final double meanAnomaly, final double meanMotion,
                final AbsoluteDate epoch) {
        this.inclination = inclination;
        this.raan = raan;
        this.eccentricity = eccentricity;
        this.argOfPerigee = argOfPerigee;
        this.meanAnomaly = meanAnomaly;
        this.meanMotion = meanMotion;
        this.epoch = epoch;
    }

    public static TLE parse(final String line1, final String line2) {
        // Epoch is in columns 19-32 of line 1: 2-digit year + fractional day of year.
        final double epochField = Double.parseDouble(line1.substring(18, 32).trim());
        final int twoDigitYear = (int) (epochField / 1000.0);
        final double dayOfYear = epochField - twoDigitYear * 1000.0;
        final int year = (twoDigitYear < 57) ? 2000 + twoDigitYear : 1900 + twoDigitYear;
        final AbsoluteDate epoch = dateFromDayOfYear(year, dayOfYear);

        final double inc = Math.toRadians(Double.parseDouble(line2.substring(8, 16).trim()));
        final double raan = Math.toRadians(Double.parseDouble(line2.substring(17, 25).trim()));
        final double ecc = Double.parseDouble("0." + line2.substring(26, 33).trim());
        final double argP = Math.toRadians(Double.parseDouble(line2.substring(34, 42).trim()));
        final double meanAnom = Math.toRadians(Double.parseDouble(line2.substring(43, 51).trim()));
        final double revsPerDay = Double.parseDouble(line2.substring(52, 63).trim());
        final double meanMotion = revsPerDay * Constants.TWO_PI / Constants.JULIAN_DAY;

        return new TLE(inc, raan, ecc, argP, meanAnom, meanMotion, epoch);
    }

    /** @return an approximate osculating Keplerian orbit in GCRF (see class note). */
    public KeplerianOrbit toKeplerianOrbit() {
        return toKeplerianOrbit(FramesFactory.getGCRF(), Constants.EARTH_MU);
    }

    public KeplerianOrbit toKeplerianOrbit(final Frame frame, final double mu) {
        final double a = Math.cbrt(mu / (meanMotion * meanMotion));
        return new KeplerianOrbit(a, eccentricity, inclination, argOfPerigee, raan,
                meanAnomaly, PositionAngle.MEAN, frame, epoch, mu);
    }

    public AbsoluteDate getEpoch() {
        return epoch;
    }

    private static AbsoluteDate dateFromDayOfYear(final int year, final double dayOfYear) {
        final AbsoluteDate yearStart =
                new AbsoluteDate(year, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        // dayOfYear is 1-based (day 1.0 == Jan 1 00:00)
        return yearStart.shiftedBy((dayOfYear - 1.0) * Constants.JULIAN_DAY);
    }
}
