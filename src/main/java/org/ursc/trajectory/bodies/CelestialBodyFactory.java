package org.ursc.trajectory.bodies;

import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Factory for the celestial bodies relevant to LEO perturbations: the Earth
 * (central body), the Sun and the Moon.
 *
 * <p>The Sun and Moon use the low-precision analytical series of Montenbruck
 * &amp; Gill, <i>Satellite Orbits</i> (sections 3.3.2), accurate to roughly
 * 0.1&deg; / a few hundred km &mdash; far better than needed for third-body and
 * radiation-pressure perturbation modelling. Positions are returned in GCRF
 * (mean equator and equinox of J2000).</p>
 */
public final class CelestialBodyFactory {

    private static final CelestialBody EARTH = new CelestialBody() {
        public String getName() {
            return "Earth";
        }
        public double getMu() {
            return Constants.EARTH_MU;
        }
        public Vector3D getPosition(final AbsoluteDate date, final Frame frame) {
            return Vector3D.ZERO;
        }
    };

    private static final CelestialBody SUN = new CelestialBody() {
        public String getName() {
            return "Sun";
        }
        public double getMu() {
            return Constants.SUN_MU;
        }
        public Vector3D getPosition(final AbsoluteDate date, final Frame frame) {
            return requireGcrf(frame, sunPositionGCRF(date));
        }
    };

    private static final CelestialBody MOON = new CelestialBody() {
        public String getName() {
            return "Moon";
        }
        public double getMu() {
            return Constants.MOON_MU;
        }
        public Vector3D getPosition(final AbsoluteDate date, final Frame frame) {
            return requireGcrf(frame, moonPositionGCRF(date));
        }
    };

    private CelestialBodyFactory() {
    }

    public static CelestialBody getEarth() {
        return EARTH;
    }

    public static CelestialBody getSun() {
        return SUN;
    }

    public static CelestialBody getMoon() {
        return MOON;
    }

    private static Vector3D requireGcrf(final Frame frame, final Vector3D gcrfPosition) {
        if (frame == FramesFactory.getGCRF()) {
            return gcrfPosition;
        }
        throw new IllegalArgumentException(
                "ephemerides are currently provided in GCRF only, requested " + frame);
    }

    /** Geocentric Sun position in GCRF (m). */
    static Vector3D sunPositionGCRF(final AbsoluteDate date) {
        final double t = date.julianCenturiesTT();
        final double m = Math.toRadians(357.5256 + 35999.049 * t);           // solar mean anomaly
        final double lambdaDeg = 282.94 + Math.toDegrees(m)
                + (6892.0 * Math.sin(m) + 72.0 * Math.sin(2 * m)) / 3600.0;  // ecliptic longitude
        final double lambda = Math.toRadians(lambdaDeg);
        final double rMeters =
                (149.619e9 - 2.499e9 * Math.cos(m) - 0.021e9 * Math.cos(2 * m)); // distance (m)

        final double xEcl = rMeters * Math.cos(lambda);
        final double yEcl = rMeters * Math.sin(lambda);
        return eclipticToEquatorial(xEcl, yEcl, 0.0);
    }

    /** Geocentric Moon position in GCRF (m). */
    static Vector3D moonPositionGCRF(final AbsoluteDate date) {
        final double t = date.julianCenturiesTT();

        final double l0 = 218.31617 + 481267.88088 * t - 1.3972 * t;
        final double l = Math.toRadians(134.96292 + 477198.86753 * t);  // Moon mean anomaly
        final double lp = Math.toRadians(357.52543 + 35999.04944 * t);  // Sun mean anomaly
        final double f = Math.toRadians(93.27283 + 483202.01873 * t);   // argument of latitude
        final double d = Math.toRadians(297.85027 + 445267.11135 * t);  // mean elongation

        final double dLambda = (
                  22640 * Math.sin(l)
                +   769 * Math.sin(2 * l)
                -  4586 * Math.sin(l - 2 * d)
                +  2370 * Math.sin(2 * d)
                -   668 * Math.sin(lp)
                -   412 * Math.sin(2 * f)
                -   212 * Math.sin(2 * l - 2 * d)
                -   206 * Math.sin(l + lp - 2 * d)
                +   192 * Math.sin(l + 2 * d)
                -   165 * Math.sin(lp - 2 * d)
                +   148 * Math.sin(l - lp)
                -   125 * Math.sin(d)
                -   110 * Math.sin(l + lp)
                -    55 * Math.sin(2 * f - 2 * d)) / 3600.0;              // deg
        final double lambda = Math.toRadians(l0 + dLambda);

        final double betaArg = f + Math.toRadians(dLambda + (412 * Math.sin(2 * f) + 541 * Math.sin(lp)) / 3600.0);
        final double betaDeg = (
                  18520 * Math.sin(betaArg)
                -   526 * Math.sin(f - 2 * d)
                +    44 * Math.sin(l + f - 2 * d)
                -    31 * Math.sin(-l + f - 2 * d)
                -    25 * Math.sin(-2 * l + f)
                -    23 * Math.sin(lp + f - 2 * d)
                +    21 * Math.sin(-l + f)
                +    11 * Math.sin(-lp + f - 2 * d)) / 3600.0;            // deg
        final double beta = Math.toRadians(betaDeg);

        final double rKm = 385000.0
                - 20905.0 * Math.cos(l)
                -  3699.0 * Math.cos(2 * d - l)
                -  2956.0 * Math.cos(2 * d)
                -   570.0 * Math.cos(2 * l)
                +   246.0 * Math.cos(2 * l - 2 * d)
                -   205.0 * Math.cos(lp - 2 * d)
                -   171.0 * Math.cos(l + 2 * d)
                -   152.0 * Math.cos(l + lp - 2 * d);
        final double r = rKm * 1000.0;

        final double cosB = Math.cos(beta);
        final double xEcl = r * cosB * Math.cos(lambda);
        final double yEcl = r * cosB * Math.sin(lambda);
        final double zEcl = r * Math.sin(beta);
        return eclipticToEquatorial(xEcl, yEcl, zEcl);
    }

    private static Vector3D eclipticToEquatorial(final double x, final double y, final double z) {
        final double eps = Constants.OBLIQUITY_J2000;
        final double cosE = Math.cos(eps);
        final double sinE = Math.sin(eps);
        return new Vector3D(
                x,
                cosE * y - sinE * z,
                sinE * y + cosE * z);
    }
}
