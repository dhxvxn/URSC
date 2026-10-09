package org.ursc.trajectory.orbits;

import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Equinoctial elements, free of the singularities that Keplerian elements have at
 * zero eccentricity and zero inclination (the common case for operational LEO).
 *
 * <ul>
 *   <li>{@code a}  &mdash; semi-major axis</li>
 *   <li>{@code ex = e cos(pa + raan)}, {@code ey = e sin(pa + raan)}</li>
 *   <li>{@code hx = tan(i/2) cos(raan)}, {@code hy = tan(i/2) sin(raan)}</li>
 *   <li>{@code lv} &mdash; true longitude = pa + raan + true anomaly</li>
 * </ul>
 */
public final class EquinoctialOrbit extends Orbit {

    private final double a;
    private final double ex;
    private final double ey;
    private final double hx;
    private final double hy;
    private final double lv;

    public EquinoctialOrbit(final double a, final double ex, final double ey,
                            final double hx, final double hy, final double lv,
                            final Frame frame, final AbsoluteDate date, final double mu) {
        super(frame, date, mu);
        this.a = a;
        this.ex = ex;
        this.ey = ey;
        this.hx = hx;
        this.hy = hy;
        this.lv = lv;
    }

    /** Convert any orbit into equinoctial elements (through Keplerian elements). */
    public EquinoctialOrbit(final Orbit orbit) {
        super(orbit.getFrame(), orbit.getDate(), orbit.getMu());
        final KeplerianOrbit k = (orbit instanceof KeplerianOrbit)
                ? (KeplerianOrbit) orbit : new KeplerianOrbit(orbit);
        final double raan = k.getRightAscensionOfAscendingNode();
        final double pa = k.getPerigeeArgument();
        final double ecc = k.getE();
        this.a = k.getA();
        this.ex = ecc * Math.cos(pa + raan);
        this.ey = ecc * Math.sin(pa + raan);
        final double tanHalfI = Math.tan(k.getI() / 2.0);
        this.hx = tanHalfI * Math.cos(raan);
        this.hy = tanHalfI * Math.sin(raan);
        this.lv = pa + raan + k.getTrueAnomaly();
    }

    @Override
    public OrbitType getType() {
        return OrbitType.EQUINOCTIAL;
    }

    public double getEquinoctialEx() {
        return ex;
    }

    public double getEquinoctialEy() {
        return ey;
    }

    public double getHx() {
        return hx;
    }

    public double getHy() {
        return hy;
    }

    public double getTrueLongitude() {
        return lv;
    }

    @Override
    public double getA() {
        return a;
    }

    @Override
    public double getE() {
        return Math.sqrt(ex * ex + ey * ey);
    }

    @Override
    public double getI() {
        return 2.0 * Math.atan(Math.sqrt(hx * hx + hy * hy));
    }

    @Override
    public PVCoordinates getPVCoordinates() {
        return toKeplerian().getPVCoordinates();
    }

    /** @return the equivalent Keplerian representation. */
    public KeplerianOrbit toKeplerian() {
        final double ecc = getE();
        final double inc = getI();
        final double raan = Math.atan2(hy, hx);
        final double paPlusRaan = Math.atan2(ey, ex);
        final double pa = Constants.normalizeAngleZeroTwoPi(paPlusRaan - raan);
        final double trueAnom = Constants.normalizeAngleZeroTwoPi(lv - paPlusRaan);
        return new KeplerianOrbit(a, ecc, inc, pa, Constants.normalizeAngleZeroTwoPi(raan),
                trueAnom, PositionAngle.TRUE, getFrame(), getDate(), getMu());
    }

    @Override
    public String toString() {
        return String.format(
                "EquinoctialOrbit{a=%.3f m, ex=%.6e, ey=%.6e, hx=%.6e, hy=%.6e, lv=%.4f deg}",
                a, ex, ey, hx, hy, Math.toDegrees(lv));
    }
}
