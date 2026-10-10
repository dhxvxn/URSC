package org.ursc.trajectory.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Reader for CCSDS/Earth-Explorer <b>POEORB</b> precise-orbit ephemeris files
 * (e.g. Sentinel-3 {@code AUX_POEORB *.EOF}): a list of time-tagged state vectors
 * in the Earth-fixed (ITRF) frame, plus the UT1&minus;UTC offset carried in the
 * records. Used as reference truth for propagation validation and as the
 * observation source for orbit determination.
 */
public final class PoeOrbReader {

    /** Parsed ephemeris: aligned arrays of epoch and Earth-fixed position/velocity. */
    public static final class Ephemeris {
        public final List<AbsoluteDate> dates;
        public final List<Vector3D> positionItrf;
        public final List<Vector3D> velocityItrf;
        public final double ut1MinusUtc; // seconds, from the first record

        Ephemeris(final List<AbsoluteDate> dates, final List<Vector3D> positionItrf,
                  final List<Vector3D> velocityItrf, final double ut1MinusUtc) {
            this.dates = dates;
            this.positionItrf = positionItrf;
            this.velocityItrf = velocityItrf;
            this.ut1MinusUtc = ut1MinusUtc;
        }

        public int size() {
            return dates.size();
        }
    }

    private static final Pattern UTC =
            Pattern.compile("UTC=(\\d+)-(\\d+)-(\\d+)T(\\d+):(\\d+):([0-9.]+)");
    private static final Pattern UT1 =
            Pattern.compile("UT1=(\\d+)-(\\d+)-(\\d+)T(\\d+):(\\d+):([0-9.]+)");

    private PoeOrbReader() {
    }

    public static Ephemeris read(final Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return read(in);
        }
    }

    public static Ephemeris read(final InputStream in) throws IOException {
        final List<AbsoluteDate> dates = new ArrayList<>();
        final List<Vector3D> rItrf = new ArrayList<>();
        final List<Vector3D> vItrf = new ArrayList<>();
        double dut1 = 0.0;
        boolean dut1Set = false;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            AbsoluteDate curDate = null;
            double utcSod = 0.0;
            final double[] c = new double[6];
            while ((line = br.readLine()) != null) {
                final String s = line.trim();
                if (s.startsWith("<UTC>")) {
                    final Matcher m = UTC.matcher(s);
                    if (m.find()) {
                        final int y = Integer.parseInt(m.group(1));
                        final int mo = Integer.parseInt(m.group(2));
                        final int d = Integer.parseInt(m.group(3));
                        final int h = Integer.parseInt(m.group(4));
                        final int mi = Integer.parseInt(m.group(5));
                        final double sec = Double.parseDouble(m.group(6));
                        curDate = new AbsoluteDate(y, mo, d, h, mi, sec, TimeScalesFactory.getUTC());
                        utcSod = sec + 60.0 * mi + 3600.0 * h;
                    }
                } else if (!dut1Set && s.startsWith("<UT1>")) {
                    final Matcher m = UT1.matcher(s);
                    if (m.find()) {
                        final double sec = Double.parseDouble(m.group(6));
                        final double mi = Integer.parseInt(m.group(5));
                        final double h = Integer.parseInt(m.group(4));
                        dut1 = (sec + 60.0 * mi + 3600.0 * h) - utcSod;
                        dut1Set = true;
                    }
                } else if (s.startsWith("<X ")) {
                    c[0] = tagValue(s);
                } else if (s.startsWith("<Y ")) {
                    c[1] = tagValue(s);
                } else if (s.startsWith("<Z ")) {
                    c[2] = tagValue(s);
                } else if (s.startsWith("<VX ")) {
                    c[3] = tagValue(s);
                } else if (s.startsWith("<VY ")) {
                    c[4] = tagValue(s);
                } else if (s.startsWith("<VZ ")) {
                    c[5] = tagValue(s);
                } else if (s.startsWith("</OSV>")) {
                    dates.add(curDate);
                    rItrf.add(new Vector3D(c[0], c[1], c[2]));
                    vItrf.add(new Vector3D(c[3], c[4], c[5]));
                }
            }
        }
        if (dates.isEmpty()) {
            throw new IOException("no OSV records found; is this a POEORB .EOF file?");
        }
        return new Ephemeris(dates, rItrf, vItrf, dut1);
    }

    private static double tagValue(final String line) {
        final int a = line.indexOf('>') + 1;
        final int b = line.indexOf('<', a);
        return Double.parseDouble(line.substring(a, b).trim());
    }
}
