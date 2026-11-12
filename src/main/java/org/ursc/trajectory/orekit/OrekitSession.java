package org.ursc.trajectory.orekit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.SortedSet;

import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.data.LazyLoadedDataContext;
import org.orekit.forces.gravity.potential.ICGEMFormatReader;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.frames.EOPEntry;
import org.orekit.frames.Frame;
import org.orekit.frames.ITRFVersion;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeScale;
import org.orekit.time.UT1Scale;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.ursc.trajectory.frames.EopProvider;
import org.ursc.trajectory.time.UTCScale;

/**
 * One run's Orekit data context. Orekit reads its physical data (leap seconds, Earth orientation,
 * gravity field, planetary ephemeris) through a {@link DataContext}; every run gets a fresh one
 * so that the cutoff discipline of that run (EOP held at the cutoff, no data from the future)
 * cannot leak into the next run through Orekit's caches.
 *
 * <ul>
 *   <li>leap seconds: written from the program's own table ({@link UTCScale#leapSecondTable()}),
 *       so Orekit and the rest of the program share exactly one UTC;</li>
 *   <li>Earth orientation: sampled daily from the run's (cutoff-safe) {@link EopProvider};</li>
 *   <li>gravity: the selected ICGEM file, else the bundled EGM96 (degree/order 70);</li>
 *   <li>Sun and Moon: the JPL DE440 file (required).</li>
 * </ul>
 *
 * <p>The session installs itself as Orekit's default data context (Orekit force models and
 * parsers consult the default) and restores the previous one on {@link #close()}; runs must
 * therefore execute one at a time, which the GUI and CLI already guarantee.</p>
 */
public final class OrekitSession implements AutoCloseable {

    private static final Object LOCK = new Object();
    private static final String[] MONTHS = {"Jan.", "Feb.", "Mar.", "Apr.", "May", "Jun.", "Jul.", "Aug.",
        "Sep.", "Oct.", "Nov.", "Dec."};

    public final LazyLoadedDataContext context;
    public final TimeScale utc;
    public final TimeScale gps;
    public final UT1Scale ut1;
    public final Frame gcrf;
    public final Frame eme2000;
    public final Frame itrf;
    public final OneAxisEllipsoid earth;
    public final CelestialBody sun;
    public final CelestialBody moon;
    private final LazyLoadedDataContext previous;
    private final Path dir;

    /**
     * @param eop         Earth orientation (already cutoff-safe), or null for none
     * @param eopFrom     first day of EOP to load (our date type)
     * @param eopTo       last day of EOP to load
     * @param gravityFile ICGEM gravity file, or null for the bundled EGM96
     * @param de440File   JPL DE440 binary ephemeris (lnxp1990.440)
     */
    public static OrekitSession open(final EopProvider eop, final org.ursc.trajectory.time.AbsoluteDate eopFrom,
                                     final org.ursc.trajectory.time.AbsoluteDate eopTo, final Path gravityFile,
                                     final Path de440File) throws IOException {
        synchronized (LOCK) {
            return new OrekitSession(eop, eopFrom, eopTo, gravityFile, de440File);
        }
    }

