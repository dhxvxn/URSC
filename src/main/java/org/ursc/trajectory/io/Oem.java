package org.ursc.trajectory.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CCSDS Orbit Ephemeris Message, version 2.0, KVN encoding (CCSDS 502.0-B-2).
 * Units on the wire: km, km/s and km^2-based covariance; in memory: metres and seconds.
 * Epochs are kept as the file's text (the caller owns the time system).
 */
public final class Oem {

    public static final String VERSION = "2.0";

    /** Header block. */
    public String creationDate = "";
    public String originator = "URSC";
    public final List<String> headerComments = new ArrayList<>();
    public final List<Segment> segments = new ArrayList<>();

    /** One META/DATA/COVARIANCE segment. */
    public static final class Segment {
        /** Metadata in file order, e.g. OBJECT_NAME, OBJECT_ID, CENTER_NAME, REF_FRAME, TIME_SYSTEM ... */
        public final Map<String, String> meta = new LinkedHashMap<>();
        public final List<String> dataComments = new ArrayList<>();
        public final List<StateLine> states = new ArrayList<>();
        public final List<CovarianceBlock> covariances = new ArrayList<>();

        public String get(final String key) {
            return meta.getOrDefault(key, "");
        }
    }

    /** Ephemeris line: epoch, position (m), velocity (m/s). */
    public static final class StateLine {
        public final String epoch;
        public final double[] pv;

        public StateLine(final String epoch, final double[] pv) {
            this.epoch = epoch;
            this.pv = pv.clone();
        }
    }

    /** Covariance block: epoch, optional reference frame, full symmetric 6x6 in m and m/s. */
    public static final class CovarianceBlock {
        public final String epoch;
        public final String refFrame;
        public final double[][] matrix;

        public CovarianceBlock(final String epoch, final String refFrame, final double[][] matrix) {
            this.epoch = epoch;
            this.refFrame = refFrame;
            this.matrix = matrix;
        }
    }

    // ------------------------------------------------------------------ write

