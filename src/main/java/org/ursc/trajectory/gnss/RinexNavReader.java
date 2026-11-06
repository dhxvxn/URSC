package org.ursc.trajectory.gnss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Reader for RINEX 3.x and RINEX 4.00–4.02 GPS LNAV navigation (broadcast-ephemeris) files, producing
 * {@link GpsBroadcastEphemeris} messages grouped per satellite.
 *
 * <p>Each GPS record is the standard eight-line block (satellite/epoch/clock line
 * plus seven broadcast-orbit lines). Other constellations present in a mixed file
 * are skipped. Numeric fields use the Fortran {@code D} exponent format and the
 * fixed 19-character columns of the RINEX 3 navigation layout.</p>
 */
public final class RinexNavReader {

    /** GPS time origin: 1980-01-06 00:00:00 GPS. */
    private static final AbsoluteDate GPS_EPOCH =
            new AbsoluteDate(1980, 1, 6, 0, 0, 0.0, TimeScalesFactory.getGPS());
    private static final double WEEK = 604800.0;
    private static final double HALF_WEEK = 302400.0;

    private RinexNavReader() {
    }

    /** Parsed navigation data: broadcast messages per PRN, time-ordered by Toe. */
    public static final class Data {
        private final Map<Integer, List<GpsBroadcastEphemeris>> byPrn;

        Data(final Map<Integer, List<GpsBroadcastEphemeris>> byPrn) {
            this.byPrn = byPrn;
        }

        /** @return the PRNs with at least one ephemeris message. */
        public java.util.Set<Integer> prns() {
            return byPrn.keySet();
        }

        public int messageCount() {
            int n = 0;
            for (final List<GpsBroadcastEphemeris> l : byPrn.values()) {
                n += l.size();
            }
            return n;
        }

        /**
         * Select the broadcast message for a satellite closest in time to {@code t}.
         *
         * @return the nearest-Toe ephemeris, or {@code null} if the PRN is absent
         */
        public GpsBroadcastEphemeris ephemerisFor(final int prn, final AbsoluteDate t) {
            final List<GpsBroadcastEphemeris> list = byPrn.get(prn);
            if (list == null || list.isEmpty()) {
                return null;
            }
            GpsBroadcastEphemeris best = null;
            double bestDt = Double.MAX_VALUE;
            for (final GpsBroadcastEphemeris eph : list) {
                final double dt = Math.abs(t.durationFrom(eph.getToe()));
                if (dt < bestDt) {
                    bestDt = dt;
                    best = eph;
                }
            }
            return best;
        }
    }

    /** Read a navigation file, transparently decompressing a {@code .gz} by extension. */
    public static Data read(final Path path) throws IOException {
        InputStream in = Files.newInputStream(path);
        if (path.getFileName().toString().endsWith(".gz")) {
            in = new GZIPInputStream(in);
        }
        try (InputStream stream = in) {
            return read(stream);
        }
    }

