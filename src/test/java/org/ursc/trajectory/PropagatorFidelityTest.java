package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.bodies.EphemerisSource;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * The high-precision-ephemeris seam in {@link CelestialBodyFactory}: by default the Sun and Moon
 * use the analytic series, and an installed {@link EphemerisSource} (such as the DE440 reader)
 * overrides them, falling back to analytic when cleared. The reader itself (JPL binary parsing and
 * Chebyshev interpolation) is validated against the real 14.9 MB DE440 file in a scratch harness,
 * since that file is too large to bundle for unit tests.
 */
class PropagatorFidelityTest {

    private static final AbsoluteDate T =
            new AbsoluteDate(2024, 10, 11, 12, 0, 0.0, TimeScalesFactory.getUTC());

    @AfterEach
    void clearEphemeris() {
        CelestialBodyFactory.setEphemeris(null);   // global singleton: never leak into other tests
    }

    @Test
    void analyticByDefault() {
        assertNull(CelestialBodyFactory.getEphemeris());
        final Vector3D sun = CelestialBodyFactory.getSun().getPosition(T, FramesFactory.getGCRF());
        final Vector3D moon = CelestialBodyFactory.getMoon().getPosition(T, FramesFactory.getGCRF());
        assertTrue(sun.getNorm() > 1.4e11 && sun.getNorm() < 1.6e11, "Sun ~1 AU: " + sun.getNorm());
        assertTrue(moon.getNorm() > 3.4e8 && moon.getNorm() < 4.1e8, "Moon 340-410 Mm: " + moon.getNorm());
    }

    @Test
    void installedSourceOverridesAndFallsBack() {
        final Vector3D fakeSun = new Vector3D(1.0e11, 2.0e10, 3.0e10);
        final Vector3D fakeMoon = new Vector3D(3.0e8, 1.0e8, -5.0e7);
        CelestialBodyFactory.setEphemeris(new EphemerisSource() {
            public Vector3D sunGeocentric(final AbsoluteDate d) {
                return fakeSun;
            }
            public Vector3D moonGeocentric(final AbsoluteDate d) {
                return fakeMoon;
            }
        });
        assertVec(fakeSun, CelestialBodyFactory.getSun().getPosition(T, FramesFactory.getGCRF()));
        assertVec(fakeMoon, CelestialBodyFactory.getMoon().getPosition(T, FramesFactory.getGCRF()));

        CelestialBodyFactory.setEphemeris(null);
        final Vector3D sun = CelestialBodyFactory.getSun().getPosition(T, FramesFactory.getGCRF());
        assertTrue(sun.getNorm() > 1.4e11 && sun.getNorm() < 1.6e11, "fell back to analytic Sun");
    }

    private static void assertVec(final Vector3D expected, final Vector3D actual) {
        assertEquals(expected.getX(), actual.getX(), 1e-6);
        assertEquals(expected.getY(), actual.getY(), 1e-6);
        assertEquals(expected.getZ(), actual.getZ(), 1e-6);
    }
}
