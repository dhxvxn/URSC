package org.ursc.trajectory.gnss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Parsing tests for {@link RinexObsReader}, using an inline RINEX 3.03 GPS snippet
 * modelled on the Sentinel-3A GNSSRD product (same observation-type layout).
 */
class RinexObsReaderTest {

    // Two epochs, three GPS satellites, the GNSSRD observation-type set.
    private static final String RINEX =
            "     3.03           O                   G                   RINEX VERSION / TYPE\n"
          + "G    9 C1C L1C S1C C1W L1W S1W C2W L2W S2W                  SYS / # / OBS TYPES\n"
          + "  2024    10    16     0     0    0.0000000     GPS         TIME OF FIRST OBS\n"
          + "                                                            END OF HEADER\n"
          + "> 2024 10 16  0  0  0.0000000  0  3       0.008219661246\n"
          + "G02  22756293.467    -3152045.0100         44.140    22756291.983"
              + "    -3152045.0100         28.760    22756289.994   -21489978.7300         28.760  \n"
          + "G04  23327226.811   -13707615.4970         39.980    23327227.536"
              + "   -13707615.4970         23.200    23327227.562   -29555293.9240         23.200  \n"
          + "G08  21528850.796   -23549879.8220         46.200    21528851.503"
              + "   -23549879.8220         42.290    21528853.021   -36634822.0130         42.290  \n"
          + "> 2024 10 16  0  0  1.0000000  0  3       0.008219699931\n"
          + "G02  22760397.653    -3130475.4630         44.100    22760396.510"
              + "    -3130475.4630         28.830    22760394.512   -21473171.2970         28.830  \n"
          + "G04  23326364.849   -13712148.0720         40.050    23326364.831"
              + "   -13712148.0720         23.260    23326364.901   -29558825.7990         23.260  \n"
          + "G08  21524689.678   -23571748.5600         46.250    21524689.992"
              + "   -23571748.5600         42.370    21524691.519   -36651862.5810         42.370  \n";

    private static RinexObsReader.Data parse() throws IOException {
        return RinexObsReader.read(
                new ByteArrayInputStream(RINEX.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesHeaderObsTypes() throws IOException {
        final RinexObsReader.Data data = parse();
        assertEquals(9, data.getObsTypes().size());
        assertEquals("C1C", data.getObsTypes().get(0));
        assertEquals("C2W", data.getObsTypes().get(6));
    }

    @Test
    void parsesEpochsAndObservations() throws IOException {
        final RinexObsReader.Data data = parse();
        assertEquals(2, data.size());

        final RinexObsReader.Epoch e0 = data.getEpochs().get(0);
        assertEquals(3, e0.getSatellites().size());
        // epoch tag is GPS time 2024-10-16 00:00:00
        final double[] c = e0.getDate().getComponents(TimeScalesFactory.getGPS());
        assertEquals(2024, (int) c[0]);
        assertEquals(10, (int) c[1]);
        assertEquals(16, (int) c[2]);

        final RinexObsReader.SatRecord g02 = e0.getSatellites().get(0);
        assertEquals(2, g02.getPrn());
        assertEquals(22756293.467, g02.obs("C1C"), 1e-3);
        assertEquals(22756291.983, g02.obs("C1W"), 1e-3);
        assertEquals(22756289.994, g02.obs("C2W"), 1e-3);

        // 1 Hz data advances one second between epochs
        assertEquals(1.0, data.getEpochs().get(1).getDate()
                .durationFrom(e0.getDate()), 1e-6);
    }

    @Test
    void ionoFreePseudorangeIsBetweenL1AndL2() throws IOException {
        final RinexObsReader.SatRecord g02 = parse().getEpochs().get(0).getSatellites().get(0);
        final double p1 = g02.obs("C1W");
        final double p2 = g02.obs("C2W");
        final double pif = g02.ionoFreePseudorange();
        // iono-free is a linear combination with coefficients ~+2.546 and ~-1.546
        final double expected = GpsConstants.ionoFreePseudorange(p1, p2);
        assertEquals(expected, pif, 1e-6);
        // sanity: result is a finite range within a few km of the raw codes
        assertTrue(Math.abs(pif - p1) < 5000.0, "iono-free jump " + (pif - p1));
    }

    @Test
    void decimationThinsEpochs() throws IOException {
        // request one epoch per 10 s: only the first of the two 1-Hz epochs survives
        final RinexObsReader.Data data = RinexObsReader.read(
                new ByteArrayInputStream(RINEX.getBytes(StandardCharsets.UTF_8)), 10.0);
        assertEquals(1, data.size());
    }
}