    private OrekitSession(final EopProvider eop, final org.ursc.trajectory.time.AbsoluteDate eopFrom,
                          final org.ursc.trajectory.time.AbsoluteDate eopTo, final Path gravityFile,
                          final Path de440File) throws IOException {
        dir = Files.createTempDirectory("orekit-data");
        writeLeapSeconds(dir.resolve("UTC-TAI.history"));
        final String gravityName;
        if (gravityFile != null) {
            gravityName = gravityFile.getFileName().toString();
            Files.copy(gravityFile, dir.resolve(gravityName), StandardCopyOption.REPLACE_EXISTING);
        } else {
            gravityName = "egm96s_to70.gfc.gz";
            try (InputStream in = OrekitSession.class.getResourceAsStream("/gravity/egm96s_to70.gfc.gz")) {
                if (in == null) {
                    throw new IOException("bundled EGM96 gravity field missing from the program");
                }
                Files.copy(in, dir.resolve(gravityName));
            }
        }
        if (de440File == null || !Files.isRegularFile(de440File)) {
            throw new IOException("JPL DE440 ephemeris (lnxp1990.440) not found"
                    + (de440File == null ? "" : ": " + de440File) + "; it is required for the Sun and Moon");
        }
        try {
            Files.createSymbolicLink(dir.resolve("lnxp1990.440"), de440File.toAbsolutePath());
        } catch (final IOException | UnsupportedOperationException e) {
            Files.copy(de440File, dir.resolve("lnxp1990.440"));
        }

        // FES2004 ocean-tide coefficients (8 dominant constituents, degree 4), IERS table layout
        try (InputStream in = OrekitSession.class.getResourceAsStream("/gravity/fes2004_8const.dat")) {
            if (in != null) {
                final StringBuilder fes = new StringBuilder();
                for (final String l : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                    if (!l.startsWith("#") && !l.isBlank()) {
                        fes.append(l).append('\n');
                    }
                }
                Files.writeString(dir.resolve("fes2004_Cnm-Snm.dat"), fes.toString());
            }
        }

        context = new LazyLoadedDataContext();
        context.getDataProvidersManager().addProvider(new DirectoryCrawler(dir.toFile()));
        context.getGravityFields().clearPotentialCoefficientsReaders();
        context.getGravityFields().addPotentialCoefficientsReader(
                new ICGEMFormatReader(java.util.regex.Pattern.quote(gravityName.replaceAll("\\.gz$", "")), false));
        context.getGravityFields().clearOceanTidesReaders();
        context.getGravityFields().addOceanTidesReader(
                new org.orekit.forces.gravity.potential.FESCnmSnmReader("^fes2004_Cnm-Snm\\.dat$", 1.0e-11));
        context.getFrames().clearEOPHistoryLoaders();
        if (eop != null) {
            context.getFrames().addEOPHistoryLoader(IERSConventions.IERS_2010,
                    (converter, history) -> fill(history, eop, eopFrom, eopTo));
        } else {
            context.getFrames().addEOPHistoryLoader(IERSConventions.IERS_2010, (converter, history) -> { });
        }
        previous = DataContext.getDefault();
        DataContext.setDefault(context);

        utc = context.getTimeScales().getUTC();
        gps = context.getTimeScales().getGPS();
        ut1 = context.getTimeScales().getUT1(IERSConventions.IERS_2010, true);
        gcrf = context.getFrames().getGCRF();
        eme2000 = context.getFrames().getEME2000();
        itrf = context.getFrames().getITRF(IERSConventions.IERS_2010, true);
        earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS, Constants.WGS84_EARTH_FLATTENING, itrf);
        sun = context.getCelestialBodies().getSun();
        moon = context.getCelestialBodies().getMoon();
    }

    /** Normalised gravity field truncated to {@code degree} x {@code order}. */
    public NormalizedSphericalHarmonicsProvider gravity(final int degree, final int order) {
        return context.getGravityFields().getNormalizedProvider(degree, order);
    }

    /** Daily EOP entries from the program's provider (UT1-UTC, polar motion, LOD from UT1 rate). */
    private void fill(final SortedSet<EOPEntry> history, final EopProvider eop,
                      final org.ursc.trajectory.time.AbsoluteDate from, final org.ursc.trajectory.time.AbsoluteDate to) {
        final TimeScale u = context.getTimeScales().getUTC();
        final AbsoluteDate start = OrekitDates.toOrekit(from);
        final int mjd0 = start.getComponents(u).getDate().getMJD() - 2;
        final int mjd1 = OrekitDates.toOrekit(to).getComponents(u).getDate().getMJD() + 2;
        for (int mjd = mjd0; mjd <= mjd1; mjd++) {
            final AbsoluteDate d = new AbsoluteDate(new DateComponents(DateComponents.MODIFIED_JULIAN_EPOCH, mjd), u);
            final AbsoluteDate next = new AbsoluteDate(new DateComponents(DateComponents.MODIFIED_JULIAN_EPOCH, mjd + 1), u);
            final org.ursc.trajectory.time.AbsoluteDate dd = OrekitDates.fromOrekit(d);
            final double dut1 = eop.getUT1MinusUTC(dd);
            double lod = -(eop.getUT1MinusUTC(OrekitDates.fromOrekit(next)) - dut1);
            if (Math.abs(lod) > 0.5) {
                lod = 0.0; // leap second inside the day
            }
            history.add(new EOPEntry(mjd, dut1, lod, eop.getXp(dd), eop.getYp(dd), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    ITRFVersion.ITRF_2020, d));
        }
    }

    /** Write the leap-second table in the IERS {@code UTC-TAI.history} format Orekit reads. */
    private static void writeLeapSeconds(final Path file) throws IOException {
        final int[][] t = UTCScale.leapSecondTable();
        final StringBuilder sb = new StringBuilder();
        sb.append(" ---------------------------------------------------------------------------\n");
        sb.append(" Leap seconds written by the program from its own table\n");
        sb.append(" ---------------------------------------------------------------------------\n");
        for (int i = 0; i < t.length; i++) {
            final String start = String.format(Locale.US, " %4d  %-4s %2d", t[i][0], MONTHS[t[i][1] - 1], t[i][2]);
            final String end = i + 1 < t.length
                    ? String.format(Locale.US, "%4d  %-4s %2d", t[i + 1][0], MONTHS[t[i + 1][1] - 1], t[i + 1][2])
                    : "              ";
            sb.append(start).append(" - ").append(end).append(String.format(Locale.US, "    %2ds%n", t[i][3]));
        }
        Files.writeString(file, sb.toString());
    }

    @Override
    public void close() {
        synchronized (LOCK) {
            if (DataContext.getDefault() == context) {
                DataContext.setDefault(previous);
            }
            try (var s = Files.walk(dir)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (final IOException ignored) {
                // best effort
            }
        }
    }
}
