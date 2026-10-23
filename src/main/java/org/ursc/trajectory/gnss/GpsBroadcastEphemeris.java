package org.ursc.trajectory.gnss;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * One GPS broadcast-ephemeris message for a single satellite, and the IS-GPS-200
 * algorithm to evaluate the satellite's Earth-fixed (WGS-84 ECEF) position and
 * clock offset at a requested time.
 *
 * <p>The clock offset includes the relativistic eccentricity correction but
 * <em>not</em> the group delay (TGD): point positioning here uses the
 * ionosphere-free code combination, in which TGD cancels. The returned position is
 * in the ECEF frame at the requested (transmit) epoch; the Earth-rotation (Sagnac)
 * correction over the signal travel time to the receiver is applied by the caller.</p>
 */
public final class GpsBroadcastEphemeris {

    private static final double WE = GpsConstants.EARTH_ROTATION_RATE;
    private static final double HALF_WEEK = 302400.0;
    private static final double WEEK = 604800.0;

    private final int prn;
    private final AbsoluteDate toc;    // clock reference epoch
    private final AbsoluteDate toe;    // ephemeris reference epoch
    private final double toeSow;       // Toe as GPS seconds of week
    private final double af0;
    private final double af1;
    private final double af2;
    private final double sqrtA;
    private final double e;
    private final double m0;
    private final double deltaN;
    private final double omega0;
    private final double i0;
    private final double omega;
    private final double omegaDot;
    private final double iDot;
    private final double cuc;
    private final double cus;
    private final double crc;
    private final double crs;
    private final double cic;
    private final double cis;

    /** Full broadcast-message constructor (SI units, radians). */
    public GpsBroadcastEphemeris(final int prn, final AbsoluteDate toc, final AbsoluteDate toe,
            final double toeSow, final double af0, final double af1, final double af2,
            final double sqrtA, final double e, final double m0, final double deltaN,
            final double omega0, final double i0, final double omega, final double omegaDot,
            final double iDot, final double cuc, final double cus, final double crc,
            final double crs, final double cic, final double cis) {
        this.prn = prn;
        this.toc = toc;
        this.toe = toe;
        this.toeSow = toeSow;
        this.af0 = af0;
        this.af1 = af1;
        this.af2 = af2;
        this.sqrtA = sqrtA;
        this.e = e;
        this.m0 = m0;
        this.deltaN = deltaN;
        this.omega0 = omega0;
        this.i0 = i0;
        this.omega = omega;
        this.omegaDot = omegaDot;
        this.iDot = iDot;
        this.cuc = cuc;
        this.cus = cus;
        this.crc = crc;
        this.crs = crs;
        this.cic = cic;
        this.cis = cis;
    }

    public int getPrn() {
        return prn;
    }

    /** @return the ephemeris reference epoch (Toe), for selecting the freshest message. */
    public AbsoluteDate getToe() {
        return toe;
    }

    /** Time from the ephemeris reference epoch, with GPS week rollover removed (s). */
    private double tk(final AbsoluteDate t) {
        double dt = t.durationFrom(toe);
        if (dt > HALF_WEEK) {
            dt -= WEEK;
        } else if (dt < -HALF_WEEK) {
            dt += WEEK;
        }
        return dt;
    }

    /** Eccentric anomaly at time {@code t} (Newton iteration on Kepler's equation). */
    private double eccentricAnomaly(final double tk) {
        final double a = sqrtA * sqrtA;
        final double n = Math.sqrt(GpsConstants.GM / (a * a * a)) + deltaN;
        final double mk = m0 + n * tk;
        double ek = mk;
        for (int i = 0; i < 20; i++) {
            final double d = (ek - e * Math.sin(ek) - mk) / (1.0 - e * Math.cos(ek));
            ek -= d;
            if (Math.abs(d) < 1.0e-13) {
                break;
            }
        }
        return ek;
    }

    /**
     * @param t transmit time (GPS)
     * @return the satellite ECEF (WGS-84) position (m) at {@code t}
     */
    public Vector3D positionEcef(final AbsoluteDate t) {
        final double tk = tk(t);
        final double a = sqrtA * sqrtA;
        final double ek = eccentricAnomaly(tk);

        final double nu = Math.atan2(Math.sqrt(1.0 - e * e) * Math.sin(ek), Math.cos(ek) - e);
        final double phi = nu + omega;
        final double s2 = Math.sin(2.0 * phi);
        final double c2 = Math.cos(2.0 * phi);

        final double uk = phi + cus * s2 + cuc * c2;
        final double rk = a * (1.0 - e * Math.cos(ek)) + crs * s2 + crc * c2;
        final double ik = i0 + iDot * tk + cis * s2 + cic * c2;

        final double xp = rk * Math.cos(uk);
        final double yp = rk * Math.sin(uk);

        final double omegaK = omega0 + (omegaDot - WE) * tk - WE * toeSow;
        final double cosO = Math.cos(omegaK);
        final double sinO = Math.sin(omegaK);
        final double cosI = Math.cos(ik);
        final double sinI = Math.sin(ik);

        return new Vector3D(
                xp * cosO - yp * cosI * sinO,
                xp * sinO + yp * cosI * cosO,
                yp * sinI);
    }

    /**
     * @param t transmit time (GPS)
     * @return the satellite clock offset (s) at {@code t} (polynomial + relativistic term;
     *         group delay excluded, as the ionosphere-free combination removes it)
     */
    public double clockBias(final AbsoluteDate t) {
        final double dtClock = t.durationFrom(toc);
        final double ek = eccentricAnomaly(tk(t));
        final double relativistic = GpsConstants.RELATIVISTIC_F * e * sqrtA * Math.sin(ek);
        return af0 + af1 * dtClock + af2 * dtClock * dtClock + relativistic;
    }
}
