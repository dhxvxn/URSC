package org.ursc.trajectory.gnss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ursc.trajectory.gnss.products.BroadcastSource;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource.ProductClass;
import org.ursc.trajectory.gnss.products.GnssProductResolver;
import org.ursc.trajectory.gnss.products.IgsProductName;
import org.ursc.trajectory.gnss.products.PreciseSource;
import org.ursc.trajectory.gnss.products.ProductLoader;
import org.ursc.trajectory.gnss.products.RinexClkReader;
import org.ursc.trajectory.gnss.products.Sp3Reader;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * IGS precise products: SP3/CLK parsing and interpolation, relativistic clock convention,
 * product naming, loader pairing, and the priority/cutoff resolver.
 */
class GnssProductsTest {

    private static final double C = GpsConstants.SPEED_OF_LIGHT;
    private static final AbsoluteDate DAY =
            new AbsoluteDate(2024, 10, 16, 0, 0, 0.0, TimeScalesFactory.getGPS());
    private static final AbsoluteDate NOON = DAY.shiftedBy(43200.0);

    /** Eccentric (e=0.02), harmonically perturbed GPS-like ephemeris referenced at noon. */
    private static GpsBroadcastEphemeris sat(final int prn) {
        final double af0 = 1.0e-4 * (prn % 5 - 2);
        final double af1 = 1.0e-11 * (prn % 3 - 1);
        return new GpsBroadcastEphemeris(prn, NOON, NOON, 0.0, af0, af1, 0.0,
                5153.65, 0.02, (prn * 0.71) % 6.283, 4.5e-9,
                (prn * 1.05) % 6.283, 0.96, 0.4 + 0.1 * prn, -8.0e-9, 1.0e-10,
                1.0e-6, 5.0e-6, 250.0, 30.0, 1.0e-7, -5.0e-8);
    }

    /** Clock polynomial without the relativistic term, as IGS products carry it. */
    private static double polyClock(final int prn, final AbsoluteDate t) {
        final double af0 = 1.0e-4 * (prn % 5 - 2);
        final double af1 = 1.0e-11 * (prn % 3 - 1);
        return af0 + af1 * t.durationFrom(NOON);
    }

    private static final int[] PRNS = {1, 3, 6, 9, 12, 14, 17, 21, 24, 28, 30, 32};

    private static String sp3Text(final double step, final boolean withClocks) {
        final StringBuilder sb = new StringBuilder();
        final int n = (int) Math.round(86400.0 / step);
        sb.append(String.format(Locale.US, "#dP2024 10 16  0  0  0.00000000 %7d ORBIT IGS20 FIT  IGS%n", n));
        sb.append(String.format(Locale.US, "## 2336 259200.00000000 %14.8f 60599 0.0000000000000%n", step));
        sb.append("+   ").append(PRNS.length).append("\n");
        sb.append("%c G  cc GPS ccc cccc cccc cccc cccc ccccc ccccc ccccc ccccc\n");
        sb.append("%c cc cc ccc ccc cccc cccc cccc cccc ccccc ccccc ccccc ccccc\n");
        sb.append("/* synthetic test product\n");
        for (int k = 0; k < n; k++) {
            final AbsoluteDate t = DAY.shiftedBy(k * step);
            final int hh = (int) (k * step / 3600), mm = (int) ((k * step % 3600) / 60);
            final double ss = k * step % 60;
            sb.append(String.format(Locale.US, "*  2024 10 16 %2d %2d %11.8f%n", hh, mm, ss));
            for (final int prn : PRNS) {
                final Vector3D p = sat(prn).positionEcef(t);
                sb.append(String.format(Locale.US, "PG%02d%14.6f%14.6f%14.6f%14.6f%n", prn,
                        p.getX() / 1000, p.getY() / 1000, p.getZ() / 1000,
                        withClocks ? polyClock(prn, t) * 1e6 : 999999.999999));
            }
        }
        sb.append("EOF\n");
        return sb.toString();
    }

    private static String clkText(final boolean v304) {
        final StringBuilder sb = new StringBuilder();
        sb.append(v304 ? "     3.04           C                   G                   RINEX VERSION / TYPE\n"
                       : "     3.00           C                   G                   RINEX VERSION / TYPE\n");
        sb.append("GPS                                                         TIME SYSTEM ID\n");
        sb.append("                                                            END OF HEADER\n");
        for (int k = 0; k < 288; k++) {
            final AbsoluteDate t = DAY.shiftedBy(k * 300.0);
            final int hh = k * 300 / 3600, mm = (k * 300 % 3600) / 60;
            for (final int prn : PRNS) {
                sb.append(String.format(Locale.US, v304
                        ? "AS G%02d       2024 10 16 %2d %2d  0.000000  1   %19.12E%n"
                        : "AS G%02d  2024 10 16 %2d %2d  0.000000  1   %19.12E%n", prn, hh, mm, polyClock(prn, t)));
            }
        }
        return sb.toString();
    }

