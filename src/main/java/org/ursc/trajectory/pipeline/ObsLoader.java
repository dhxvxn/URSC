package org.ursc.trajectory.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.orekit.data.DataSource;
import org.orekit.files.rinex.observation.ObservationData;
import org.orekit.files.rinex.observation.ObservationDataSet;
import org.orekit.files.rinex.observation.RinexObservation;
import org.orekit.files.rinex.observation.RinexObservationHeader;
import org.orekit.files.rinex.observation.RinexObservationParser;
import org.orekit.gnss.SatelliteSystem;
import org.ursc.trajectory.gnss.RinexObsReader;
import org.ursc.trajectory.orekit.OrekitDates;
import org.ursc.trajectory.orekit.OrekitSession;
import org.ursc.trajectory.io.ProductInput;
import org.ursc.trajectory.io.TarGz;

/** Loads GNSSRD archives / RINEX observation files and merges their epochs in time order. */
public final class ObsLoader {

    /** Merged observations plus the spacecraft identity inferred from file names. */
    public static final class Loaded {
        public final List<RinexObsReader.Epoch> epochs = new ArrayList<>();
        public String objectName = "";
        public String objectId = "";
        /** GPS antenna offset from the observation header (body frame, m), or null if absent. */
        public double[] antennaDelta;
        public final List<String> problems = new ArrayList<>();
    }

    private ObsLoader() {
    }

    public static Loaded load(final List<Path> files, final double samplingSeconds) {
        return load(files, samplingSeconds, null, null);
    }

    /**
     * Load and merge observation files, keeping only epochs in {@code [from, to]} (either bound
     * may be null). Filtering per file keeps memory bounded when many days are read at a high rate.
     */
    public static Loaded load(final List<Path> files, final double samplingSeconds,
                              final org.ursc.trajectory.time.AbsoluteDate from,
                              final org.ursc.trajectory.time.AbsoluteDate to) {
        final Loaded out = new Loaded();
        final Map<Double, RinexObsReader.Epoch> byTime = new TreeMap<>();
        for (final Path f : files) {
            final String n = f.getFileName().toString();
            try {
                final RinexObsReader.Data d;
                if (n.endsWith(".tgz") || n.endsWith(".tar.gz") || n.endsWith(".TGZ")) {
                    try (InputStream in = TarGz.openEntry(f, ".DBL")) {
                        d = RinexObsReader.read(in, samplingSeconds);
                    }
                } else {
                    try (InputStream in = ProductInput.open(f)) {
                        d = RinexObsReader.read(in, samplingSeconds);
                    }
                }
                if (out.antennaDelta == null) {
                    out.antennaDelta = d.getAntennaDelta();
                }
                for (final RinexObsReader.Epoch e : d.getEpochs()) {
                    if (from != null && e.getDate().isBefore(from) || to != null && e.getDate().isAfter(to)) {
                        continue;
                    }
                    byTime.putIfAbsent(e.getDate().durationFromJ2000(), e); // first file wins on overlap
                }
                if (out.objectName.isEmpty()) {
                    identify(n, out);
                    if (!n.toUpperCase(Locale.US).matches("^S\\d[A-Z]_.*")) {
                        final String marker = markerName(f);
                        if (!marker.isEmpty()) {
                            out.objectName = marker;
                        }
                    }
                }
            } catch (final IOException | RuntimeException e) {
                out.problems.add(n + ": unreadable GNSS observation file (" + e.getMessage() + ")");
            }
        }
        out.epochs.addAll(byTime.values());
        out.epochs.sort(Comparator.comparingDouble(e -> e.getDate().durationFromJ2000()));
        return out;
    }

