package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ursc.trajectory.attitude.BodyFrame;
import org.ursc.trajectory.attitude.QuaternionFileAttitude;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Parsing, GPS-epoch handling, interpolation and quaternion→body-frame conversion of the
 * AUX_PROQUA {@code .DBL} reader, using a small synthetic file (so it is reproducible without the
 * full 86400-row product). Row 0 is the identity attitude; row 1 is a +90° rotation about the
 * inertial Z axis — both exact, checkable cases.
 */
class QuaternionAttitudeTest {

    /** The AUX_PROQUA DBL format: comment lines then {@code date time qr q1 q2 q3 mode src}. */
    private static final String DBL =
            "# Parameter list  : Q_COMPR Q_COMP1 Q_COMP2 Q_COMP3 ATT_MODE SOURCE\n"
          + "# Satellite       : SEN-3A\n"
          + "# Start date (GPS): 2024/10/16 00:00:00\n"
          + "2024/10/16 00:00:00.000  1.000000  0.000000  0.000000  0.000000  4 i\n"
          + "2024/10/16 00:00:01.000  0.707107  0.000000  0.000000  0.707107  4 i\n";

    private static SpacecraftState stateAt(final AbsoluteDate date) {
        final CartesianOrbit orbit = new CartesianOrbit(
                new PVCoordinates(new Vector3D(7.0e6, 0, 0), new Vector3D(0, 7500, 0)),
                FramesFactory.getGCRF(), date, Constants.EARTH_MU);
        return new SpacecraftState(orbit, 1129.648);
    }

    private static AbsoluteDate gps(final double secondOfDay) {
        return new AbsoluteDate(2024, 10, 16, 0, 0, secondOfDay, TimeScalesFactory.getGPS());
    }

    @Test
    void parsesCoverageNormAndIdentityAttitude(@TempDir final Path dir) throws Exception {
        final Path f = dir.resolve("proqua.DBL");
        Files.writeString(f, DBL);
        final QuaternionFileAttitude att = new QuaternionFileAttitude(f, true);

        // coverage is the GPS interval [00:00:00, 00:00:01]
        assertTrue(att.covers(gps(0.0)));
        assertTrue(att.covers(gps(1.0)));
        assertFalse(att.covers(gps(2.0)));
        assertEquals(1.0, att.coverageEnd().durationFrom(att.coverageStart()), 1e-9);

        // GPS time is honoured: the file epoch is 18 s before the same UTC wall-clock reading.
        final AbsoluteDate sameWallClockUtc =
                new AbsoluteDate(2024, 10, 16, 0, 0, 0.0, TimeScalesFactory.getUTC());
        assertEquals(18.0, sameWallClockUtc.durationFrom(att.coverageStart()), 1e-6);

        // row 0: identity quaternion → body axes == inertial axes
        final BodyFrame b0 = att.attitude(stateAt(gps(0.0)));
        assertVec(Vector3D.PLUS_I, b0.x());
        assertVec(Vector3D.PLUS_J, b0.y());
        assertVec(Vector3D.PLUS_K, b0.z());
    }

    @Test
    void rotationAboutZAndInterpolation(@TempDir final Path dir) throws Exception {
        final Path f = dir.resolve("proqua.DBL");
        Files.writeString(f, DBL);
        final QuaternionFileAttitude att = new QuaternionFileAttitude(f, true);

        // row 1: +90° about inertial Z (body→inertial) → Xsat=(0,1,0), Ysat=(-1,0,0), Zsat=(0,0,1)
        final BodyFrame b1 = att.attitude(stateAt(gps(1.0)));
        assertVec(new Vector3D(0, 1, 0), b1.x());
        assertVec(new Vector3D(-1, 0, 0), b1.y());
        assertVec(Vector3D.PLUS_K, b1.z());

        // midpoint: interpolated, still an orthonormal right-handed unit frame
        final BodyFrame bm = att.attitude(stateAt(gps(0.5)));
        assertEquals(1.0, bm.x().getNorm(), 1e-9);
        assertEquals(1.0, bm.y().getNorm(), 1e-9);
        assertEquals(0.0, bm.x().dotProduct(bm.y()), 1e-9);
        assertVec(bm.z(), bm.x().crossProduct(bm.y()));
        // ~45° about Z: Xsat ≈ (cos45, sin45, 0)
        assertEquals(Math.cos(Math.PI / 4), bm.x().getX(), 2e-3);
        assertEquals(Math.sin(Math.PI / 4), bm.x().getY(), 2e-3);
    }

    @Test
    void inverseConventionIsTranspose(@TempDir final Path dir) throws Exception {
        final Path f = dir.resolve("proqua.DBL");
        Files.writeString(f, DBL);
        final BodyFrame fwd = new QuaternionFileAttitude(f, true).attitude(stateAt(gps(1.0)));
        final BodyFrame inv = new QuaternionFileAttitude(f, false).attitude(stateAt(gps(1.0)));
        // forward Xsat=(0,1,0); the inverse convention is the transpose → Xsat=(0,-1,0)
        assertVec(new Vector3D(0, 1, 0), fwd.x());
        assertVec(new Vector3D(0, -1, 0), inv.x());
    }

    private static void assertVec(final Vector3D expected, final Vector3D actual) {
        assertEquals(expected.getX(), actual.getX(), 1e-6);
        assertEquals(expected.getY(), actual.getY(), 1e-6);
        assertEquals(expected.getZ(), actual.getZ(), 1e-6);
    }
}
