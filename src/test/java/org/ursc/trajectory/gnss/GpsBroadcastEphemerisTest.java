package org.ursc.trajectory.gnss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Tests the IS-GPS-200 satellite position/clock evaluation in
 * {@link GpsBroadcastEphemeris} with controlled broadcast parameters.
 */
class GpsBroadcastEphemerisTest {

    private static final AbsoluteDate TOE =
            new AbsoluteDate(2024, 10, 16, 0, 0, 0.0, TimeScalesFactory.getGPS());
    private static final double SQRT_A = 5153.65;        // -> A ~ 26,560 km
    private static final double A = SQRT_A * SQRT_A;

    /** Build an otherwise-zero ephemeris with the given eccentricity, mean anomaly, clock. */
    private static GpsBroadcastEphemeris eph(final double e, final double m0,
                                             final double af0, final double af1) {
        return new GpsBroadcastEphemeris(1, TOE, TOE, 0.0, af0, af1, 0.0,
                SQRT_A, e, m0, 0.0,
                0.5, 0.9, 0.2, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    @Test
    void circularOrbitRadiusMatchesSemiMajorAxis() {
        final Vector3D p = eph(0.0, 0.0, 0.0, 0.0).positionEcef(TOE);
        assertEquals(A, p.getNorm(), 1.0, "radius should equal A for a circular orbit");
    }

    @Test
    void radiusAtPerigeeIsReduced() {
        final double e = 0.01;
        // M0 = 0 -> E = 0 -> radius = A(1 - e) (perigee)
        final Vector3D p = eph(e, 0.0, 0.0, 0.0).positionEcef(TOE);
        assertEquals(A * (1.0 - e), p.getNorm(), 1.0, "perigee radius");
    }

    @Test
    void clockPolynomialEvaluates() {
        final double af0 = 1.0e-4;
        final double af1 = 2.0e-11;
        final GpsBroadcastEphemeris e = eph(0.0, 0.0, af0, af1);
        // e = 0 -> no relativistic term; clock is the pure polynomial
        assertEquals(af0, e.clockBias(TOE), 1.0e-15, "clock at toc");
        assertEquals(af0 + af1 * 100.0, e.clockBias(TOE.shiftedBy(100.0)), 1.0e-15,
                "clock 100 s later");
    }

    @Test
    void relativisticCorrectionHasCorrectSign() {
        final double ecc = 0.02;
        final double m0 = 1.0; // non-zero -> E != 0 -> sin(E) > 0 -> negative correction (F < 0)
        final GpsBroadcastEphemeris e = eph(ecc, m0, 5.0e-4, 0.0);
        final double clock = e.clockBias(TOE);
        assertTrue(clock < 5.0e-4, "relativistic term should lower the clock for sin(E) > 0");
        // magnitude is the eccentricity term F*e*sqrtA*sin(E); independently bounded
        final double maxTerm = Math.abs(GpsConstants.RELATIVISTIC_F) * ecc * SQRT_A;
        assertTrue(5.0e-4 - clock <= maxTerm + 1.0e-18, "correction within |F*e*sqrtA|");
    }
}
