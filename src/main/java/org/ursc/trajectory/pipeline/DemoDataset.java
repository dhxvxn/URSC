package org.ursc.trajectory.pipeline;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.gnss.GpsBroadcastEphemeris;
import org.ursc.trajectory.gnss.GpsConstants;
import org.ursc.trajectory.gnss.SppEngine;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Writes a clearly labelled <b>simulated</b> dataset in real file formats so the complete
 * OD + forecast workflow can be tried without real data: RINEX 3 GNSS observations of a
 * simulated LEO satellite, IGS-style Final/Rapid/Ultra-rapid SP3 + CLK files and RINEX 3
 * broadcast navigation for one synthetic GPS constellation, and a reference orbit in POEORB
 * format. The spacecraft truth uses the GUI's default force model, so a run with default
 * settings recovers it to the measurement-noise level: a consistency demonstration, not a
 * statement about real-world accuracy.
 */
public final class DemoDataset {

    private static final double C = GpsConstants.SPEED_OF_LIGHT;
    private static final AbsoluteDate GPS_EPOCH =
            new AbsoluteDate(1980, 1, 6, 0, 0, 0.0, TimeScalesFactory.getGPS());
    /** Demo day (no real data in the repository covers it, so it cannot be mixed up). */
    private static final int YEAR = 2025;
    private static final int MONTH = 1;
    private static final int DAY = 15;
    /** Suggested forecast cutoff for the demo. */
    public static final String CUTOFF_UTC = "2025-01-15T06:00:00";
    public static final String OBS_FILE = "SIM_DEMO_GNSS_OBS_20250115.rnx";
    public static final String REF_FILE = "SIM_DEMO_REFERENCE_ORBIT_20250115.EOF";

    private DemoDataset() {
    }

    /** Synthetic GPS satellite referenced at {@code ref} (6 planes x 4 slots). */
    private static GpsBroadcastEphemeris gps(final int prn, final AbsoluteDate ref, final double dt) {
        final int plane = (prn - 1) / 4;
        final int slot = (prn - 1) % 4;
        final double sqrtA = 5153.6 + 0.3 * slot;
        final double a = sqrtA * sqrtA;
        final double n = Math.sqrt(GpsConstants.GM / (a * a * a)) + 4.5e-9;
        final double omegaDot = -8.0e-9;
        final double iDot = 2.0e-10;
        final double af0 = 1.0e-5 * ((prn * 7) % 11 - 5);
        final double af1 = 1.0e-12 * ((prn * 3) % 5 - 2);
        final AbsoluteDate t = ref.shiftedBy(dt);
        final double sow = t.durationFrom(GPS_EPOCH) % (7 * 86400.0);
        // re-referencing the same physical orbit to a later epoch: M0, Omega0, i0 and the clock shift
        return new GpsBroadcastEphemeris(prn, t, t, sow, af0 + af1 * dt, af1, 0.0,
                sqrtA, 0.004 + 0.001 * slot, slot * Math.PI / 2 + plane * 0.31 + n * dt, 4.5e-9,
                plane * Math.PI / 3 + omegaDot * dt, 0.958 + iDot * dt, 0.3 * plane, omegaDot, iDot,
                1.0e-6, 4.0e-6, 220.0, 25.0, 1.0e-7, -6.0e-8);
    }

    /** Clock polynomial without the relativistic term (as IGS clock products carry it). */
    private static double poly(final int prn, final AbsoluteDate ref, final AbsoluteDate t) {
        final double af0 = 1.0e-5 * ((prn * 7) % 11 - 5);
        final double af1 = 1.0e-12 * ((prn * 3) % 5 - 2);
        return af0 + af1 * t.durationFrom(ref);
    }

