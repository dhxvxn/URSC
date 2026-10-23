package org.ursc.trajectory.gnss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Parsing test for {@link RinexNavReader}. Builds a single RINEX 3 GPS navigation
 * record at the standard fixed columns and checks the field order and the resulting
 * orbit/clock through {@link GpsBroadcastEphemeris}.
 */
class RinexNavReaderTest {

    private static final double SQRT_A = 5153.65; // A ~ 26,560 km

    /** Format a value as a 19-char field (the reader accepts E or D exponents). */
    private static String fld(final double v) {
        return String.format("%19.12E", v);
    }

    /** One zero-everything-else GPS nav record with the given e and M0. */
    private static String navRecord(final double e, final double m0,
                                    final double af0, final double toeSow) {
        final StringBuilder sb = new StringBuilder();
        sb.append("     3.04           N                   G                   "
                + "RINEX VERSION / TYPE\n");
        sb.append("                                                            END OF HEADER\n");
        // SV / epoch / clock : "G01 2024 10 16 00 00 00" is exactly 23 chars
        sb.append("G01 2024 10 16 00 00 00").append(fld(af0)).append(fld(0.0))
                .append(fld(0.0)).append('\n');
        // broadcast-orbit lines: 4 spaces + 4 fields of 19 chars each
        appendOrbit(sb, 0.0, 0.0, 0.0, m0);                 // IODE, Crs, DeltaN, M0
        appendOrbit(sb, 0.0, e, 0.0, SQRT_A);               // Cuc, e, Cus, sqrtA
        appendOrbit(sb, toeSow, 0.0, 0.5, 0.0);             // Toe, Cic, Omega0, Cis
        appendOrbit(sb, 0.9, 0.0, 0.2, 0.0);                // i0, Crc, omega, OmegaDot
        appendOrbit(sb, 0.0, 0.0, 2338.0, 0.0);             // IDOT, codes, week, L2P
        appendOrbit(sb, 2.0, 0.0, -1.0e-8, 0.0);            // acc, health, TGD, IODC
        appendOrbit(sb, 0.0, 0.0, 0.0, 0.0);                // transmit, fit, spares
        return sb.toString();
    }

    private static void appendOrbit(final StringBuilder sb, final double a, final double b,
                                    final double c, final double d) {
        sb.append("    ").append(fld(a)).append(fld(b)).append(fld(c)).append(fld(d)).append('\n');
    }

    @Test
    void parsesOneGpsMessageAndComputesOrbit() throws IOException {
        final String text = navRecord(0.004, 0.0, 1.23e-4, 0.0);
        final RinexNavReader.Data data = RinexNavReader.read(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, data.messageCount());
        assertTrue(data.prns().contains(1));

        final AbsoluteDate toc =
                new AbsoluteDate(2024, 10, 16, 0, 0, 0.0, TimeScalesFactory.getGPS());
        final GpsBroadcastEphemeris eph = data.ephemerisFor(1, toc);
        assertNotNull(eph);

        // evaluate at Toe (tk = 0): M0 = 0 -> E = 0 -> radius = A(1 - e)
        final double a = SQRT_A * SQRT_A;
        final Vector3D p = eph.positionEcef(eph.getToe());
        assertEquals(a * (1.0 - 0.004), p.getNorm(), 2.0, "orbit radius from parsed fields");

        // clock near af0 (within the small relativistic bound)
        assertEquals(1.23e-4, eph.clockBias(toc), 1.0e-8, "clock from parsed af0");
    }
}