    public void write(final Path file) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.US_ASCII)) {
            write(w);
        }
    }

    public String toKvn() {
        final java.io.StringWriter sw = new java.io.StringWriter();
        try {
            write(sw);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return sw.toString();
    }

    public void write(final Writer w) throws IOException {
        w.write("CCSDS_OEM_VERS = " + VERSION + "\n");
        for (final String c : headerComments) {
            w.write(comment(c));
        }
        w.write("CREATION_DATE = " + creationDate + "\n");
        w.write("ORIGINATOR = " + originator + "\n");
        for (final Segment s : segments) {
            w.write("\nMETA_START\n");
            for (final Map.Entry<String, String> e : s.meta.entrySet()) {
                w.write(String.format(Locale.US, "%-20s = %s%n", e.getKey(), e.getValue()));
            }
            w.write("META_STOP\n\n");
            for (final String c : s.dataComments) {
                w.write(comment(c));
            }
            for (final StateLine l : s.states) {
                w.write(String.format(Locale.US, "%s %17.9f %17.9f %17.9f %15.12f %15.12f %15.12f%n", l.epoch,
                        l.pv[0] / 1000, l.pv[1] / 1000, l.pv[2] / 1000, l.pv[3] / 1000, l.pv[4] / 1000, l.pv[5] / 1000));
            }
            if (!s.covariances.isEmpty()) {
                w.write("\nCOVARIANCE_START\n");
                for (final CovarianceBlock b : s.covariances) {
                    w.write("EPOCH = " + b.epoch + "\n");
                    if (b.refFrame != null && !b.refFrame.isEmpty()) {
                        w.write("COV_REF_FRAME = " + b.refFrame + "\n");
                    }
                    for (int i = 0; i < 6; i++) {
                        final StringBuilder row = new StringBuilder();
                        for (int j = 0; j <= i; j++) {
                            if (j > 0) {
                                row.append(' ');
                            }
                            row.append(String.format(Locale.US, "%17.10e", b.matrix[i][j] * 1e-6));
                        }
                        w.write(row.append('\n').toString());
                    }
                }
                w.write("COVARIANCE_STOP\n");
            }
        }
    }

    private static String comment(final String c) {
        final StringBuilder sb = new StringBuilder();
        for (final String line : c.split("\n")) {
            sb.append("COMMENT ").append(line.replace('\r', ' ')).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ read

    public static Oem read(final Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.US_ASCII));
    }

    public static Oem parse(final String text) throws IOException {
        final Oem oem = new Oem();
        final BufferedReader br = new BufferedReader(new StringReader(text));
        String line;
        Segment seg = null;
        boolean inMeta = false;
        boolean inCov = false;
        boolean sawVersion = false;
        String covEpoch = null;
        String covFrame = "";
        final List<Double> covValues = new ArrayList<>();
        int lineNo = 0;
        while ((line = br.readLine()) != null) {
            lineNo++;
            final String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("COMMENT")) {
                final String c = t.length() > 7 ? t.substring(7).trim() : "";
                if (seg == null) {
                    oem.headerComments.add(c);
                } else if (!inMeta && !inCov) {
                    seg.dataComments.add(c);
                }
                continue;
            }
            if (t.startsWith("CCSDS_OEM_VERS")) {
                sawVersion = true;
                continue;
            }
            if ("META_START".equals(t)) {
                seg = new Segment();
                oem.segments.add(seg);
                inMeta = true;
                continue;
            }
            if ("META_STOP".equals(t)) {
                inMeta = false;
                continue;
            }
            if ("COVARIANCE_START".equals(t)) {
                inCov = true;
                continue;
            }
            if ("COVARIANCE_STOP".equals(t)) {
                flushCov(seg, covEpoch, covFrame, covValues, lineNo);
                inCov = false;
                covEpoch = null;
                continue;
            }
            final int eq = t.indexOf('=');
            if (eq > 0 && !Character.isDigit(t.charAt(0)) && !t.startsWith("-") && !t.startsWith("+")) {
                final String k = t.substring(0, eq).trim();
                final String v = t.substring(eq + 1).trim();
                if (inMeta) {
                    seg.meta.put(k, v);
                } else if (inCov) {
                    if ("EPOCH".equals(k)) {
                        flushCov(seg, covEpoch, covFrame, covValues, lineNo);
                        covEpoch = v;
                        covFrame = "";
                    } else if ("COV_REF_FRAME".equals(k)) {
                        covFrame = v;
                    }
                } else if ("CREATION_DATE".equals(k)) {
                    oem.creationDate = v;
                } else if ("ORIGINATOR".equals(k)) {
                    oem.originator = v;
                }
                continue;
            }
            final String[] f = t.split("\\s+");
            if (inCov) {
                for (final String x : f) {
                    covValues.add(Double.parseDouble(x) * 1e6);
                }
                continue;
            }
            if (seg == null || inMeta) {
                throw new IOException("OEM line " + lineNo + ": data outside a segment");
            }
            if (f.length < 7) {
                throw new IOException("OEM line " + lineNo + ": expected epoch and 6 state values");
            }
            final double[] pv = new double[6];
            for (int i = 0; i < 6; i++) {
                pv[i] = Double.parseDouble(f[i + 1]) * 1000.0;
            }
            seg.states.add(new StateLine(f[0], pv));
        }
        if (!sawVersion) {
            throw new IOException("not a CCSDS OEM (no CCSDS_OEM_VERS line)");
        }
        return oem;
    }

    private static void flushCov(final Segment seg, final String epoch, final String frame,
                                 final List<Double> v, final int lineNo) throws IOException {
        if (epoch == null) {
            return;
        }
        if (v.size() != 21) {
            throw new IOException("OEM covariance ending near line " + lineNo + " has " + v.size()
                    + " values, expected 21 (lower triangle of 6x6)");
        }
        final double[][] m = new double[6][6];
        int k = 0;
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j <= i; j++) {
                m[i][j] = v.get(k);
                m[j][i] = v.get(k);
                k++;
            }
        }
        seg.covariances.add(new CovarianceBlock(epoch, frame, m));
        v.clear();
    }
}