    /**
     * Write the dataset into {@code dir} (created if needed).
     *
     * @return the files written
     */
    public static List<Path> write(final Path dir, final ProgressListener pl) throws IOException {
        Files.createDirectories(dir);
        final List<Path> out = new ArrayList<>();
        final AbsoluteDate ref = new AbsoluteDate(YEAR, MONTH, DAY, 0, 0, 0.0, TimeScalesFactory.getGPS());
        final String doy = doy(ref);
        final String doyPrev = doy(ref.shiftedBy(-86400.0));
        final AbsoluteDate obsStart = ref.shiftedBy(-2 * 3600.0);
        final AbsoluteDate obsEnd = ref.shiftedBy(8 * 3600.0);
        final AbsoluteDate truthEnd = ref.shiftedBy(36 * 3600.0);

        // ---- spacecraft truth with the GUI defaults (generic cannonball, all forces, no optional data)
        pl.stage("Simulating the satellite orbit", 0.05);
        final RunConfig cfg = new RunConfig();
        final List<RunResult.State> truth;
        try (Environment env = Environment.setUp(cfg, null);
             org.ursc.trajectory.orekit.OrekitSession session = OdForecastPipeline.openSession(cfg, env,
                     obsStart.shiftedBy(-86400.0), truthEnd.shiftedBy(86400.0))) {
            final double r = Constants.EARTH_EQUATORIAL_RADIUS + 790e3;
            final double v = Math.sqrt(Constants.EARTH_MU / r);
            final double inc = Math.toRadians(98.6);
            final double raan = Math.toRadians(40);
            final org.orekit.time.AbsoluteDate t0 = org.ursc.trajectory.orekit.OrekitDates.toOrekit(obsStart);
            final org.orekit.propagation.SpacecraftState s0 = new org.orekit.propagation.SpacecraftState(
                    new org.orekit.orbits.CartesianOrbit(new org.orekit.utils.PVCoordinates(
                            new org.hipparchus.geometry.euclidean.threed.Vector3D(r * Math.cos(raan), r * Math.sin(raan), 0),
                            new org.hipparchus.geometry.euclidean.threed.Vector3D(-v * Math.cos(inc) * Math.sin(raan),
                                    v * Math.cos(inc) * Math.cos(raan), v * Math.sin(inc))),
                            session.gcrf, t0, org.orekit.utils.Constants.EIGEN5C_EARTH_MU)).withMass(cfg.spacecraft.massKg);
            final RunConfig c30 = RunConfig.fromJson(cfg.toJson(), new ArrayList<>());
            c30.forecast.outputStepSeconds = 30.0;
            truth = org.ursc.trajectory.orekit.OrekitForecast.run(s0, null, List.of(),
                    () -> org.ursc.trajectory.orekit.OrekitDynamics.forces(c30, env, session, c30.spacecraft.cd,
                            c30.spacecraft.cr, new ArrayList<>()),
                    org.ursc.trajectory.orekit.OrekitDynamics.attitude(c30, env, session), t0,
                    org.ursc.trajectory.orekit.OrekitDates.toOrekit(truthEnd), c30, ProgressListener.NONE);
        }

        // ---- GNSS observations (RINEX 3.03, GPS time), 30 s: dual-frequency code (1 m noise) and
        //      carrier phase (2 mm noise, one random integer ambiguity per satellite and frequency)
        pl.stage("Writing GNSS observations", 0.55);
        final Path obs = dir.resolve(OBS_FILE);
        final Random rnd = new Random(20241016L);
        try (BufferedWriter w = Files.newBufferedWriter(obs)) {
            w.write(hdr("     3.03           OBSERVATION DATA    G: GPS", "RINEX VERSION / TYPE"));
            w.write(hdr("SIMULATED DEMO DATA - NOT A REAL SATELLITE", "COMMENT"));
            w.write(hdr("DEMO                SIM                 20250115 000000 UTC", "PGM / RUN BY / DATE"));
            w.write(hdr("SIMSAT", "MARKER NAME"));
            w.write(hdr("SPACEBORNE", "MARKER TYPE"));
            w.write(hdr("SIM                 SIM", "OBSERVER / AGENCY"));
            w.write(hdr("0                   SIMULATED           0", "REC # / TYPE / VERS"));
            w.write(hdr("0                   SIMULATED", "ANT # / TYPE"));
            w.write(hdr("        0.0000        0.0000        0.0000", "ANTENNA: DELTA H/E/N"));
            w.write(hdr("G    4 C1W L1W C2W L2W", "SYS / # / OBS TYPES"));
            final double[] f0 = obsStart.getComponents(TimeScalesFactory.getGPS());
            w.write(hdr(String.format(Locale.US, "  %4d    %2d    %2d    %2d    %2d   %10.7f     GPS",
                    (int) f0[0], (int) f0[1], (int) f0[2], (int) f0[3], (int) f0[4], f0[5]), "TIME OF FIRST OBS"));
            w.write(hdr("G L1W  0.00000", "SYS / PHASE SHIFT"));
            w.write(hdr("G L2W  0.00000", "SYS / PHASE SHIFT"));
            w.write(hdr("  0", "GLONASS SLOT / FRQ #"));
            w.write(hdr("", "GLONASS COD/PHS/BIS"));
            w.write(hdr("", "END OF HEADER"));
            for (int k = 0; obsStart.shiftedBy(k * 30.0).compareTo(obsEnd) <= 0; k++) {
                final AbsoluteDate tRx = obsStart.shiftedBy(k * 30.0);
                final RunResult.State s = truth.get(k);
                final Vector3D rcv = FramesFactory.getTransform(FramesFactory.getGCRF(), FramesFactory.getITRF(), tRx)
                        .transformPosition(s.position);
                final StringBuilder sats = new StringBuilder();
                int n = 0;
                for (int prn = 1; prn <= 24; prn++) {
                    final GpsBroadcastEphemeris e = gps(prn, ref, 0.0);
                    double tau = 0.07;
                    for (int it = 0; it < 6; it++) {
                        tau = rcv.distance(SppEngine.satellitePositionAtReception(e, tRx, tau)) / C;
                    }
                    final Vector3D sat = SppEngine.satellitePositionAtReception(e, tRx, tau);
                    if (sat.subtract(rcv).dotProduct(rcv) <= 0) {
                        continue;
                    }
                    final double clockM = 1500.0 + 0.02 * tRx.durationFrom(obsStart);
                    final double range = C * tau + clockM - C * e.clockBias(tRx.shiftedBy(-tau));
                    final double p = range + rnd.nextGaussian();
                    final double ph = range + 0.002 * rnd.nextGaussian();
                    final double l1 = ph / (C / GpsConstants.F_L1) + 1000.0 * prn;
                    final double l2 = ph / (C / GpsConstants.F_L2) - 700.0 * prn;
                    sats.append(String.format(Locale.US, "G%02d%14.3f  %14.3f  %14.3f  %14.3f  %n", prn, p, l1, p, l2));
                    n++;
                }
                final double[] c = tRx.getComponents(TimeScalesFactory.getGPS());
                w.write(String.format(Locale.US, "> %4d %02d %02d %02d %02d %10.7f  0 %2d%n",
                        (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], c[5], n));
                w.write(sats.toString());
            }
        }
        out.add(obs);

        // ---- GPS products
        pl.stage("Writing GPS products", 0.7);
        out.add(sp3(dir.resolve("IGS0OPSFIN_" + doy + "0000_01D_15M_ORB.SP3"), ref, ref, 96, false));
        out.add(clk(dir.resolve("IGS0OPSFIN_" + doy + "0000_01D_30S_CLK.CLK"), ref, ref, 2880, 30.0));
        out.add(sp3(dir.resolve("IGS0OPSRAP_" + doy + "0000_01D_15M_ORB.SP3"), ref, ref, 96, false));
        out.add(clk(dir.resolve("IGS0OPSRAP_" + doy + "0000_01D_05M_CLK.CLK"), ref, ref, 288, 300.0));
        // Ultra-rapid issued at 03:00 on the 16th: observed 15th, predicted 16th; clocks in the SP3 (no CLK)
        out.add(sp3(dir.resolve("IGS0OPSULT_" + doyPrev + "0000_02D_15M_ORB.SP3"), ref.shiftedBy(-86400.0), ref, 192, false));
        out.add(nav(dir.resolve("SIMD00XXX_R_" + doyPrev + "0000_02D_GN.rnx"), ref));

        // ---- reference orbit (POEORB format), Earth-fixed, 60 s
        pl.stage("Writing the reference orbit", 0.85);
        final Path eof = dir.resolve(REF_FILE);
        try (BufferedWriter w = Files.newBufferedWriter(eof)) {
            w.write("<?xml version=\"1.0\"?>\n<Earth_Explorer_File>\n<Earth_Explorer_Header><Fixed_Header>\n");
            w.write("<File_Name>" + REF_FILE.replace(".EOF", "") + "</File_Name>\n");
            w.write("<File_Description>SIMULATED reference orbit for the demo (not a real POEORB)</File_Description>\n");
            w.write("<Notes>Simulated truth in POEORB layout for the comparison display</Notes>\n<Mission>SIMSAT</Mission>\n");
            w.write("<File_Type>AUX_POEORB</File_Type>\n<Validity_Period>\n");
            final AbsoluteDate e0 = TimeUtil.parseUtc(String.format(Locale.US, "%04d-%02d-%02dT00:00:00", YEAR, MONTH, DAY));
            final AbsoluteDate e1 = truth.get(truth.size() - 1).date;
            w.write("<Validity_Start>UTC=" + TimeUtil.formatUtc(e0) + "</Validity_Start>\n");
            w.write("<Validity_Stop>UTC=" + TimeUtil.formatUtc(e1.shiftedBy(-30)) + "</Validity_Stop>\n</Validity_Period>\n");
            w.write("</Fixed_Header></Earth_Explorer_Header>\n<Data_Block type=\"xml\">\n<List_of_OSVs>\n");
            for (int k = 0; k < truth.size(); k += 2) { // 60 s spacing
                final RunResult.State s = truth.get(k);
                if (s.date.isBefore(e0)) {
                    continue;
                }
                final FrameUtil.PV pv = FrameUtil.gcrfToItrf(s.position, s.velocity, s.date);
                final String utc = TimeUtil.formatUtc(s.date);
                w.write("<OSV>\n<TAI>TAI=" + TimeUtil.formatUtc(s.date.shiftedBy(37)) + "</TAI>\n<UTC>UTC=" + utc
                        + "</UTC>\n<UT1>UT1=" + utc + "</UT1>\n<Absolute_Orbit>+1</Absolute_Orbit>\n");
                w.write(String.format(Locale.US, "<X unit=\"m\">%.6f</X>\n<Y unit=\"m\">%.6f</Y>\n<Z unit=\"m\">%.6f</Z>\n"
                        + "<VX unit=\"m/s\">%.6f</VX>\n<VY unit=\"m/s\">%.6f</VY>\n<VZ unit=\"m/s\">%.6f</VZ>\n<Quality>SIMULATED</Quality>\n</OSV>\n",
                        pv.r.getX(), pv.r.getY(), pv.r.getZ(), pv.v.getX(), pv.v.getY(), pv.v.getZ()));
            }
            w.write("</List_of_OSVs>\n</Data_Block>\n</Earth_Explorer_File>\n");
        }
        out.add(eof);

        final Path readme = dir.resolve("README-SIMULATED-DATA.txt");
        Files.writeString(readme, String.join("\n",
                "Demo dataset - SIMULATED DATA, not from any real satellite or IGS analysis centre.",
                "",
                "Contents: dual-frequency GNSS code (1 m noise) and carrier phase (2 mm noise) of a simulated 790 km",
                "sun-synchronous satellite (30 s,",
                "14 January 22:00 to 15 January 08:00 GPS time), one synthetic 24-satellite GPS constellation",
                "written as IGS-style Final/Rapid (SP3 + CLK, 15 January), Ultra-rapid (SP3 with clocks, observed 14th,",
                "predicted 15th) and RINEX 3 broadcast navigation, plus a reference orbit in POEORB layout.",
                "",
                "Try: cutoff " + CUTOFF_UTC + " UTC, Auto-select, Run with default settings.",
                "Forecast mode: Final (nominal issue 3 February) and Rapid (16 January 17:00) are excluded because",
                "they are published after the cutoff; the Ultra-rapid (issued 15 January 03:00) is used, from its",
                "predicted segment, with broadcast as fallback. Reconstruction mode uses the Final product.",
                "",
                "The satellite truth was generated with the GUI's default force model and NO optional data (zero",
                "Earth orientation, constant space weather F10.7=150 / Ap=15, DE440 Sun/Moon), so a default run",
                "recovers it to the noise level. If Auto-select also adds real EOP, space-weather or DE440 files from",
                "your catalog, the forecast model no longer matches the simulation and drifts by tens of metres per",
                "day (mostly drag): a useful illustration of model sensitivity. Remove those files to see the match.",
                "This demonstrates consistency, not real-world accuracy.", ""));
        out.add(readme);
        pl.stage("Demo dataset ready", 1.0);
        return out;
    }

