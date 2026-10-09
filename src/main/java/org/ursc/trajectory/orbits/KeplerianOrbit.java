package org.ursc.trajectory.orbits;

import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Classical Keplerian elements: semi-major axis, eccentricity, inclination,
 * argument of perigee, right ascension of the ascending node and an anomaly.
 *
 * <p>The true anomaly is stored internally; mean and eccentric anomalies are
 * available on request. Conversions to/from Cartesian follow Vallado,
 * <i>Fundamentals of Astrodynamics and Applications</i>.</p>
 */
public final class KeplerianOrbit extends Orbit {

    private final double a;
    private final double e;
    private final double i;
    private final double pa;    // argument of perigee (omega)
    private final double raan;  // right ascension of ascending node (Omega)
    private final double v;     // true anomaly

    public KeplerianOrbit(final double a, final double e, final double i,
                          final double pa, final double raan, final double anomaly,
                          final PositionAngle type,
                          final Frame frame, final AbsoluteDate date, final double mu) {
        super(frame, date, mu);
        this.a = a;
        this.e = e;
        this.i = i;
        this.pa = pa;
        this.raan = raan;
        this.v = toTrueAnomaly(anomaly, e, type);
    }

    /** Convert any orbit (via its Cartesian coordinates) into Keplerian elements. */
    public KeplerianOrbit(final Orbit orbit) {
        this(orbit.getPVCoordinates(), orbit.getFrame(), orbit.getDate(), orbit.getMu());
    }

    /** Build Keplerian elements from position/velocity (the rv2coe algorithm). */
    public KeplerianOrbit(final PVCoordinates pv, final Frame frame,
                          final AbsoluteDate date, final double mu) {
        super(frame, date, mu);
        final Vector3D rVec = pv.getPosition();
        final Vector3D vVec = pv.getVelocity();
        final double r = rVec.getNorm();
        final double vr = vVec.getNorm();

        final Vector3D h = rVec.crossProduct(vVec);
        final Vector3D nodeVec = Vector3D.PLUS_K.crossProduct(h);
        final double nMag = nodeVec.getNorm();

        final Vector3D eVec = rVec.scalarMultiply(vr * vr - mu / r)
                .subtract(vVec.scalarMultiply(rVec.dotProduct(vVec)))
                .scalarMultiply(1.0 / mu);
        this.e = eVec.getNorm();

        final double energy = vr * vr / 2.0 - mu / r;
        this.a = -mu / (2.0 * energy);
        this.i = Vector3D.angle(h, Vector3D.PLUS_K);

        if (nMag > 1.0e-11) {
            double node = Math.acos(clamp(nodeVec.getX() / nMag));
            if (nodeVec.getY() < 0) {
                node = Constants.TWO_PI - node;
            }
            this.raan = node;
        } else {
            this.raan = 0.0; // equatorial orbit: node undefined, use reference
        }

        if (nMag > 1.0e-11 && e > 1.0e-11) {
            double argP = Math.acos(clamp(nodeVec.dotProduct(eVec) / (nMag * e)));
            if (eVec.getZ() < 0) {
                argP = Constants.TWO_PI - argP;
            }
            this.pa = argP;
        } else {
            this.pa = 0.0;
        }

        if (e > 1.0e-11) {
            double trueAnom = Math.acos(clamp(eVec.dotProduct(rVec) / (e * r)));
            if (rVec.dotProduct(vVec) < 0) {
                trueAnom = Constants.TWO_PI - trueAnom;
            }
            this.v = trueAnom;
        } else {
            // circular: measure argument of latitude from the node (or x-axis if equatorial)
            final Vector3D reference = nMag > 1.0e-11 ? nodeVec : Vector3D.PLUS_I;
            double u = Math.acos(clamp(reference.dotProduct(rVec) / (reference.getNorm() * r)));
            if (rVec.getZ() < 0) {
                u = Constants.TWO_PI - u;
            }
            this.v = u;
        }
    }

    @Override
    public OrbitType getType() {
        return OrbitType.KEPLERIAN;
    }

    public double getPerigeeArgument() {
        return pa;
    }

