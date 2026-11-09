package org.ursc.trajectory.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.io.Oem;
import org.ursc.trajectory.time.AbsoluteDate;

/** Run comparison: RTN decomposition, units and interpolation on non-coincident epochs. */
class RunCompareTest {

    private static final double R = 7.18e6;
    private static final double W = Math.sqrt(3.986004418e14 / (R * R * R));

    /** Circular equatorial orbit; offsets in radial / along-track (m), sampled every step seconds from t0. */
    private static Oem orbit(final double t0, final double step, final int n, final double dr, final double dAlong, final String frame)
            throws Exception {
        final StringBuilder sb = new StringBuilder("CCSDS_OEM_VERS = 2.0\nCREATION_DATE = 2025-01-01T00:00:00\nORIGINATOR = TEST\n"
                + "META_START\nOBJECT_NAME = X\nOBJECT_ID = X\nCENTER_NAME = EARTH\nREF_FRAME = " + frame + "\nTIME_SYSTEM = UTC\n"
                + "START_TIME = 2025-01-15T00:00:00\nSTOP_TIME = 2025-01-16T00:00:00\nMETA_STOP\n");
        final AbsoluteDate e0 = TimeUtil.parseUtc("2025-01-15T00:00:00");
        for (int i = 0; i < n; i++) {
            final double t = t0 + i * step;
            final double a = W * t;
            final double r = R + dr;
            final double u = a + dAlong / R;
            final double v = W * R;
            sb.append(TimeUtil.formatUtc(e0.shiftedBy(t))).append(String.format(java.util.Locale.US, " %.9f %.9f 0 %.12f %.12f 0%n",
                    r * Math.cos(u) / 1000, r * Math.sin(u) / 1000, -v * Math.sin(a) / 1000, v * Math.cos(a) / 1000));
        }
        return Oem.parse(sb.toString());
    }

    @Test
    void decomposesAKnownOffsetInMetres() throws Exception {
        final RunCompare.Result r = RunCompare.compare(orbit(0, 60, 200, 0, 0, "EME2000"), orbit(0, 60, 200, 12.0, 30.0, "EME2000"));
        assertEquals(200, r.t.size());
        assertEquals(12.0, r.radial.get(50), 0.01);
        assertEquals(30.0, r.along.get(50), 0.01);
        assertEquals(0.0, r.cross.get(50), 1e-6);
        assertEquals(Math.hypot(12, 30), r.final3d, 0.02);
    }

    @Test
    void interpolatesNonCoincidentEpochs() throws Exception {
        // the other run is sampled at 60 s offset by 17 s: identical orbit, so differences must be ~0
        final RunCompare.Result r = RunCompare.compare(orbit(0, 60, 200, 0, 0, "EME2000"), orbit(-17, 60, 202, 0, 0, "EME2000"));
        assertEquals(0.0, r.max3d, 1e-3);
    }

    @Test
    void refusesDifferentFrames() {
        assertThrows(IllegalArgumentException.class,
                () -> RunCompare.compare(orbit(0, 60, 10, 0, 0, "EME2000"), orbit(0, 60, 10, 0, 0, "ITRF")));
    }
}
