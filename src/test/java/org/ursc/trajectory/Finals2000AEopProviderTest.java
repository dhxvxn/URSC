package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.frames.ConstantEop;
import org.ursc.trajectory.frames.Finals2000AEopProvider;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/** Parsing, interpolation and fallback of the IERS finals2000A.all EOP file. */
class Finals2000AEopProviderTest {

    private static final double ARCSEC = Math.PI / (180.0 * 3600.0);

    // Three daily records (MJD 60000..60002). year/mo/day are ignored (MJD drives the lookup).
    // Layout: yy mm dd  MJD  <flag> PMx errx PMy erry  <flag> UT1-UTC err  <flag> dX errdX dY errdY
    private static final String TXT =
            "23  2 25 60000.00 I  0.100000 0.000050  0.200000 0.000050  I -0.1000000 0.0000100  "
                    + "I    0.000    0.000    0.000    0.000\n"
            + "23  2 26 60001.00 I  0.120000 0.000050  0.220000 0.000050  I -0.1100000 0.0000100  "
                    + "I    0.000    0.000    0.000    0.000\n"
            + "23  2 27 60002.00 P  0.140000 0.000050  0.240000 0.000050  P -0.1200000 0.0000100  "
                    + "P    0.000    0.000    0.000    0.000\n";

    private Finals2000AEopProvider provider() throws Exception {
        return new Finals2000AEopProvider(
                new ByteArrayInputStream(TXT.getBytes(StandardCharsets.UTF_8)),
                new ConstantEop(0.5, 0.0, 0.0));
    }

    @Test
    void interpolatesBulletinAatMidday() throws Exception {
        final Finals2000AEopProvider p = provider();
        // MJD 60001.5 -> midpoint of day 2 (60001) and day 3 (60002)
        final AbsoluteDate t = new AbsoluteDate(2023, 2, 26, 12, 0, 0.0, TimeScalesFactory.getUTC());
        assertEquals(-0.1150000, p.getUT1MinusUTC(t), 1e-6);
        assertEquals(0.130000 * ARCSEC, p.getXp(t), 1e-12);
        assertEquals(0.230000 * ARCSEC, p.getYp(t), 1e-12);
    }

    @Test
    void fallsBackOutsideTheFile() throws Exception {
        final Finals2000AEopProvider p = provider();
        final AbsoluteDate far = new AbsoluteDate(2030, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
        assertEquals(0.5, p.getUT1MinusUTC(far), 1e-12);  // fallback UT1
        assertEquals(0.0, p.getXp(far), 1e-12);           // fallback polar motion
    }

    @Test
    void detectsFinals2000AButNotC04() throws Exception {
        assertTrue(Finals2000AEopProvider.looksLikeFinals2000A(
                new ByteArrayInputStream(TXT.getBytes(StandardCharsets.UTF_8))));
        // an EOP C04 line (4-digit year, no I/P Bulletin-A flags) must not be mistaken for finals
        final String c04 = "2023  2 26  60001   0.120000 0.220000  -0.1100000  0.0000  0.000 0.000\n";
        assertFalse(Finals2000AEopProvider.looksLikeFinals2000A(
                new ByteArrayInputStream(c04.getBytes(StandardCharsets.UTF_8))));
    }
}