    /**
     * Load GNSS observation files with Orekit's {@link RinexObservationParser}, keeping GPS epochs
     * in {@code [from, to]} whose GPS second of day is a multiple of {@code rateSeconds}. The
     * epochs are thinned on the text before Orekit parses them, so a day of 1 Hz data costs a
     * tenth of the memory at 10 s. The antenna offset is read from the header (ANTENNA: DELTA
     * H/E/N or X/Y/Z, both stored in the spacecraft body frame for space-borne receivers).
     */
    public static Loaded loadWithOrekit(final List<Path> files, final double rateSeconds,
                                        final org.ursc.trajectory.time.AbsoluteDate from,
                                        final org.ursc.trajectory.time.AbsoluteDate to, final OrekitSession session) {
        final Loaded out = new Loaded();
        final Map<Double, RinexObsReader.Epoch> byTime = new TreeMap<>();
        final org.orekit.time.AbsoluteDate oFrom = from == null ? null : OrekitDates.toOrekit(from);
        final org.orekit.time.AbsoluteDate oTo = to == null ? null : OrekitDates.toOrekit(to);
        for (final Path f : files) {
            final String n = f.getFileName().toString();
            try {
                final String text;
                if (n.endsWith(".tgz") || n.endsWith(".tar.gz") || n.endsWith(".TGZ")) {
                    try (InputStream in = TarGz.openEntry(f, ".DBL")) {
                        text = thin(in, rateSeconds, oFrom, oTo, session);
                    }
                } else {
                    try (InputStream in = ProductInput.open(f)) {
                        text = thin(in, rateSeconds, oFrom, oTo, session);
                    }
                }
                final RinexObservation ro = new RinexObservationParser().parse(
                        new DataSource(n, () -> new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
                final RinexObservationHeader h = ro.getHeader();
                if (out.antennaDelta == null) {
                    if (h.getAntennaReferencePoint() != null) {
                        final org.hipparchus.geometry.euclidean.threed.Vector3D a = h.getAntennaReferencePoint();
                        out.antennaDelta = new double[] {a.getX(), a.getY(), a.getZ()};
                    } else if (h.getEccentricities() != null) {
                        out.antennaDelta = new double[] {h.getAntennaHeight(), h.getEccentricities().getX(),
                            h.getEccentricities().getY()};
                    }
                }
                convert(ro.getObservationDataSets(), byTime);
                if (out.objectName.isEmpty()) {
                    identify(n, out);
                    if (!n.toUpperCase(Locale.US).matches("^S\\d[A-Z]_.*") && h.getMarkerName() != null
                            && !h.getMarkerName().isBlank()) {
                        out.objectName = h.getMarkerName().trim();
                    }
                }
            } catch (final IOException | RuntimeException e) {
                out.problems.add(n + ": unreadable GNSS observation file (" + e.getMessage() + ")");
            }
        }
        out.epochs.addAll(byTime.values());
        return out;
    }

    /** Keep the header and the epochs on the rate grid inside the window. */
    private static String thin(final InputStream in, final double rate, final org.orekit.time.AbsoluteDate from,
                               final org.orekit.time.AbsoluteDate to, final OrekitSession session) throws IOException {
        final java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.US_ASCII), 1 << 16);
        final StringBuilder sb = new StringBuilder(1 << 22);
        String l;
        boolean header = true;
        boolean keep = false;
        while ((l = br.readLine()) != null) {
            if (header) {
                sb.append(l).append('\n');
                header = !l.contains("END OF HEADER");
                continue;
            }
            if (l.startsWith(">")) {
                keep = false;
                try {
                    final int hh = Integer.parseInt(l.substring(13, 15).trim());
                    final int mm = Integer.parseInt(l.substring(16, 18).trim());
                    final double ss = Double.parseDouble(l.substring(18, 29).trim());
                    final double sod = hh * 3600.0 + mm * 60.0 + ss;
                    final double k = Math.rint(sod / rate);
                    if (rate <= 0 || Math.abs(sod - k * rate) < 1.0e-3) {
                        final org.orekit.time.AbsoluteDate d = new org.orekit.time.AbsoluteDate(
                                Integer.parseInt(l.substring(2, 6).trim()), Integer.parseInt(l.substring(7, 9).trim()),
                                Integer.parseInt(l.substring(10, 12).trim()), hh, mm, ss, session.gps);
                        keep = (from == null || d.durationFrom(from) > -1.0e-3) && (to == null || to.durationFrom(d) > -1.0e-3);
                    }
                } catch (final RuntimeException e) {
                    keep = false;
                }
            }
            if (keep) {
                sb.append(l).append('\n');
            }
        }
        return sb.toString();
    }

