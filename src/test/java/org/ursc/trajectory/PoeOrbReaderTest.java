package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.io.PoeOrbReader;

/** Parsing tests for {@link PoeOrbReader} using a small synthetic POEORB .EOF. */
class PoeOrbReaderTest {

    private static final String EOF =
            "<?xml version=\"1.0\"?>\n"
            + "<Earth_Explorer_File>\n"
            + "  <Data_Block type=\"xml\">\n"
            + "    <List_of_OSVs count=\"2\">\n"
            + "      <OSV>\n"
            + "        <TAI>TAI=2024-10-16T00:00:37.000000</TAI>\n"
            + "        <UTC>UTC=2024-10-16T00:00:00.000000</UTC>\n"
            + "        <UT1>UT1=2024-10-16T00:00:00.057942</UT1>\n"
            + "        <X unit=\"m\">+1000000.000</X>\n"
            + "        <Y unit=\"m\">+2000000.000</Y>\n"
            + "        <Z unit=\"m\">+6800000.000</Z>\n"
            + "        <VX unit=\"m/s\">-7000.000000</VX>\n"
            + "        <VY unit=\"m/s\">+1000.000000</VY>\n"
            + "        <VZ unit=\"m/s\">+500.000000</VZ>\n"
            + "        <Quality>NOMINAL</Quality>\n"
            + "      </OSV>\n"
            + "      <OSV>\n"
            + "        <TAI>TAI=2024-10-16T00:00:47.000000</TAI>\n"
            + "        <UTC>UTC=2024-10-16T00:00:10.000000</UTC>\n"
            + "        <UT1>UT1=2024-10-16T00:00:10.057942</UT1>\n"
            + "        <X unit=\"m\">+930000.000</X>\n"
            + "        <Y unit=\"m\">+2010000.000</Y>\n"
            + "        <Z unit=\"m\">+6804000.000</Z>\n"
            + "        <VX unit=\"m/s\">-7001.000000</VX>\n"
            + "        <VY unit=\"m/s\">+1001.000000</VY>\n"
            + "        <VZ unit=\"m/s\">+505.000000</VZ>\n"
            + "        <Quality>NOMINAL</Quality>\n"
            + "      </OSV>\n"
            + "    </List_of_OSVs>\n"
            + "  </Data_Block>\n"
            + "</Earth_Explorer_File>\n";

    private PoeOrbReader.Ephemeris parse(final String text) throws IOException {
        return PoeOrbReader.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesRecordsAndFields() throws IOException {
        final PoeOrbReader.Ephemeris eph = parse(EOF);
        assertEquals(2, eph.size());

        assertEquals(1000000.0, eph.positionItrf.get(0).getX(), 1e-6);
        assertEquals(2000000.0, eph.positionItrf.get(0).getY(), 1e-6);
        assertEquals(6800000.0, eph.positionItrf.get(0).getZ(), 1e-6);
        assertEquals(-7000.0, eph.velocityItrf.get(0).getX(), 1e-6);
        assertEquals(500.0, eph.velocityItrf.get(0).getZ(), 1e-6);

        assertEquals(930000.0, eph.positionItrf.get(1).getX(), 1e-6);
        assertEquals(6804000.0, eph.positionItrf.get(1).getZ(), 1e-6);

        // records are 10 s apart
        assertEquals(10.0, eph.dates.get(1).durationFrom(eph.dates.get(0)), 1e-6);
    }

    @Test
    void readsUt1MinusUtcFromFirstRecord() throws IOException {
        final PoeOrbReader.Ephemeris eph = parse(EOF);
        assertEquals(0.057942, eph.ut1MinusUtc, 1e-9);
    }

    private Path writeEof(final String... secondsOfMinute) throws IOException {
        final StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?>\n<Data_Block>\n<List_of_OSVs>\n");
        int i = 0;
        for (final String sec : secondsOfMinute) {
            sb.append("<OSV>\n")
              .append("<UTC>UTC=2024-10-16T00:00:").append(sec).append("</UTC>\n")
              .append("<UT1>UT1=2024-10-16T00:00:").append(sec).append("</UT1>\n")
              .append("<X unit=\"m\">").append(1000.0 * i).append("</X>\n")
              .append("<Y unit=\"m\">0.0</Y>\n<Z unit=\"m\">0.0</Z>\n")
              .append("<VX unit=\"m/s\">0.0</VX>\n<VY unit=\"m/s\">0.0</VY>\n<VZ unit=\"m/s\">0.0</VZ>\n")
              .append("</OSV>\n");
            i++;
        }
        sb.append("</List_of_OSVs>\n</Data_Block>\n");
        final Path p = Files.createTempFile("poe", ".EOF");
        Files.write(p, sb.toString().getBytes(StandardCharsets.UTF_8));
        p.toFile().deleteOnExit();
        return p;
    }

    @Test
    void readAllMergesAndDedupsAcrossFiles() throws IOException {
        final Path f1 = writeEof("00.000000", "10.000000", "20.000000");
        final Path f2 = writeEof("20.000000", "30.000000"); // first epoch overlaps f1's last
        final List<Path> both = new ArrayList<>();
        both.add(f2);
        both.add(f1); // supplied out of order on purpose
        final PoeOrbReader.Ephemeris eph = PoeOrbReader.readAll(both);

        // four unique epochs (the duplicated :20 is dropped), in chronological order
        assertEquals(4, eph.size());
        assertEquals(0.0, eph.dates.get(0).durationFrom(eph.dates.get(0)), 1e-9);
        assertEquals(10.0, eph.dates.get(1).durationFrom(eph.dates.get(0)), 1e-6);
        assertEquals(20.0, eph.dates.get(2).durationFrom(eph.dates.get(0)), 1e-6);
        assertEquals(30.0, eph.dates.get(3).durationFrom(eph.dates.get(0)), 1e-6);
    }

    @Test
    void rejectsFileWithoutRecords() {
        final String empty = "<?xml version=\"1.0\"?>\n<Earth_Explorer_File></Earth_Explorer_File>\n";
        assertThrows(IOException.class, () -> parse(empty));
    }
}
