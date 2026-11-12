package org.ursc.trajectory.gnss;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Reader for RINEX 3.0x GPS observation files (the Sentinel-3A spaceborne receiver
 * {@code GNSSRD} product is a GPS-only RINEX 3.03 observation file).
 *
 * <p>Parses the observation-type list from the header and, for each epoch, the
 * per-satellite observations (pseudorange, carrier phase, signal strength). Epoch
 * tags are read in GPS time ({@link TimeScalesFactory#getGPS()}). Only GPS (system
 * {@code G}) is parsed; other constellations in a mixed file are skipped.</p>
 *
 * <p>Observation records are fixed-width per RINEX 3: a 3-character satellite id,
 * then each observable in a 16-character field (a 14.3 value followed by optional
 * loss-of-lock and signal-strength indicators). A blank field is returned as
 * {@link Double#NaN}. High-rate files can be thinned at read time via a decimation
 * interval.</p>
 */
public final class RinexObsReader {

    private RinexObsReader() {
    }

    /** One satellite's observations at one epoch, keyed by RINEX observation code. */
    public static final class SatRecord {
        private final int prn;
        private final Map<String, Integer> typeIndex;
        private final double[] values;
        /** Loss-of-lock indicator per observation type (RINEX LLI digit), or null if none were parsed. */
        private final int[] lli;

        SatRecord(final int prn, final Map<String, Integer> typeIndex, final double[] values) {
            this(prn, typeIndex, values, null);
        }

        public SatRecord(final int prn, final Map<String, Integer> typeIndex, final double[] values, final int[] lli) {
            this.prn = prn;
            this.typeIndex = typeIndex;
            this.values = values;
            this.lli = lli;
        }

        /**
         * @return the RINEX loss-of-lock indicator for {@code code} (bit 0 = lost lock / possible
         *         cycle slip since the previous epoch), or 0 if absent.
         */
        public int lli(final String code) {
            final Integer i = typeIndex.get(code);
            return lli == null || i == null || i >= lli.length ? 0 : lli[i];
        }

        /** L1 carrier-phase observation code used for the combinations: L1W if present, else L1C. */
        public String phaseL1Code() {
            return Double.isNaN(obs("L1W")) ? "L1C" : "L1W";
        }

        /** L1 carrier phase in metres (cycles x wavelength), NaN if absent. */
        public double phaseL1Meters() {
            return obs(phaseL1Code()) * GpsConstants.SPEED_OF_LIGHT / GpsConstants.F_L1;
        }

        /** L2 (L2W) carrier phase in metres, NaN if absent. */
        public double phaseL2Meters() {
            return obs("L2W") * GpsConstants.SPEED_OF_LIGHT / GpsConstants.F_L2;
        }

        /**
         * Ionosphere-free carrier phase in metres (same combination as the code), NaN if either
         * frequency is missing. Contains an unknown constant (the ambiguity) per continuous arc.
         */
        public double ionoFreePhase() {
            final double f1sq = GpsConstants.F_L1 * GpsConstants.F_L1;
            final double f2sq = GpsConstants.F_L2 * GpsConstants.F_L2;
            return (f1sq * phaseL1Meters() - f2sq * phaseL2Meters()) / (f1sq - f2sq);
        }

        /** Geometry-free phase combination L1 - L2 (m): ionosphere + constant; jumps reveal cycle slips. */
        public double geometryFree() {
            return phaseL1Meters() - phaseL2Meters();
        }

        /**
         * Melbourne-Wubbena combination in wide-lane cycles: wide-lane phase minus narrow-lane code.
         * Free of geometry, clocks and first-order ionosphere, so it is constant within an arc apart
         * from code noise; a jump reveals a cycle slip.
         */
        public double melbourneWubbenaCycles() {
            double p1 = obs("C1W");
            if (Double.isNaN(p1)) {
                p1 = obs("C1C");
            }
            final double p2 = obs("C2W");
            final double f1 = GpsConstants.F_L1;
            final double f2 = GpsConstants.F_L2;
            final double wl = (f1 * phaseL1Meters() - f2 * phaseL2Meters()) / (f1 - f2);
            final double nl = (f1 * p1 + f2 * p2) / (f1 + f2);
            return (wl - nl) / (GpsConstants.SPEED_OF_LIGHT / (f1 - f2));
        }

        /** Loss of lock flagged on either carrier used in the combinations. */
        public boolean lossOfLock() {
            return (lli(phaseL1Code()) & 1) != 0 || (lli("L2W") & 1) != 0;
        }

        /** @return the GPS PRN (satellite number). */
        public int getPrn() {
            return prn;
        }

        /** @return the observation for the given RINEX code (e.g. "C1C"), or NaN if absent. */
        public double obs(final String code) {
            final Integer i = typeIndex.get(code);
            if (i == null || i >= values.length) {
                return Double.NaN;
            }
            return values[i];
        }

        /**
         * Ionosphere-free pseudorange from the L1 and L2 P-code observations
         * (C1W, C2W), falling back to C1C on L1 if C1W is absent.
         *
         * @return the ionosphere-free pseudorange (m), or NaN if either frequency is missing
         */
        public double ionoFreePseudorange() {
            double p1 = obs("C1W");
            if (Double.isNaN(p1)) {
                p1 = obs("C1C");
            }
            final double p2 = obs("C2W");
            return GpsConstants.ionoFreePseudorange(p1, p2);
        }
    }

    /** All GPS satellites observed at one epoch. */
    public static final class Epoch {
        private final AbsoluteDate date;
        private final double receiverClockOffset;
        private final List<SatRecord> satellites;

        public Epoch(final AbsoluteDate date, final double receiverClockOffset,
              final List<SatRecord> satellites) {
            this.date = date;
            this.receiverClockOffset = receiverClockOffset;
            this.satellites = satellites;
        }

        /** @return epoch tag, in GPS time. */
        /**
         * Build a synthetic epoch (simulation and tests): each entry is a PRN and its
         * ionosphere-free pseudorange in metres.
         */
        public static Epoch synthetic(final AbsoluteDate date, final java.util.Map<Integer, Double> ionoFree) {
            final java.util.Map<String, Integer> idx = new java.util.LinkedHashMap<>();
            idx.put("C1W", 0);
            idx.put("C2W", 1);
            final List<SatRecord> sats = new java.util.ArrayList<>();
            for (final java.util.Map.Entry<Integer, Double> e : ionoFree.entrySet()) {
                // iono-free(P, P) == P
                sats.add(new SatRecord(e.getKey(), idx, new double[] {e.getValue(), e.getValue()}));
            }
            return new Epoch(date, 0.0, sats);
        }

        /**
         * Build a synthetic dual-frequency epoch (simulation and tests): each entry maps a PRN to
         * {@code {C1W, L1W, C2W, L2W}} with codes in metres and phases in cycles, and an optional
         * per-PRN loss-of-lock flag.
         */
        public static Epoch syntheticDual(final AbsoluteDate date, final java.util.Map<Integer, double[]> obs,
                                          final java.util.Set<Integer> lossOfLock) {
            final java.util.Map<String, Integer> idx = new java.util.LinkedHashMap<>();
            idx.put("C1W", 0);
            idx.put("L1W", 1);
            idx.put("C2W", 2);
            idx.put("L2W", 3);
            final List<SatRecord> sats = new java.util.ArrayList<>();
            for (final java.util.Map.Entry<Integer, double[]> e : obs.entrySet()) {
                final int flag = lossOfLock != null && lossOfLock.contains(e.getKey()) ? 1 : 0;
                sats.add(new SatRecord(e.getKey(), idx, e.getValue().clone(), new int[] {0, flag, 0, flag}));
            }
            return new Epoch(date, 0.0, sats);
        }

        public AbsoluteDate getDate() {
            return date;
        }

        /** @return the receiver clock offset reported on the epoch line (s); 0 if absent. */
        public double getReceiverClockOffset() {
            return receiverClockOffset;
        }

        public List<SatRecord> getSatellites() {
            return satellites;
        }
    }

    /** Parsed observation file: the GPS observation-type order and the epochs. */
    public static final class Data {
        private final List<String> obsTypes;
        private final List<Epoch> epochs;
        private double[] antennaDelta;

        Data(final List<String> obsTypes, final List<Epoch> epochs) {
            this.obsTypes = obsTypes;
            this.epochs = epochs;
        }

        /**
         * Antenna reference-point offset from the header ({@code ANTENNA: DELTA X/Y/Z}, or the
         * {@code DELTA H/E/N} record that spaceborne producers such as Sentinel-3 CPOD fill with the
         * body-frame X/Y/Z), in metres; {@code null} if the header has none.
         */
        public double[] getAntennaDelta() {
            return antennaDelta == null ? null : antennaDelta.clone();
        }

        /** @return the GPS observation codes in file order (e.g. C1C, L1C, S1C, C1W, ...). */
        public List<String> getObsTypes() {
            return obsTypes;
        }

        public List<Epoch> getEpochs() {
            return epochs;
        }

        public int size() {
            return epochs.size();
        }
    }

    /**
     * Read a RINEX observation file, keeping every epoch. Transparently
     * decompresses a {@code .gz} file by extension.
     */
    public static Data read(final Path path) throws IOException {
        return read(path, 0.0);
    }

    /**
     * Read a RINEX observation file, thinning to roughly one epoch per
     * {@code decimateSeconds}. Transparently decompresses a {@code .gz} file.
     *
     * @param path            the RINEX (or RINEX {@code .gz}) file
     * @param decimateSeconds minimum spacing between kept epochs (s); &le; 0 keeps all
     */
    public static Data read(final Path path, final double decimateSeconds) throws IOException {
        InputStream in = Files.newInputStream(path);
        if (path.getFileName().toString().endsWith(".gz")) {
            in = new GZIPInputStream(in);
        }
        try (InputStream stream = in) {
            return read(stream, decimateSeconds);
        }
    }

    /** Read a RINEX observation stream, keeping every epoch. */
    public static Data read(final InputStream stream) throws IOException {
        return read(stream, 0.0);
    }

    /**
     * Read a RINEX observation stream, thinning to roughly one epoch per
     * {@code decimateSeconds}.
     */
    public static Data read(final InputStream stream, final double decimateSeconds)
            throws IOException {
        final BufferedReader br =
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));

        final List<String> gpsTypes = new ArrayList<>();
        double[] antennaDelta = null;
        // parse header
        String line;
        while ((line = br.readLine()) != null) {
            final String label = line.length() >= 61 ? line.substring(60).trim() : "";
            if ("SYS / # / OBS TYPES".equals(label) && line.charAt(0) == 'G') {
                final int count = Integer.parseInt(line.substring(3, 6).trim());
                parseObsTypes(line, count, gpsTypes);
                // continuation lines when > 13 types (blank system column)
                while (gpsTypes.size() < count) {
                    line = br.readLine();
                    parseObsTypes(line, count - gpsTypes.size(), gpsTypes);
                }
            } else if ("ANTENNA: DELTA X/Y/Z".equals(label) || "ANTENNA: DELTA H/E/N".equals(label)) {
                try {
                    antennaDelta = new double[] {
                        Double.parseDouble(line.substring(0, 14).trim()),
                        Double.parseDouble(line.substring(14, 28).trim()),
                        Double.parseDouble(line.substring(28, 42).trim())};
                } catch (final RuntimeException ignored) {
                    antennaDelta = null;
                }
            } else if ("END OF HEADER".equals(label)) {
                break;
            }
        }
        if (gpsTypes.isEmpty()) {
            throw new IOException("no GPS observation types found in RINEX header");
        }

        final Map<String, Integer> typeIndex = new LinkedHashMap<>();
        for (int i = 0; i < gpsTypes.size(); i++) {
            typeIndex.put(gpsTypes.get(i), i);
        }

        // parse epochs
        final List<Epoch> epochs = new ArrayList<>();
        AbsoluteDate lastKept = null;
        while ((line = br.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) != '>') {
                continue;
            }
            final String[] h = line.substring(1).trim().split("\\s+");
            final int year = Integer.parseInt(h[0]);
            final int month = Integer.parseInt(h[1]);
            final int day = Integer.parseInt(h[2]);
            final int hour = Integer.parseInt(h[3]);
            final int minute = Integer.parseInt(h[4]);
            final double second = Double.parseDouble(h[5]);
            final int flag = Integer.parseInt(h[6]);
            final int numSat = Integer.parseInt(h[7]);
            final double rcvClock = h.length > 8 ? Double.parseDouble(h[8]) : 0.0;

            final AbsoluteDate date =
                    new AbsoluteDate(year, month, day, hour, minute, second,
                            TimeScalesFactory.getGPS());

            final boolean keep = flag == 0
                    && (lastKept == null || decimateSeconds <= 0.0
                        || date.durationFrom(lastKept) >= decimateSeconds - 1.0e-6);

            final List<SatRecord> sats = keep ? new ArrayList<>() : null;
            for (int s = 0; s < numSat; s++) {
                final String rec = br.readLine();
                if (rec == null) {
                    break;
                }
                if (!keep || rec.length() < 3 || rec.charAt(0) != 'G') {
                    continue;
                }
                final int prn = Integer.parseInt(rec.substring(1, 3).trim());
                final double[] values = new double[gpsTypes.size()];
                final int[] lli = new int[gpsTypes.size()];
                for (int t = 0; t < gpsTypes.size(); t++) {
                    final int start = 3 + 16 * t;
                    final int end = start + 14;
                    if (start >= rec.length()) {
                        values[t] = Double.NaN;
                        continue;
                    }
                    final String field =
                            rec.substring(start, Math.min(end, rec.length())).trim();
                    values[t] = field.isEmpty() ? Double.NaN : Double.parseDouble(field);
                    if (end < rec.length()) {
                        final char c = rec.charAt(end);
                        lli[t] = c >= '0' && c <= '9' ? c - '0' : 0;
                    }
                }
                sats.add(new SatRecord(prn, typeIndex, values, lli));
            }
            if (keep) {
                epochs.add(new Epoch(date, rcvClock, sats));
                lastKept = date;
            }
        }

        if (epochs.isEmpty()) {
            throw new IOException("no observation epochs parsed from RINEX stream");
        }
        final Data data = new Data(new ArrayList<>(gpsTypes), epochs);
        data.antennaDelta = antennaDelta;
        return data;
    }

    private static void parseObsTypes(final String line, final int remaining,
                                      final List<String> out) {
        // codes begin at column 7, each 4 characters wide (3-char code + space)
        for (int i = 0; i < remaining; i++) {
            final int start = 7 + 4 * i;
            final int end = start + 3;
            if (end > line.length()) {
                break;
            }
            final String code = line.substring(start, end).trim();
            if (!code.isEmpty()) {
                out.add(code);
            }
        }
    }
}
