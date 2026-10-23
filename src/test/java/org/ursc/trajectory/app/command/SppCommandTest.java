package org.ursc.trajectory.app.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.gnss.GpsBroadcastEphemeris;
import org.ursc.trajectory.gnss.GpsConstants;
import org.ursc.trajectory.gnss.RinexNavReader;
import org.ursc.trajectory.gnss.SppEngine;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * End-to-end CLI test for {@link SppCommand}: writes consistent synthetic RINEX
 * observation and navigation files, runs the command, and checks the output CSV
 * recovers the known receiver position. Exercises the full file path
 * (obs reader + nav reader + engine + CSV output + argument handling).
 */
class SppCommandTest {

    private static final double C = GpsConstants.SPEED_OF_LIGHT;
    private static final double SQRT_A = 5153.65;
    private static final double TOE_SOW = 259200.0; // 2024-10-16 00:00:00 GPS (Wed) seconds-of-week
    private static final AbsoluteDate TRX =
            new AbsoluteDate(2024, 10, 16, 0, 0, 0.0, TimeScalesFactory.getGPS());
    private static final Vector3D R_TRUE = new Vector3D(2.5e6, -4.5e6, 5.2e6);
    private static final double CB_TRUE = 1234.5;

    private static String fld(final double v) {
        return String.format(Locale.US, "%19.12E", v);
    }

    private static void navRecord(final StringBuilder sb, final int prn,
                                  final double omega0, final double m0) {
        sb.append(String.format(Locale.US, "G%02d 2024 10 16 00 00 00", prn))
                .append(fld(1.0e-4)).append(fld(0.0)).append(fld(0.0)).append('\n');
        orbit(sb, 0.0, 0.0, 0.0, m0);          // IODE, Crs, DeltaN, M0
        orbit(sb, 0.0, 0.0, 0.0, SQRT_A);      // Cuc, e, Cus, sqrtA
        orbit(sb, TOE_SOW, 0.0, omega0, 0.0);  // Toe, Cic, Omega0, Cis
        orbit(sb, 0.96, 0.0, 0.0, 0.0);        // i0, Crc, omega, OmegaDot
        orbit(sb, 0.0, 0.0, 2338.0, 0.0);      // IDOT, codes, week, L2P
        orbit(sb, 2.0, 0.0, 0.0, 0.0);         // acc, health, TGD, IODC
        orbit(sb, 0.0, 0.0, 0.0, 0.0);         // transmit, fit, spares
    }

    private static void orbit(final StringBuilder sb, final double a, final double b,
                              final double c, final double d) {
        sb.append("    ").append(fld(a)).append(fld(b)).append(fld(c)).append(fld(d)).append('\n');
    }

    @Test
    void recoversReceiverThroughCli(@TempDir final Path dir) throws Exception {
        // 1. write the navigation file for six satellites
        final StringBuilder nav = new StringBuilder();
        nav.append("     3.04           N                   G                   "
                + "RINEX VERSION / TYPE\n");
        nav.append("                                                            END OF HEADER\n");
        for (int i = 0; i < 6; i++) {
            navRecord(nav, i + 1, i, (i * 1.3) % 6.283);
        }
        final Path navFile = dir.resolve("brdc.rnx");
        Files.writeString(navFile, nav.toString());

        // 2. read it back and synthesize consistent pseudoranges to the known receiver
        final RinexNavReader.Data navData = RinexNavReader.read(navFile);
        final StringBuilder obs = new StringBuilder();
        obs.append("     3.03           O                   G                   "
                + "RINEX VERSION / TYPE\n");
        obs.append("G    2 C1W C2W                                              SYS / # / OBS TYPES\n");
        obs.append("  2024    10    16     0     0    0.0000000     GPS         TIME OF FIRST OBS\n");
        obs.append("                                                            END OF HEADER\n");
        obs.append("> 2024 10 16  0  0  0.0000000  0  6\n");
        for (int i = 0; i < 6; i++) {
            final int prn = i + 1;
            final GpsBroadcastEphemeris eph = navData.ephemerisFor(prn, TRX);
            double tau = 0.07;
            for (int k = 0; k < 6; k++) {
                final Vector3D s = SppEngine.satellitePositionAtReception(eph, TRX, tau);
                tau = R_TRUE.distance(s) / C;
            }
            final double p = C * tau + CB_TRUE - C * eph.clockBias(TRX.shiftedBy(-tau));
            obs.append(String.format(Locale.US, "G%02d%14.3f  %14.3f  %n", prn, p, p));
        }
        final Path obsFile = dir.resolve("s3a.rnx");
        Files.writeString(obsFile, obs.toString());

        // 3. run the command
        final Path csv = dir.resolve("out.csv");
        final int rc = new SppCommand().run(new Args(new String[] {
            "--obs", obsFile.toString(), "--nav", navFile.toString(),
            "--decimate", "0", "--out", csv.toString(),
        }));
        assertEquals(0, rc, "command exit code");

        // 4. check the CSV recovered the receiver
        final List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertEquals(2, lines.size(), "header + one epoch");
        final String[] f = lines.get(1).split(",");
        final Vector3D fix = new Vector3D(
                Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]));
        assertTrue(fix.distance(R_TRUE) < 1.0e-2,
                "position error " + fix.distance(R_TRUE) + " m");
        assertEquals(CB_TRUE, Double.parseDouble(f[4]), 1.0e-2, "clock bias");
        assertEquals(6, Integer.parseInt(f[5]), "satellites used");
    }

    @Test
    void missingArgsReturnsUsageCode() throws Exception {
        assertEquals(2, new SppCommand().run(new Args(new String[] {"--obs", "x.rnx"})));
    }
}
