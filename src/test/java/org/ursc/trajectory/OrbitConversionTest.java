package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.EquinoctialOrbit;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.PositionAngle;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/** Round-trip conversions between the orbit parameter sets must be loss-free. */
class OrbitConversionTest {

    private static final double MU = Constants.EARTH_MU;
    private final AbsoluteDate date =
            new AbsoluteDate(2024, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());

    @Test
    void keplerianToCartesianAndBack() {
        final KeplerianOrbit k = new KeplerianOrbit(
                7_000_000.0, 0.01, Math.toRadians(45.0),
                Math.toRadians(20.0), Math.toRadians(30.0), Math.toRadians(40.0),
                PositionAngle.TRUE, FramesFactory.getGCRF(), date, MU);

        final CartesianOrbit c = new CartesianOrbit(k);
        final KeplerianOrbit back = new KeplerianOrbit(c);

        assertEquals(k.getA(), back.getA(), 1.0e-3);
        assertEquals(k.getE(), back.getE(), 1.0e-10);
        assertEquals(k.getI(), back.getI(), 1.0e-10);
        assertEquals(k.getPerigeeArgument(), back.getPerigeeArgument(), 1.0e-9);
        assertEquals(k.getRightAscensionOfAscendingNode(),
                back.getRightAscensionOfAscendingNode(), 1.0e-9);
        assertEquals(k.getTrueAnomaly(), back.getTrueAnomaly(), 1.0e-9);
    }

    @Test
    void keplerianToEquinoctialAndBack() {
        final KeplerianOrbit k = new KeplerianOrbit(
                7_200_000.0, 0.02, Math.toRadians(63.4),
                Math.toRadians(10.0), Math.toRadians(80.0), Math.toRadians(120.0),
                PositionAngle.TRUE, FramesFactory.getGCRF(), date, MU);

        final EquinoctialOrbit eq = new EquinoctialOrbit(k);
        final KeplerianOrbit back = eq.toKeplerian();

        assertEquals(k.getA(), back.getA(), 1.0e-3);
        assertEquals(k.getE(), back.getE(), 1.0e-10);
        assertEquals(k.getI(), back.getI(), 1.0e-10);
    }

    @Test
    void meanEccentricTrueAnomalyConsistency() {
        final KeplerianOrbit fromMean = new KeplerianOrbit(
                7_000_000.0, 0.1, Math.toRadians(30.0),
                0.0, 0.0, Math.toRadians(90.0),
                PositionAngle.MEAN, FramesFactory.getGCRF(), date, MU);
        // rebuild from the derived true anomaly and check the mean anomaly comes back
        final KeplerianOrbit fromTrue = new KeplerianOrbit(
                7_000_000.0, 0.1, Math.toRadians(30.0),
                0.0, 0.0, fromMean.getTrueAnomaly(),
                PositionAngle.TRUE, FramesFactory.getGCRF(), date, MU);
        assertEquals(Math.toRadians(90.0), fromTrue.getMeanAnomaly(), 1.0e-9);
    }
}