    /** Orekit observation sets (one per satellite and epoch) to the program's epoch records. */
    private static void convert(final List<ObservationDataSet> sets, final Map<Double, RinexObsReader.Epoch> byTime) {
        int i = 0;
        while (i < sets.size()) {
            final org.orekit.time.AbsoluteDate date = sets.get(i).getDate();
            final List<RinexObsReader.SatRecord> sats = new ArrayList<>();
            final double clk = sets.get(i).getRcvrClkOffset();
            while (i < sets.size() && sets.get(i).getDate().equals(date)) {
                final ObservationDataSet ods = sets.get(i++);
                if (ods.getSatellite().getSystem() != SatelliteSystem.GPS) {
                    continue;
                }
                final Map<String, Integer> idx = new java.util.LinkedHashMap<>();
                final List<ObservationData> data = ods.getObservationData();
                final double[] v = new double[data.size()];
                final int[] lli = new int[data.size()];
                for (int k = 0; k < data.size(); k++) {
                    idx.put(data.get(k).getObservationType().getName(), k);
                    v[k] = data.get(k).getValue();
                    lli[k] = data.get(k).getLossOfLockIndicator();
                }
                sats.add(new RinexObsReader.SatRecord(ods.getSatellite().getPRN(), idx, v, lli));
            }
            final org.ursc.trajectory.time.AbsoluteDate d = OrekitDates.fromOrekit(date);
            byTime.putIfAbsent(d.durationFromJ2000(), new RinexObsReader.Epoch(d, Double.isNaN(clk) ? 0.0 : clk, sats));
        }
    }

    /**
     * Spacecraft a GNSS observation file belongs to: the Sentinel mission code (S3A, S3B...) from
     * the product name, else the RINEX MARKER NAME, else the file name.
     */
    public static String spacecraftOf(final Path f) {
        final String n = f.getFileName().toString();
        final String u = n.toUpperCase(Locale.US);
        if (u.matches("^S\\d[A-Z]_.*")) {
            return u.substring(0, 3);
        }
        final String m = markerName(f);
        return m.isEmpty() ? n : m;
    }

    /** Distinct spacecraft among observation files (sorted). */
    public static List<String> spacecraft(final List<Path> files) {
        final java.util.TreeSet<String> s = new java.util.TreeSet<>();
        for (final Path f : files) {
            s.add(spacecraftOf(f));
        }
        return new ArrayList<>(s);
    }

    /** RINEX "MARKER NAME" header value (plain or compressed files), or "" if absent. */
    public static String markerName(final Path f) {
        try (InputStream in = ProductInput.open(f);
             java.io.BufferedReader br = new java.io.BufferedReader(
                     new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.US_ASCII))) {
            String l;
            for (int i = 0; i < 60 && (l = br.readLine()) != null; i++) {
                if (l.length() > 60 && "MARKER NAME".equals(l.substring(60).trim())) {
                    return l.substring(0, 60).trim();
                }
                if (l.contains("END OF HEADER")) {
                    break;
                }
            }
        } catch (final IOException | RuntimeException ignored) {
            // fall back to the file-name guess
        }
        return "";
    }

    /** Sentinel product names start with the mission code (S3A_, S3B_, S6A_...). */
    static void identify(final String fileName, final Loaded out) {
        final String u = fileName.toUpperCase(Locale.US);
        if (u.startsWith("S3A_")) {
            out.objectName = "SENTINEL-3A";
            out.objectId = "2016-011A";
        } else if (u.startsWith("S3B_")) {
            out.objectName = "SENTINEL-3B";
            out.objectId = "2018-039A";
        } else if (u.startsWith("S6A_")) {
            out.objectName = "SENTINEL-6A";
            out.objectId = "2020-086A";
        } else {
            final int us = u.indexOf('_');
            out.objectName = us > 0 ? u.substring(0, us) : "SPACECRAFT";
        }
    }
}
