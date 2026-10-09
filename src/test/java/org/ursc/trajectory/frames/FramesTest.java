package org.ursc.trajectory.frames;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.math.RotationMatrix;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Validates the IAU-2006/2000B frame model against the published SOFA reference
 * values (the authoritative check), plus transform consistency.
 */
class FramesTest {

    @Test
    void nutation2000BMatchesSofa() {
        // SOFA t_sofa_c reference for iauNut00b at TT JD 2400000.5 + 53736.0
        final double t = (2453736.5 - 2451545.0) / 36525.0;
        final double[] nut = Iau2006.nutation2000B(t);
        assertEquals(-0.9632552291148362e-5, nut[0], 1e-13, "dpsi");
        assertEquals(0.4063197106621159e-4, nut[1], 1e-13, "deps");
    }

    @Test
    void meanObliquityMatchesSofa() {
        // SOFA iauObl06 reference at TT JD 2400000.5 + 54388.0
        final double t = (2454388.5 - 2451545.0) / 36525.0;
        assertEquals(0.4090749229387258103, Iau2006.meanObliquity(t), 1e-15);
    }

    @Test
    void transformRoundTripsToIdentity() {
        final AbsoluteDate d = new AbsoluteDate(2024, 3, 20, 12, 0, 0.0, TimeScalesFactory.getUTC());
        final RotationMatrix r = Iau2006.gcrfToItrf(d, ConstantEop.ZERO);
        // r * r^T should be the identity
        final RotationMatrix shouldBeId = r.multiply(r.transpose());
        final Vector3D x = shouldBeId.applyTo(Vector3D.PLUS_I);
        final Vector3D y = shouldBeId.applyTo(Vector3D.PLUS_J);
        assertEquals(1.0, x.getX(), 1e-12);
        assertEquals(0.0, x.getY(), 1e-12);
        assertEquals(1.0, y.getY(), 1e-12);
    }

    @Test
    void eopFileIsParsedAndInterpolated() throws Exception {
        // minimal EOP C04-style lines: year month day MJD x(") y(") UT1-UTC(s)
        final String data =
                "2024 1 1 60310  0.100000  0.200000  0.0100000\n"
                + "2024 1 2 60311  0.200000  0.400000  0.0200000\n";
        final Path f = Files.createTempFile("eop", ".txt");
        Files.write(f, data.getBytes(StandardCharsets.UTF_8));
        final IersEopProvider eop = new IersEopProvider(f, ConstantEop.ZERO);

        // midday on Jan 1 (MJD 60310.5) -> halfway between the two daily rows
        final AbsoluteDate mid = new AbsoluteDate(2024, 1, 1, 12, 0, 0.0, TimeScalesFactory.getUTC());
        assertEquals(0.0150000, eop.getUT1MinusUTC(mid), 1e-9);
        final double arcsec = Math.PI / (180.0 * 3600.0);
        assertEquals(0.150000 * arcsec, eop.getXp(mid), 1e-12);

        // out of range -> fallback (zero)
        final AbsoluteDate far = new AbsoluteDate(2000, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        assertEquals(0.0, eop.getUT1MinusUTC(far), 0.0);
        Files.deleteIfExists(f);
    }

    @Test
    void iauDiffersFromSimpleAtArcsecondLevel() {
        final AbsoluteDate d = new AbsoluteDate(2024, 3, 20, 12, 0, 0.0, TimeScalesFactory.getUTC());
        final RotationMatrix iau = Iau2006.gcrfToItrf(d, ConstantEop.ZERO);
        final RotationMatrix simple = RotationMatrix.rotationZ(FramesFactory.gmst(d));
        // a LEO position vector rotated both ways should differ by precession/nutation
        final Vector3D r = new Vector3D(6978137.0, 0.0, 0.0);
        final double diff = iau.applyTo(r).subtract(simple.applyTo(r)).getNorm();
        // precession since J2000 (~0.4 deg over 24 yr) dominates: tens of km, but bounded
        assertTrue(diff > 1.0 && diff < 1.0e6, "unexpected IAU-vs-simple difference: " + diff + " m");
    }
}