    public double getRightAscensionOfAscendingNode() {
        return raan;
    }

    public double getTrueAnomaly() {
        return v;
    }

    public double getEccentricAnomaly() {
        final double cosV = Math.cos(v);
        final double eccAnom = Math.atan2(Math.sqrt(1 - e * e) * Math.sin(v), e + cosV);
        return Constants.normalizeAngleZeroTwoPi(eccAnom);
    }

    public double getMeanAnomaly() {
        final double eccAnom = getEccentricAnomaly();
        return Constants.normalizeAngleZeroTwoPi(eccAnom - e * Math.sin(eccAnom));
    }

    @Override
    public double getA() {
        return a;
    }

    @Override
    public double getE() {
        return e;
    }

    @Override
    public double getI() {
        return i;
    }

    @Override
    public PVCoordinates getPVCoordinates() {
        final double p = a * (1.0 - e * e);
        final double cosV = Math.cos(v);
        final double sinV = Math.sin(v);
        final double rMag = p / (1.0 + e * cosV);

        // perifocal (PQW) coordinates
        final double xP = rMag * cosV;
        final double yP = rMag * sinV;
        final double sqrtMuP = Math.sqrt(getMu() / p);
        final double vxP = -sqrtMuP * sinV;
        final double vyP = sqrtMuP * (e + cosV);

        final double cosO = Math.cos(raan);
        final double sinO = Math.sin(raan);
        final double cosW = Math.cos(pa);
        final double sinW = Math.sin(pa);
        final double cosI = Math.cos(i);
        final double sinI = Math.sin(i);

        // PQW -> ECI rotation matrix elements
        final double r11 = cosO * cosW - sinO * sinW * cosI;
        final double r12 = -cosO * sinW - sinO * cosW * cosI;
        final double r21 = sinO * cosW + cosO * sinW * cosI;
        final double r22 = -sinO * sinW + cosO * cosW * cosI;
        final double r31 = sinW * sinI;
        final double r32 = cosW * sinI;

        final Vector3D position = new Vector3D(
                r11 * xP + r12 * yP,
                r21 * xP + r22 * yP,
                r31 * xP + r32 * yP);
        final Vector3D velocity = new Vector3D(
                r11 * vxP + r12 * vyP,
                r21 * vxP + r22 * vyP,
                r31 * vxP + r32 * vyP);
        return new PVCoordinates(position, velocity);
    }

    private static double toTrueAnomaly(final double anomaly, final double e, final PositionAngle type) {
        switch (type) {
            case TRUE:
                return anomaly;
            case ECCENTRIC:
                return eccentricToTrue(anomaly, e);
            case MEAN:
                return eccentricToTrue(solveKepler(anomaly, e), e);
            default:
                throw new IllegalArgumentException("unknown position angle type " + type);
        }
    }

    private static double eccentricToTrue(final double eccAnom, final double e) {
        return Math.atan2(Math.sqrt(1 - e * e) * Math.sin(eccAnom),
                Math.cos(eccAnom) - e);
    }

    /** Solve Kepler's equation M = E - e sin E by Newton-Raphson. */
    private static double solveKepler(final double meanAnom, final double e) {
        double eccAnom = (e < 0.8) ? meanAnom : Math.PI;
        for (int iter = 0; iter < 100; iter++) {
            final double f = eccAnom - e * Math.sin(eccAnom) - meanAnom;
            final double fp = 1.0 - e * Math.cos(eccAnom);
            final double delta = f / fp;
            eccAnom -= delta;
            if (Math.abs(delta) < 1.0e-13) {
                break;
            }
        }
        return eccAnom;
    }

    private static double clamp(final double x) {
        return Math.max(-1.0, Math.min(1.0, x));
    }

    @Override
    public String toString() {
        return String.format(
                "KeplerianOrbit{a=%.3f m, e=%.6f, i=%.4f deg, pa=%.4f deg, raan=%.4f deg, v=%.4f deg}",
                a, e, Math.toDegrees(i), Math.toDegrees(pa), Math.toDegrees(raan), Math.toDegrees(v));
    }
}