    /** Read GPS LNAV from RINEX 3.x or record-delimited RINEX 4.00–4.02 navigation.
     * Unsupported RINEX 4 record families (including GPS CNAV/CNV2) are skipped by
     * their record boundaries, never interpreted using the LNAV field layout. */
    public static Data read(final InputStream stream) throws IOException {
        final BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        final Map<Integer, List<GpsBroadcastEphemeris>> byPrn = new TreeMap<>();
        String line = br.readLine();
        if (line == null || line.length() < 21 || line.charAt(20) != 'N')
            throw new IOException("Expected a RINEX navigation header");
        final double version;
        try { version = Double.parseDouble(line.substring(0, 9).trim()); }
        catch (RuntimeException e) { throw new IOException("Invalid RINEX version", e); }
        if (!Double.isFinite(version) || version < 3 || version > 4.02)
            throw new IOException("Supported navigation versions: RINEX 3.x and 4.00–4.02; got " + version);
        boolean endHeader = false;
        while ((line = br.readLine()) != null) {
            if (line.length() >= 61 && "END OF HEADER".equals(line.substring(60).trim())) {
                endHeader = true; break;
            }
        }
        if (!endHeader) throw new IOException("Missing END OF HEADER");
        if (version < 4) {
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("G")) continue;
                List<String> block = new ArrayList<>(); block.add(line);
                for (int i = 0; i < 7; i++) {
                    String next = br.readLine();
                    if (next == null || next.isEmpty() || !Character.isWhitespace(next.charAt(0)))
                        throw new IOException("Truncated GPS navigation record: " + line);
                    block.add(next);
                }
                addGps(byPrn, block);
            }
        } else {
            String marker = null;
            List<String> block = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                if (line.startsWith(">")) {
                    readV4Block(byPrn, marker, block);
                    marker = line; block = new ArrayList<>();
                } else if (!line.isBlank()) {
                    if (marker == null) throw new IOException("Missing RINEX 4 record marker");
                    // Unsupported records can be long. Store only a GPS LNAV payload.
                    if (isGpsLnav(marker)) {
                        if (block.size() >= 8) throw new IOException("Oversized GPS LNAV record: " + marker);
                        block.add(line);
                    }
                }
            }
            readV4Block(byPrn, marker, block);
        }
        if (byPrn.isEmpty()) throw new IOException("No supported GPS LNAV messages found in navigation file");
        for (List<GpsBroadcastEphemeris> messages : byPrn.values())
            messages.sort(java.util.Comparator.comparingDouble(e -> e.getToe().durationFrom(GPS_EPOCH)));
        return new Data(byPrn);
    }

    private static boolean isGpsLnav(String marker) {
        String[] t = marker.trim().split("\\s+");
        return t.length == 4 && t[0].equals(">") && t[1].equals("EPH")
                && t[2].matches("G[0-9]{2}") && t[3].equals("LNAV");
    }

    private static void readV4Block(Map<Integer,List<GpsBroadcastEphemeris>> out,
                                    String marker, List<String> block) throws IOException {
        if (marker == null || !isGpsLnav(marker)) return;
        String prn = marker.trim().split("\\s+")[2];
        if (block.size() != 8 || !block.get(0).startsWith(prn + " "))
            throw new IOException("Invalid or truncated GPS LNAV payload: " + marker);
        addGps(out, block);
    }

    private static void addGps(Map<Integer,List<GpsBroadcastEphemeris>> out,
                                List<String> block) throws IOException {
        try {
            GpsBroadcastEphemeris e = parseGps(block);
            out.computeIfAbsent(e.getPrn(), k -> new ArrayList<>()).add(e);
        } catch (RuntimeException e) {
            throw new IOException("Malformed GPS LNAV record: " + block.get(0), e);
        }
    }

    private static GpsBroadcastEphemeris parseGps(List<String> block) {
        final String line = block.get(0);
        final int prn = Integer.parseInt(line.substring(1, 3).trim());
        final int year = Integer.parseInt(line.substring(4, 8).trim());
        final int month = Integer.parseInt(line.substring(9, 11).trim());
        final int day = Integer.parseInt(line.substring(12, 14).trim());
        final int hour = Integer.parseInt(line.substring(15, 17).trim());
        final int minute = Integer.parseInt(line.substring(18, 20).trim());
        final int second = Integer.parseInt(line.substring(21, 23).trim());
        final AbsoluteDate toc =
                new AbsoluteDate(year, month, day, hour, minute, second,
                        TimeScalesFactory.getGPS());
        final double af0 = d(line, 23);
        final double af1 = d(line, 42);
        final double af2 = d(line, 61);

        final double[] o = new double[28]; // broadcast-orbit values, lines 1..7 x 4
        for (int li = 0; li < 7; li++) {
            final String orbit = block.get(li + 1);
            for (int f = 0; f < 4; f++) {
                o[li * 4 + f] = d(orbit, 4 + 19 * f);
            }
        }
        if (prn < 1 || prn > 32 || !Double.isFinite(af0) || !Double.isFinite(af1) || !Double.isFinite(af2))
            throw new IllegalArgumentException("Invalid GPS clock or satellite identifier");
        for (int i = 0; i <= 16; i++) if (!Double.isFinite(o[i]))
            throw new IllegalArgumentException("Missing/nonfinite GPS orbit field " + i);
        if (!(o[7] > 0) || o[5] < 0 || o[5] >= 1 || o[8] < 0 || o[8] >= WEEK)
            throw new IllegalArgumentException("Invalid GPS orbital parameters");
        // IS-GPS-200 field order
        final double crs = o[1];
        final double deltaN = o[2];
        final double m0 = o[3];
        final double cuc = o[4];
        final double e = o[5];
        final double cus = o[6];
        final double sqrtA = o[7];
        final double toeSow = o[8];
        final double cic = o[9];
        final double omega0 = o[10];
        final double cis = o[11];
        final double i0 = o[12];
        final double crc = o[13];
        final double omega = o[14];
        final double omegaDot = o[15];
        final double iDot = o[16];

        final AbsoluteDate toe = toeFromToc(toc, toeSow);
        final GpsBroadcastEphemeris eph = new GpsBroadcastEphemeris(prn, toc, toe, toeSow,
                af0, af1, af2, sqrtA, e, m0, deltaN, omega0, i0, omega, omegaDot, iDot,
                cuc, cus, crc, crs, cic, cis);
        return eph;
    }


    /** Build the absolute Toe epoch from the clock epoch's GPS week and the Toe seconds-of-week. */
    private static AbsoluteDate toeFromToc(final AbsoluteDate toc, final double toeSow) {
        final double secSinceEpoch = toc.durationFrom(GPS_EPOCH);
        long week = (long) Math.floor(secSinceEpoch / WEEK);
        final double tocSow = secSinceEpoch - week * WEEK;
        // handle the rare case where Toe and Toc straddle a week boundary
        if (toeSow - tocSow > HALF_WEEK) {
            week -= 1;
        } else if (toeSow - tocSow < -HALF_WEEK) {
            week += 1;
        }
        return GPS_EPOCH.shiftedBy(week * WEEK + toeSow);
    }

    /** Parse a 19-char Fortran D-exponent field at {@code start}; NaN if blank/short. */
    private static double d(final String line, final int start) {
        if (line == null || start >= line.length()) {
            return Double.NaN;
        }
        final String field = line.substring(start, Math.min(start + 19, line.length())).trim();
        if (field.isEmpty()) {
            return Double.NaN;
        }
        return Double.parseDouble(field.replace('D', 'E').replace('d', 'e'));
    }
}
