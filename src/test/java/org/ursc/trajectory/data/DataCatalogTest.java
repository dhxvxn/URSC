package org.ursc.trajectory.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ursc.trajectory.data.DataCatalog.Entry;
import org.ursc.trajectory.data.DataCatalog.Kind;
import org.ursc.trajectory.pipeline.TimeUtil;

/** Content-based identification of real repository inputs, persistence, suggestions, watching. */
class DataCatalogTest {

    private static final Path ROOT = Paths.get("").toAbsolutePath();

    private static final String SP3 = "#dP2024 10 16  0  0  0.00000000      96 ORBIT IGS20 FIT  IGS\n"
            + "## 2336 259200.00000000   900.00000000 60599 0.0000000000000\n"
            + "*  2024 10 16  0  0  0.00000000\nPG01  15000.000000  10000.000000  18000.000000      1.000000\nEOF\n";
    private static final String CLK = "     3.04           C                   G                   RINEX VERSION / TYPE\n"
            + "                                                            END OF HEADER\n"
            + "AS G01       2024 10 16 00 00  0.000000  1    1.000000000000E-04\n";

    private static void link(final Path dir, final String repoPath) throws Exception {
        final Path src = ROOT.resolve(repoPath);
        Files.createSymbolicLink(dir.resolve(src.getFileName()), src);
    }

    private static Entry byName(final List<Entry> l, final String fragment) {
        return l.stream().filter(e -> e.name.contains(fragment)).findFirst().orElseThrow(
                () -> new AssertionError("no entry " + fragment));
    }

    @Test
    void identifiesRealInputsByContent(@TempDir final Path tmp) throws Exception {
        final Path data = Files.createDirectories(tmp.resolve("incoming/sub"));
        link(data, "data/gnss/observations/2024/S3A_OPER_AUX_GNSSRD_POD__20241022T121051_V20241015T235942_20241016T235941.tgz");
        link(data, "data/attitude/2024/S3A_OPER_AUX_PROQUA_POD__20241017T104044_V20241015T235942_20241016T235941.tgz");
        link(data, "data/gnss/navigation/2024/BRD400DLR_S_20242600000_01D_MN.rnx.gz");
        link(data, "docs/reports/perturbation-ladder-truestate-7day/inputs/S3A_POEORB_truth_20241015-16.EOF");
        link(data, "docs/reports/perturbation-ladder-truestate-7day/inputs/finals2000A.all");
        link(data, "docs/reports/perturbation-ladder-truestate-7day/inputs/SpaceWeather-All-v1.2.txt");
        link(data, "data/ephemeris/lnxp1990.440");
        link(data, "docs/project-journal/inputs/eigen-6s.gfc");
        Files.writeString(data.resolve("IGS0OPSRAP_20242900000_01D_15M_ORB.SP3"), SP3);
        Files.writeString(data.resolve("IGS0OPSRAP_20242900000_01D_05M_CLK.CLK"), CLK);
        Files.writeString(data.resolve("orbit.oem"), "CCSDS_OEM_VERS = 2.0\nCREATION_DATE = x\n");
        Files.writeString(data.resolve("readme.txt"), "hello");

        final Path ws = tmp.resolve("workspace");
        final DataCatalog cat = new DataCatalog(ws);
        final List<Entry> added = cat.add(List.of(tmp.resolve("incoming").toString()));
        assertEquals(12, added.size());

        final Entry obs = byName(added, "GNSSRD");
        assertEquals(Kind.GNSS_OBSERVATIONS, obs.kind);
        assertEquals("S3A", obs.satellite);
        assertEquals("2024-10-15T23:59:42.000000", obs.coverageStart);
        assertEquals(64, obs.sha256.length());
        assertEquals(Kind.ATTITUDE_PROQUA, byName(added, "PROQUA").kind);
        final Entry nav = byName(added, "BRD400");
        assertEquals(Kind.GPS_BROADCAST_NAV, nav.kind);
        // file names carry GPS time: GPS midnight of 2024-09-16 is 23:59:42 UTC the day before
        assertEquals("2024-09-15T23:59:42.000000", nav.coverageStart);
        final Entry poe = byName(added, "POEORB");
        assertEquals(Kind.POEORB, poe.kind);
        assertEquals("2024-10-15T21:59:42.000000", poe.coverageStart);
        assertEquals(Kind.EOP, byName(added, "finals2000A").kind);
        assertEquals(Kind.SPACE_WEATHER, byName(added, "SpaceWeather").kind);
        assertEquals(Kind.DE_EPHEMERIS, byName(added, "lnxp1990").kind);
        assertEquals(Kind.GRAVITY_FIELD, byName(added, "eigen-6s").kind);
        final Entry sp3 = byName(added, "ORB.SP3");
        assertEquals(Kind.IGS_ORBIT_SP3, sp3.kind);
        assertEquals("RAPID", sp3.productClass);
        assertEquals("2024-10-17T16:59:42.000000", sp3.nominalIssueTime); // end of day + 17 h, in UTC
        assertEquals(Kind.IGS_CLOCK, byName(added, "CLK.CLK").kind);
        assertEquals(Kind.OEM, byName(added, "orbit.oem").kind);
        assertEquals(Kind.UNKNOWN, byName(added, "readme").kind);

        // persisted, reloaded, and unchanged files are not re-indexed
        final DataCatalog again = new DataCatalog(ws);
        assertEquals(12, again.entries().size());
        assertTrue(again.add(List.of(tmp.resolve("incoming").toString())).isEmpty());

        // suggestions for a cutoff on 2024-10-16
        final Map<String, List<String>> s = again.suggest(TimeUtil.parseUtc("2024-10-16T06:00:00"), 6, 1, false);
        assertEquals(List.of(obs.path), s.get("gnssrd"));
        assertEquals(2, s.get("gpsProducts").size(), s.get("gpsProducts").toString()); // SP3 + CLK, not the Sep nav
        assertFalse(s.get("gpsProducts").contains(nav.path));
        assertEquals(List.of(poe.path), s.get("poeorb"));
        assertEquals(1, s.get("proqua").size());
        assertEquals(1, s.get("eopFile").size());
        assertEquals(1, s.get("de440File").size());
    }