    private static Sp3Reader.Data sp3(final boolean clocks) throws IOException {
        return Sp3Reader.read(new ByteArrayInputStream(sp3Text(900.0, clocks).getBytes(StandardCharsets.US_ASCII)));
    }

    private static RinexClkReader.Data clk(final boolean v304) throws IOException {
        return RinexClkReader.read(new ByteArrayInputStream(clkText(v304).getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void sp3InterpolationMatchesTheOrbitToMillimetres() throws IOException {
        final Sp3Reader.Data d = sp3(true);
        assertEquals(PRNS.length, d.prns().size());
        assertEquals(900.0, d.interval(), 0.0);
        double worst = 0.0;
        for (final int prn : PRNS) {
            for (double s = 3 * 3600 + 137.0; s < 21 * 3600; s += 1111.0) {
                final AbsoluteDate t = DAY.shiftedBy(s);
                worst = Math.max(worst, d.position(prn, t).distance(sat(prn).positionEcef(t)));
            }
        }
        assertTrue(worst < 0.005, "SP3 interpolation error " + worst + " m");
        assertFalse(d.coversOrbit(1, DAY.shiftedBy(-60)), "no extrapolation before the file");
    }

    @Test
    void clockReadersAgreeAndHandleBothRinexClockLayouts() throws IOException {
        final RinexClkReader.Data a = clk(false);
        final RinexClkReader.Data b = clk(true);
        assertEquals(300.0, a.interval(), 1e-9);
        final AbsoluteDate t = DAY.shiftedBy(7777.0);
        for (final int prn : PRNS) {
            assertEquals(polyClock(prn, t), a.clock(prn, t), 1e-15);
            assertEquals(polyClock(prn, t), b.clock(prn, t), 1e-15);
        }
        assertTrue(Double.isNaN(a.clock(2, t)), "absent satellite has no clock");
    }

    @Test
    void preciseClockAddsTheRelativisticTermWithTheBroadcastSign() throws IOException {
        final PreciseSource src = new PreciseSource(sp3(false), clk(true), ProductClass.FINAL,
                "test", null, false, null);
        double worst = 0.0;
        for (final int prn : PRNS) {
            for (double s = 4 * 3600; s < 20 * 3600; s += 1234.0) {
                final AbsoluteDate t = DAY.shiftedBy(s);
                // broadcast clock = polynomial + F e sqrtA sinE (independent IS-GPS-200 code)
                worst = Math.max(worst, Math.abs(src.clockBias(prn, t) - sat(prn).clockBias(t)));
            }
        }
        // e=0.02 gives a ~23 ns relativistic amplitude, so a wrong sign would give ~46 ns errors.
        // The tolerance allows the genuine model difference between the IGS convention
        // (-2 r.v/c^2 on the actual orbit) and the broadcast Keplerian form (F e sqrtA sinE)
        // when harmonic corrections (crc/crs/...) are present: tens of picoseconds.
        assertTrue(worst < 1.0e-10, "clock mismatch " + worst + " s");
    }

    @Test
    void sppWithPreciseProductRecoversTheReceiver() throws IOException {
        final PreciseSource src = new PreciseSource(sp3(false), clk(false), ProductClass.FINAL,
                "test", null, false, null);
        final AbsoluteDate tRx = DAY.shiftedBy(10 * 3600 + 30);
        final Vector3D rTrue = new Vector3D(-1.2e6, 3.3e6, 6.1e6);
        final double cb = -876.5;
        final Map<String, Integer> idx = new LinkedHashMap<>();
        idx.put("C1W", 0);
        idx.put("C2W", 1);
        final List<RinexObsReader.SatRecord> recs = new ArrayList<>();
        for (final int prn : PRNS) {
            final GpsBroadcastEphemeris eph = sat(prn);
            double tau = 0.07;
            for (int k = 0; k < 6; k++) {
                tau = rTrue.distance(SppEngine.satellitePositionAtReception(eph, tRx, tau)) / C;
            }
            final Vector3D s = SppEngine.satellitePositionAtReception(eph, tRx, tau);
            // only satellites above the receiver's horizon
            if (s.subtract(rTrue).dotProduct(rTrue) <= 0) {
                continue;
            }
            final double p = C * tau + cb - C * eph.clockBias(tRx.shiftedBy(-tau));
            recs.add(new RinexObsReader.SatRecord(prn, idx, new double[] {p, p}));
        }
        assertTrue(recs.size() >= 4, "need >=4 visible satellites, have " + recs.size());
        final SppSolution sol = new SppEngine().solveEpoch(
                new RinexObsReader.Epoch(tRx, 0.0, recs), src, Vector3D.ZERO);
        assertNotNull(sol);
        assertTrue(sol.getPosition().distance(rTrue) < 0.05, "position error " + sol.getPosition().distance(rTrue));
        assertEquals(cb, sol.getClockBias(), 0.05);
        assertNotNull(sol.covariance(5.0));
        assertEquals(recs.size(), sol.getPrns().length);
    }

    @Test
    void parsesLongAndLegacyIgsNames() {
        final IgsProductName fin = IgsProductName.parse("IGS0OPSFIN_20242900000_01D_15M_ORB.SP3.gz");
        assertTrue(fin.recognised());
        assertEquals(ProductClass.FINAL, fin.productClass());
        assertEquals("IGS", fin.provider());
        assertEquals(IgsProductName.Content.ORBIT, fin.content());
        assertEquals(0.0, fin.start().durationFrom(DAY), 1e-6);
        assertEquals(86400.0 + IgsProductName.FINAL_LATENCY_AFTER_END_S,
                fin.nominalIssueTime().durationFrom(DAY), 1e-6);

        final IgsProductName rap = IgsProductName.parse("COD0OPSRAP_20242900000_01D_05M_CLK.CLK");
        assertEquals(ProductClass.RAPID, rap.productClass());
        assertEquals("COD", rap.provider());
        assertEquals(IgsProductName.Content.CLOCK, rap.content());

        final IgsProductName ult = IgsProductName.parse("IGS0OPSULT_20242900600_02D_15M_ORB.SP3");
        assertEquals(ProductClass.ULTRA_RAPID, ult.productClass());
        assertEquals(6 * 3600.0, ult.start().durationFrom(DAY), 1e-6);
        assertEquals(30 * 3600.0, ult.observedEnd().durationFrom(DAY), 1e-6);
        assertEquals(33 * 3600.0, ult.nominalIssueTime().durationFrom(DAY), 1e-6);

        final IgsProductName legacy = IgsProductName.parse("/archive/igs23363.sp3.Z");
        assertEquals(ProductClass.FINAL, legacy.productClass());
        assertEquals(0.0, legacy.start().durationFrom(DAY), 1e-6);
        assertEquals(ProductClass.RAPID, IgsProductName.parse("igr23363.clk").productClass());

        final IgsProductName igu = IgsProductName.parse("igu23363_06.sp3");
        assertEquals(ProductClass.ULTRA_RAPID, igu.productClass());
        assertEquals(6 * 3600.0, igu.observedEnd().durationFrom(DAY), 1e-6);
        assertEquals(9 * 3600.0, igu.nominalIssueTime().durationFrom(DAY), 1e-6);

        assertFalse(IgsProductName.parse("orbit.sp3").recognised());
    }

    /** Minimal source for resolver tests. */
    private static GnssOrbitClockSource fake(final ProductClass cls, final AbsoluteDate issue,
                                             final boolean verified, final Set<Integer> covered) {
        return new GnssOrbitClockSource() {
            public ProductClass productClass() {
                return cls;
            }

            public String description() {
                return cls.label;
            }

            public AbsoluteDate issueTime() {
                return issue;
            }

            public boolean issueTimeVerified() {
                return verified;
            }

            public boolean covers(final int prn, final AbsoluteDate t) {
                return covered.contains(prn);
            }

            public Vector3D positionEcef(final int prn, final AbsoluteDate t) {
                return Vector3D.ZERO;
            }

            public double clockBias(final int prn, final AbsoluteDate t) {
                return 0.0;
            }
        };
    }

    @Test
    void resolverAppliesEligibilityBeforePriority() {
        final AbsoluteDate cutoff = DAY.shiftedBy(6 * 3600);
        final Set<Integer> all = Set.of(1, 2, 3, 4, 5, 6);
        final GnssOrbitClockSource fin = fake(ProductClass.FINAL, DAY.shiftedBy(18 * 86400), false, all);
        final GnssOrbitClockSource rap = fake(ProductClass.RAPID, DAY.shiftedBy(-7 * 3600), false, all);
        final GnssOrbitClockSource ult = fake(ProductClass.ULTRA_RAPID, DAY.shiftedBy(3 * 3600), true, all);
        final List<GnssOrbitClockSource> sources = List.of(ult, fin, rap);

        // forecast: the Final issued after cutoff must not be used; Rapid outranks Ultra
        final GnssProductResolver fc = new GnssProductResolver(sources, GnssProductResolver.Mode.FORECAST,
                cutoff, GnssProductResolver.Eligibility.NOMINAL_LATENCY, null, 4);
        assertEquals(2, fc.eligible().size());
        assertEquals(ProductClass.RAPID, fc.select(cutoff, all).source.productClass());
        assertTrue(fc.rejected().get(0).contains("after cutoff"));

        // reconstruction: best present, i.e. Final
        final GnssProductResolver rc = new GnssProductResolver(sources, GnssProductResolver.Mode.RECONSTRUCTION,
                cutoff, GnssProductResolver.Eligibility.NOMINAL_LATENCY, null, 4);
        assertEquals(ProductClass.FINAL, rc.select(cutoff, all).source.productClass());

        // verified-only: the nominal-latency Rapid is excluded, the verified Ultra remains
        final GnssProductResolver strict = new GnssProductResolver(sources, GnssProductResolver.Mode.FORECAST,
                cutoff, GnssProductResolver.Eligibility.REQUIRE_VERIFIED, null, 4);
        assertEquals(ProductClass.ULTRA_RAPID, strict.select(cutoff, all).source.productClass());

        // forced tier
        final GnssProductResolver forced = new GnssProductResolver(sources, GnssProductResolver.Mode.RECONSTRUCTION,
                cutoff, GnssProductResolver.Eligibility.NOMINAL_LATENCY, ProductClass.ULTRA_RAPID, 4);
        assertEquals(ProductClass.ULTRA_RAPID, forced.select(cutoff, all).source.productClass());
    }

    @Test
    void resolverFallsBackWholeEpochAndRecordsWhy() {
        final GnssOrbitClockSource fin = fake(ProductClass.FINAL, null, false, Set.of(1, 2, 3));
        final GnssOrbitClockSource rap = fake(ProductClass.RAPID, null, false, Set.of(1, 2, 3, 4, 5));
        final GnssProductResolver r = new GnssProductResolver(List.of(fin, rap),
                GnssProductResolver.Mode.RECONSTRUCTION, null, GnssProductResolver.Eligibility.NOMINAL_LATENCY, null, 4);
        final GnssProductResolver.Selection s = r.select(DAY, List.of(1, 2, 3, 4, 5));
        assertEquals(ProductClass.RAPID, s.source.productClass());
        assertTrue(s.fallbackReason.contains("IGS Final covers 3 of 5"));
        assertNull(r.select(DAY, List.of(7, 8, 9, 10)));
        assertEquals(1, r.usage().get("IGS Rapid"));
    }

    @Test
    void forecastModeRejectsPreciseProductsWithUnknownIssueTimeAndFiltersBroadcast() {
        final AbsoluteDate cutoff = NOON;
        final GnssOrbitClockSource unknown = fake(ProductClass.FINAL, null, false, Set.of(1, 2, 3, 4));
        final Map<Integer, List<GpsBroadcastEphemeris>> nav = new LinkedHashMap<>();
        nav.put(1, List.of(sat(1)));
        final GpsBroadcastEphemeris later = new GpsBroadcastEphemeris(2, NOON.shiftedBy(7200), NOON.shiftedBy(7200),
                0, 0, 0, 0, 5153.65, 0.01, 0, 0, 0, 0.96, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        nav.put(2, List.of(sat(2), later));
        final BroadcastSource brd = new BroadcastSource(new RinexNavReader.Data(nav), "brd");
        final GnssProductResolver r = new GnssProductResolver(List.of(unknown, brd),
                GnssProductResolver.Mode.FORECAST, cutoff, GnssProductResolver.Eligibility.NOMINAL_LATENCY, null, 1);
        assertEquals(1, r.eligible().size());
        assertTrue(r.rejected().get(0).contains("issue time unknown"));
        final BroadcastSource kept = (BroadcastSource) r.eligible().get(0);
        assertEquals(2, kept.data().messageCount(), "post-cutoff message dropped");
    }

    @Test
    void loaderPairsOrbitAndClockAndReportsUnknownNames(@TempDir final Path dir) throws IOException {
        Files.writeString(dir.resolve("IGS0OPSRAP_20242900000_01D_15M_ORB.SP3"), sp3Text(900.0, true));
        Files.writeString(dir.resolve("IGS0OPSRAP_20242900000_01D_05M_CLK.CLK"), clkText(true));
        Files.writeString(dir.resolve("mystery.sp3"), sp3Text(900.0, true));
        Files.writeString(dir.resolve("notes.txt"), "hello");
        final List<Path> files = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.sorted().forEach(files::add);
        }
        final ProductLoader.Loaded loaded = ProductLoader.load(files, Map.of());
        assertEquals(1, loaded.sources.size());
        final GnssOrbitClockSource src = loaded.sources.get(0);
        assertEquals(ProductClass.RAPID, src.productClass());
        assertTrue(src.description().contains("CLK.CLK"), src.description());
        assertFalse(src.issueTimeVerified());
        assertEquals(1, loaded.problems.size());
        assertTrue(loaded.problems.get(0).startsWith("mystery.sp3"));
        assertEquals(ProductLoader.Kind.OTHER, ProductLoader.sniff(dir.resolve("notes.txt")));
    }
}