    /** "YYYYDDD" of a GPS-time date, for IGS / RINEX long names. */
    private static String doy(final AbsoluteDate d) {
        final double[] c = d.getComponents(TimeScalesFactory.getGPS());
        final int doy = java.time.LocalDate.of((int) c[0], (int) c[1], (int) c[2]).getDayOfYear();
        return String.format(Locale.US, "%04d%03d", (int) c[0], doy);
    }

    private static String hdr(final String content, final String label) {
        return String.format(Locale.US, "%-60s%s%n", content, label);
    }

    private static Path sp3(final Path f, final AbsoluteDate start, final AbsoluteDate ref, final int n,
                            final boolean noClocks) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(f)) {
            final double[] c = start.getComponents(TimeScalesFactory.getGPS());
            w.write(String.format(Locale.US, "#dP%4d %2d %2d %2d %2d %11.8f %7d ORBIT IGS20 FIT  SIM%n",
                    (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], c[5], n));
            final double sinceEpoch = start.durationFrom(GPS_EPOCH);
            final double mjd = start.secondsSinceJ2000(TimeScalesFactory.getGPS()) / 86400.0 + 51544.5;
            w.write(String.format(Locale.US, "## %4d %15.8f %14.8f %5d %15.13f%n", (int) Math.floor(sinceEpoch / 604800.0),
                    sinceEpoch % 604800.0, 900.0, (int) Math.floor(mjd), mjd - Math.floor(mjd)));
            // SP3-d header: 5 satellite-id lines and 5 accuracy lines (17 slots each), 2 x %c/%f/%i, 4 comments
            final StringBuilder ids = new StringBuilder();
            for (int prn = 1; prn <= 24; prn++) {
                ids.append(String.format(Locale.US, "G%02d", prn));
            }
            while (ids.length() < 5 * 17 * 3) {
                ids.append("  0");
            }
            for (int line = 0; line < 5; line++) {
                w.write((line == 0 ? "+   24   " : "+        ") + ids.substring(line * 51, line * 51 + 51) + "\n");
            }
            for (int line = 0; line < 5; line++) {
                final StringBuilder acc = new StringBuilder("++       ");
                for (int k = 0; k < 17; k++) {
                    acc.append(line * 17 + k < 24 ? "  2" : "  0");
                }
                w.write(acc + "\n");
            }
            w.write("%c G  cc GPS ccc cccc cccc cccc cccc ccccc ccccc ccccc ccccc\n");
            w.write("%c cc cc ccc ccc cccc cccc cccc cccc ccccc ccccc ccccc ccccc\n");
            w.write("%f  1.2500000  1.025000000  0.00000000000  0.000000000000000\n");
            w.write("%f  0.0000000  0.000000000  0.00000000000  0.000000000000000\n");
            w.write("%i    0    0    0    0      0      0      0      0         0\n");
            w.write("%i    0    0    0    0      0      0      0      0         0\n");
            w.write("/* SIMULATED DEMO PRODUCT - synthetic GPS constellation, not an IGS product\n");
            w.write("/* written by the program's demo-dataset generator\n");
            w.write("/* positions in km, clocks in microseconds\n");
            w.write("/* do not use for real work\n");
            for (int k = 0; k < n; k++) {
                final AbsoluteDate t = start.shiftedBy(k * 900.0);
                final double[] e = t.getComponents(TimeScalesFactory.getGPS());
                w.write(String.format(Locale.US, "*  %4d %2d %2d %2d %2d %11.8f%n", (int) e[0], (int) e[1], (int) e[2],
                        (int) e[3], (int) e[4], e[5]));
                for (int prn = 1; prn <= 24; prn++) {
                    final Vector3D p = gps(prn, ref, 0.0).positionEcef(t);
                    w.write(String.format(Locale.US, "PG%02d%14.6f%14.6f%14.6f%14.6f%n", prn, p.getX() / 1000,
                            p.getY() / 1000, p.getZ() / 1000, noClocks ? 999999.999999 : poly(prn, ref, t) * 1e6));
                }
            }
            w.write("EOF\n");
        }
        return f;
    }

    private static Path clk(final Path f, final AbsoluteDate start, final AbsoluteDate ref, final int n,
                            final double step) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(f)) {
            w.write(hdr("     3.00           C                   G", "RINEX VERSION / TYPE"));
            w.write(hdr("DEMO                SIM                 20250115 000000 UTC", "PGM / RUN BY / DATE"));
            w.write(hdr("SIMULATED DEMO PRODUCT - not an IGS product", "COMMENT"));
            w.write(hdr("GPS", "TIME SYSTEM ID"));
            w.write(hdr("     1    AS", "# / TYPES OF DATA"));
            w.write(hdr("SIM  Simulated demo analysis centre", "ANALYSIS CENTER"));
            w.write(hdr("    24", "# OF SOLN SATS"));
            final StringBuilder prnList = new StringBuilder();
            for (int prn = 1; prn <= 24; prn++) {
                prnList.append(String.format(Locale.US, "G%02d ", prn));
                if (prn % 15 == 0 || prn == 24) {
                    w.write(hdr(prnList.toString(), "PRN LIST"));
                    prnList.setLength(0);
                }
            }
            w.write(hdr("", "END OF HEADER"));
            for (int k = 0; k < n; k++) {
                final AbsoluteDate t = start.shiftedBy(k * step);
                final double[] e = t.getComponents(TimeScalesFactory.getGPS());
                for (int prn = 1; prn <= 24; prn++) {
                    w.write(String.format(Locale.US, "AS G%02d       %4d %02d %02d %02d %02d %9.6f  1   %19.12E%n", prn,
                            (int) e[0], (int) e[1], (int) e[2], (int) e[3], (int) e[4], e[5], poly(prn, ref, t)));
                }
            }
        }
        return f;
    }

    /** RINEX 3.04 GPS navigation: one message per satellite every 2 h from -24 h to +24 h. */
    private static Path nav(final Path f, final AbsoluteDate ref) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(f)) {
            w.write(hdr("     3.04           N: GNSS NAV DATA    G: GPS", "RINEX VERSION / TYPE"));
            w.write(hdr("DEMO                SIM                 20250115 000000 UTC", "PGM / RUN BY / DATE"));
            w.write(hdr("SIMULATED DEMO NAVIGATION - synthetic constellation", "COMMENT"));
            w.write(hdr("    18", "LEAP SECONDS"));
            w.write(hdr("", "END OF HEADER"));
            for (double dt = -86400.0; dt <= 86400.0; dt += 7200.0) {
                for (int prn = 1; prn <= 24; prn++) {
                    final int plane = (prn - 1) / 4;
                    final int slot = (prn - 1) % 4;
                    final double sqrtA = 5153.6 + 0.3 * slot;
                    final double a = sqrtA * sqrtA;
                    final double n0 = Math.sqrt(GpsConstants.GM / (a * a * a)) + 4.5e-9;
                    final double af1 = 1.0e-12 * ((prn * 3) % 5 - 2);
                    final double af0 = 1.0e-5 * ((prn * 7) % 11 - 5) + af1 * dt;
                    final AbsoluteDate t = ref.shiftedBy(dt);
                    final double sow = t.durationFrom(GPS_EPOCH) % (7 * 86400.0);
                    final double week = Math.floor(t.durationFrom(GPS_EPOCH) / (7 * 86400.0));
                    final double[] c = t.getComponents(TimeScalesFactory.getGPS());
                    w.write(String.format(Locale.US, "G%02d %4d %02d %02d %02d %02d %02d%19.12E%19.12E%19.12E%n", prn,
                            (int) c[0], (int) c[1], (int) c[2], (int) c[3], (int) c[4], (int) Math.round(c[5]), af0, af1, 0.0));
                    final double[][] o = {
                        {1, 25.0, 4.5e-9, slot * Math.PI / 2 + plane * 0.31 + n0 * dt},
                        {1.0e-6, 0.004 + 0.001 * slot, 4.0e-6, sqrtA},
                        {sow, 1.0e-7, plane * Math.PI / 3 - 8.0e-9 * dt, -6.0e-8},
                        {0.958 + 2.0e-10 * dt, 220.0, 0.3 * plane, -8.0e-9},
                        {2.0e-10, 1, week, 0},
                        {2.0, 0, 0, 1},
                        {sow - 1800, 4, 0, 0}};
                    for (final double[] row : o) {
                        w.write(String.format(Locale.US, "    %19.12E%19.12E%19.12E%19.12E%n", row[0], row[1], row[2], row[3]));
                    }
                }
            }
        }
        return f;
    }
}