    @Test
    void watcherIndexesFilesDroppedIntoWatchedFolders(@TempDir final Path tmp) throws Exception {
        final Path inbox = Files.createDirectories(tmp.resolve("inbox"));
        final DataCatalog cat = new DataCatalog(tmp.resolve("ws"));
        final CountDownLatch seen = new CountDownLatch(1);
        final List<Entry> got = new ArrayList<>();
        try (FolderWatcher w = new FolderWatcher(cat, l -> {
            synchronized (got) {
                got.addAll(l);
            }
            seen.countDown();
        }, err -> { })) {
            w.watch(inbox.toString());
            final Path nested = Files.createDirectories(inbox.resolve("2024/290"));
            Thread.sleep(300);
            Files.writeString(nested.resolve("IGS0OPSFIN_20242900000_01D_15M_ORB.SP3"), SP3);
            assertTrue(seen.await(20, TimeUnit.SECONDS), "watcher did not report the new file");
        }
        synchronized (got) {
            assertEquals(Kind.IGS_ORBIT_SP3, got.get(0).kind);
            assertEquals("FINAL", got.get(0).productClass);
        }
        assertEquals(List.of(inbox.toString()), cat.watchedFolders());
    }

    /** A reference orbit or attitude file of another spacecraft is never suggested, whatever its file name says. */
    @Test
    void suggestsReferenceOrbitsOfTheSameSpacecraftOnly(@TempDir final Path tmp) throws Exception {
        final Path data = Files.createDirectories(tmp.resolve("in"));
        link(data, "data/gnss/observations/2024/S3A_OPER_AUX_GNSSRD_POD__20241008T071049_V20241001T235942_20241002T235941.tgz");
        // named S3A_ but its header says Mission Sentinel-3B
        link(data, "data/orbits/2024/S3A_POEORB_20241001-02.EOF");
        link(data, "docs/reports/perturbation-ladder-truestate-7day/inputs/S3A_POEORB_truth_20241007-08.EOF");
        final DataCatalog cat = new DataCatalog(tmp.resolve("ws"));
        cat.add(List.of(data.toString()));
        assertEquals("S3B", byName(cat.entries(), "S3A_POEORB_20241001-02").satellite);
        assertEquals("S3A", byName(cat.entries(), "truth_20241007-08").satellite);
        final Map<String, List<String>> s = cat.suggest(TimeUtil.parseUtc("2024-10-02T05:59:42"), 6.0, 7.0, false);
        assertEquals(List.of("S3A"), s.get("satellite"));
        assertTrue(s.get("poeorb").stream().noneMatch(p -> p.contains("S3A_POEORB_20241001-02")), String.valueOf(s.get("poeorb")));
        assertTrue(s.get("poeorb").stream().anyMatch(p -> p.contains("truth_20241007-08")), String.valueOf(s.get("poeorb")));
    }

    /** CODE high-rate clock headers place the file type in column 22 (not 21); still recognised. */
    @Test
    void recognisesRinexClockWithTheTypeCharacterShiftedOneColumn(@TempDir final Path tmp) throws Exception {
        final Path data = Files.createDirectories(tmp.resolve("in"));
        // exactly the layout CODE writes for COD0OPSFIN ... _30S_CLK: 'C' at index 21, not 20
        final String clk = "3.04                 C                    M                      RINEX VERSION / TYPE\n"
                + "CCRNXC V5.5          AIUB                 07-SEP-24 08:20        PGM / RUN BY / DATE\n"
                + "                                                            END OF HEADER\n"
                + "AS G01  2024 09 02 00 00  0.000000  1    1.0E-04\n";
        Files.writeString(data.resolve("COD0OPSFIN_20242460000_01D_30S_CLK.CLK"), clk);
        final DataCatalog cat = new DataCatalog(tmp.resolve("ws"));
        cat.add(List.of(data.toString()));
        assertEquals(Kind.IGS_CLOCK, byName(cat.entries(), "COD0OPSFIN").kind);
    }

    /** latestObservationEnd returns the end of the newest observation arc, as the default cutoff. */
    @Test
    void reportsTheLatestObservationEndAsADefaultCutoff(@TempDir final Path tmp) throws Exception {
        final Path data = Files.createDirectories(tmp.resolve("in"));
        link(data, "data/gnss/observations/2024/S3A_OPER_AUX_GNSSRD_POD__20241007T071056_V20240930T235942_20241001T235941.tgz");
        link(data, "data/gnss/observations/2024/S3A_OPER_AUX_GNSSRD_POD__20241008T071049_V20241001T235942_20241002T235941.tgz");
        final DataCatalog cat = new DataCatalog(tmp.resolve("ws"));
        cat.add(List.of(data.toString()));
        final String end = cat.latestObservationEnd("S3A");
        assertTrue(end.startsWith("2024-10-02T23:59"), end);
        assertEquals(end, cat.latestObservationEnd(null));
        assertEquals("", cat.latestObservationEnd("S3B"));
    }
}
