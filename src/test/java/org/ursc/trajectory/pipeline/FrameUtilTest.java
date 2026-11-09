package org.ursc.trajectory.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.frames.ConstantEop;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

class FrameUtilTest {

    @Test
    void eme2000FrameBiasMatchesIers2010() {
        // SOFA iauBp00 frame-bias matrix rb (GCRS -> mean J2000)
        assertEquals(-0.7078279744199225e-7, FrameUtil.BIAS[0][1], 1e-18);
        assertEquals(0.8056217146976134e-7, FrameUtil.BIAS[0][2], 1e-18);
        assertEquals(0.7078279477857337e-7, FrameUtil.BIAS[1][0], 1e-18);
        assertEquals(0.3306041454222136e-7, FrameUtil.BIAS[1][2], 1e-18);
        assertEquals(-0.8056217380986972e-7, FrameUtil.BIAS[2][0], 1e-18);
        assertEquals(-0.3306040883980552e-7, FrameUtil.BIAS[2][1], 1e-18);
    }

    @Test
    void itrfConversionUsesTheFullRotationRate() {
        FramesFactory.setModel(FramesFactory.Model.IAU_2006);
        FramesFactory.setEopProvider(new ConstantEop(0.1, 1e-6, 2e-6));
        final AbsoluteDate t = TimeUtil.parseUtc("2024-10-16T06:00:00");
        // a point fixed on the equator: inertial speed is omega * R
        final Vector3D r = new Vector3D(6378137.0, 0, 0);
        final FrameUtil.PV g = FrameUtil.itrfToGcrf(r, Vector3D.ZERO, t);
        assertEquals(7.292115e-5 * 6378137.0, g.v.getNorm(), 0.01);
        // round trip
        final FrameUtil.PV back = FrameUtil.gcrfToItrf(g.r, g.v, t);
        assertTrue(back.r.distance(r) < 1e-6);
        assertTrue(back.v.getNorm() < 1e-7, "velocity round trip " + back.v.getNorm());
        // covariance rotation preserves the trace
        final double[][] c = new double[6][6];
        for (int i = 0; i < 6; i++) {
            c[i][i] = i < 3 ? 4.0 : 1e-4;
        }
        final double[][] ce = FrameUtil.cov6FromGcrf(c, RunConfig.OutputFrame.EME2000, t);
        assertEquals(12.0, ce[0][0] + ce[1][1] + ce[2][2], 1e-9);
    }

    @Test
    void parsesAndFormatsUtc() {
        final AbsoluteDate a = TimeUtil.parseUtc("2024-10-16T06:00:00Z");
        assertEquals("2024-10-16T06:00:00.000000", TimeUtil.formatUtc(a));
        assertEquals(1.5, TimeUtil.parseUtc("2024-10-16 06:00:01.5").durationFrom(a), 1e-9);
        assertEquals(0.0, TimeUtil.parseUtc("2024-10-16T06:00").durationFrom(a), 1e-9);
    }
}
