package org.ursc.trajectory.orekit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.data.DataSource;
import org.orekit.files.rinex.clock.RinexClock;
import org.orekit.files.rinex.clock.RinexClockParser;
import org.orekit.files.rinex.navigation.RinexNavigation;
import org.orekit.files.rinex.navigation.RinexNavigationParser;
import org.orekit.files.sp3.SP3;
import org.orekit.files.sp3.SP3Coordinate;
import org.orekit.files.sp3.SP3Ephemeris;
import org.orekit.files.sp3.SP3Parser;
import org.orekit.files.sp3.SP3Segment;
import org.orekit.frames.Frame;
import org.orekit.propagation.BoundedPropagator;
import org.orekit.propagation.analytical.gnss.GNSSPropagator;
import org.orekit.propagation.analytical.gnss.GNSSPropagatorBuilder;
import org.orekit.propagation.analytical.gnss.data.GPSLegacyNavigationMessage;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.PVCoordinatesProvider;
import org.orekit.utils.TimeStampedPVCoordinates;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource.ProductClass;
import org.ursc.trajectory.gnss.products.IgsProductName;
import org.ursc.trajectory.gnss.products.ProductLoader;
import org.ursc.trajectory.io.ProductInput;

/**
 * Loads GPS orbit/clock products with Orekit's parsers ({@link SP3Parser}, {@link RinexClockParser},
 * {@link RinexNavigationParser}) and evaluates them with Orekit's propagators (SP3 interpolation,
 * {@link GNSSPropagator} for the broadcast message). File classification (IGS Final / Rapid /
 * Ultra-rapid from the file name, nominal issue time for cutoff eligibility) is unchanged.
 */
public final class OrekitGpsProducts {

    private static final double C = Constants.SPEED_OF_LIGHT;
    /** Broadcast messages older/newer than this (from Toe) are not used. */
    private static final double MAX_AGE_S = 4.0 * 3600.0;

    private OrekitGpsProducts() {
    }

    private static DataSource source(final Path p) {
        return new DataSource(ProductInput.baseName(p), () -> ProductInput.open(p));
    }

    /** Same contract as {@link ProductLoader#load}, with Orekit-backed sources. */
    public static ProductLoader.Loaded load(final List<Path> files, final Map<String, org.ursc.trajectory.time.AbsoluteDate> issueOverride,
                                            final OrekitSession session) {
        final ProductLoader.Loaded out = new ProductLoader.Loaded();
        final Map<String, Path> sp3 = new LinkedHashMap<>();
        final Map<String, Path> clk = new LinkedHashMap<>();
        final List<GPSLegacyNavigationMessage> nav = new ArrayList<>();
        final List<String> navNames = new ArrayList<>();
        for (final Path f : files) {
            final ProductLoader.Kind kind = ProductLoader.sniff(f);
            if (kind == ProductLoader.Kind.NAVIGATION) {
                try {
                    final RinexNavigation n = new RinexNavigationParser(session.context.getTimeScales()).parse(source(f));
                    int count = 0;
                    for (final List<GPSLegacyNavigationMessage> l : n.getGPSLegacyNavigationMessages().values()) {
                        nav.addAll(l);
                        count += l.size();
                    }
                    if (count == 0) {
                        out.problems.add(f.getFileName() + ": navigation file has no GPS LNAV messages");
                    } else {
                        navNames.add(f.getFileName().toString());
                    }
                } catch (final IOException | RuntimeException e) {
                    out.problems.add(f.getFileName() + ": unreadable navigation file (" + e.getMessage() + ")");
                }
                continue;
            }
            if (kind != ProductLoader.Kind.SP3 && kind != ProductLoader.Kind.CLOCK) {
                continue;
            }
            final IgsProductName name = IgsProductName.parse(ProductInput.baseName(f));
            if (!name.recognised()) {
                out.problems.add(f.getFileName() + ": " + kind
                        + " file name is not a recognised IGS Final/Rapid/Ultra-rapid name; product class unknown, not used");
                continue;
            }
            final String key = name.productClass() + "|" + name.provider() + "|" + name.start();
            (kind == ProductLoader.Kind.SP3 ? sp3 : clk).put(key, f);
        }
        if (!nav.isEmpty()) {
            out.sources.add(new Broadcast(nav, "broadcast " + String.join(" + ", navNames), session));
        }
        for (final Map.Entry<String, Path> e : sp3.entrySet()) {
            final Path orbitFile = e.getValue();
            final Path clockFile = clk.remove(e.getKey());
            final IgsProductName name = IgsProductName.parse(ProductInput.baseName(orbitFile));
            try {
                final SP3 orbit = new SP3Parser(Constants.EIGEN5C_EARTH_MU, 7, s -> session.itrf,
                        session.context.getTimeScales()).parse(source(orbitFile));
                final RinexClock clock = clockFile == null ? null
                        : new RinexClockParser(s -> session.itrf, s -> null, session.context.getTimeScales())
                                .parse(source(clockFile));
                org.ursc.trajectory.time.AbsoluteDate issue = name.nominalIssueTime();
                boolean verified = false;
                final org.ursc.trajectory.time.AbsoluteDate o = issueOverride.get(orbitFile.getFileName().toString());
                if (o != null) {
                    issue = o;
                    verified = true;
                }
                final String desc = name.productClass().label + " (" + name.provider() + ") "
                        + orbitFile.getFileName() + (clockFile == null ? " [SP3 clocks]" : " + " + clockFile.getFileName());
                out.sources.add(new Precise(orbit, clock, name.productClass(), desc, issue, verified,
                        name.productClass() == ProductClass.ULTRA_RAPID ? name.observedEnd() : null, session));
            } catch (final RuntimeException ex) {
                out.problems.add(orbitFile.getFileName() + ": unreadable SP3 (" + ex.getMessage() + ")");
            }
        }
        for (final Path orphan : clk.values()) {
            out.problems.add(orphan.getFileName() + ": clock file without a matching SP3 orbit; not used");
        }
        return out;
    }

    /** Linearly interpolated clock samples of one satellite (gaps over 3 sample intervals are holes). */
    private static final class ClockSeries {
        final double[] t;
        final double[] b;
        final double maxGap;

        ClockSeries(final double[] t, final double[] b, final double interval) {
            this.t = t;
            this.b = b;
            this.maxGap = 3.0 * interval;
        }

        double at(final double s) {
            int i = Arrays.binarySearch(t, s);
            if (i >= 0) {
                return b[i];
            }
            i = -i - 2;
            if (i < 0 || i + 1 >= t.length || t[i + 1] - t[i] > maxGap) {
                return Double.NaN;
            }
            final double f = (s - t[i]) / (t[i + 1] - t[i]);
            return b[i] + f * (b[i + 1] - b[i]);
        }
    }

    private static double seconds(final AbsoluteDate d) {
        return d.durationFrom(AbsoluteDate.J2000_EPOCH);
    }

    private static int prn(final String id) {
        return id.length() >= 2 && id.charAt(0) == 'G' ? Integer.parseInt(id.substring(1).trim()) : -1;
    }

    /** Periodic relativistic clock correction -2 r.v / c^2 (s). */
    private static double relativistic(final PVCoordinates pv) {
        return -2.0 * pv.getPosition().dotProduct(pv.getVelocity()) / (C * C);
    }

    /** IGS precise orbit (SP3) with RINEX clocks, or SP3 clocks when no clock file is given. */
    static final class Precise implements OrekitGpsSource {
        private final Map<Integer, BoundedPropagator> orbits = new HashMap<>();
        private final Map<Integer, AbsoluteDate[]> spans = new HashMap<>();
        private final Map<Integer, ClockSeries> clocks = new HashMap<>();
        private final ProductClass productClass;
        private final String description;
        private final org.ursc.trajectory.time.AbsoluteDate issue;
        private final boolean verified;
        private final AbsoluteDate observedEnd;
        private final Frame itrf;

        Precise(final SP3 sp3, final RinexClock clk, final ProductClass productClass, final String description,
                final org.ursc.trajectory.time.AbsoluteDate issue, final boolean verified,
                final org.ursc.trajectory.time.AbsoluteDate observedEnd, final OrekitSession session) {
            this.productClass = productClass;
            this.description = description;
            this.issue = issue;
            this.verified = verified;
            this.observedEnd = observedEnd == null ? null : OrekitDates.toOrekit(observedEnd);
            this.itrf = session.itrf;
            for (final Map.Entry<String, SP3Ephemeris> e : sp3.getSatellites().entrySet()) {
                final int prn = prn(e.getKey());
                if (prn < 0) {
                    continue;
                }
                orbits.put(prn, e.getValue().getPropagator());
                spans.put(prn, new AbsoluteDate[] {e.getValue().getStart(), e.getValue().getStop()});
                if (clk == null) {
                    final List<Double> t = new ArrayList<>();
                    final List<Double> b = new ArrayList<>();
                    for (final SP3Segment seg : e.getValue().getSegments()) {
                        for (final SP3Coordinate c : seg.getCoordinates()) {
                            if (Math.abs(c.getClockCorrection()) < 0.1) {
                                t.add(seconds(c.getDate()));
                                b.add(c.getClockCorrection());
                            }
                        }
                    }
                    clocks.put(prn, series(t, b, sp3.getHeader().getEpochInterval()));
                }
            }
            if (clk != null) {
                for (final Map.Entry<String, List<RinexClock.ClockDataLine>> e : clk.getClockData().entrySet()) {
                    final int prn = prn(e.getKey());
                    if (prn < 0) {
                        continue;
                    }
                    final List<Double> t = new ArrayList<>();
                    final List<Double> b = new ArrayList<>();
                    for (final RinexClock.ClockDataLine l : e.getValue()) {
                        if (l.getDataType() == RinexClock.ClockDataType.AS) {
                            t.add(seconds(l.getEpoch()));
                            b.add(l.getClockBias());
                        }
                    }
                    clocks.put(prn, series(t, b, 30.0));
                }
            }
        }

        private static ClockSeries series(final List<Double> t, final List<Double> b, final double interval) {
            final Integer[] idx = new Integer[t.size()];
            for (int i = 0; i < idx.length; i++) {
                idx[i] = i;
            }
            Arrays.sort(idx, (x, y) -> Double.compare(t.get(x), t.get(y)));
            final double[] ts = new double[idx.length];
            final double[] bs = new double[idx.length];
            for (int i = 0; i < idx.length; i++) {
                ts[i] = t.get(idx[i]);
                bs[i] = b.get(idx[i]);
            }
            double step = interval;
            if (ts.length > 1) {
                step = Math.max(interval, ts[1] - ts[0]);
            }
            return new ClockSeries(ts, bs, step);
        }

        @Override
        public PVCoordinatesProvider provider(final int prn) {
            return orbits.get(prn);
        }

        @Override
        public boolean centreOfMassOrbits() {
            return true;
        }

        @Override
        public double clock(final int prn, final AbsoluteDate t) {
            final ClockSeries s = clocks.get(prn);
            final double raw = s == null ? Double.NaN : s.at(seconds(t));
            if (Double.isNaN(raw)) {
                return Double.NaN;
            }
            return raw + relativistic(orbits.get(prn).getPVCoordinates(t, itrf));
        }

        @Override
        public boolean covers(final int prn, final AbsoluteDate t) {
            final AbsoluteDate[] sp = spans.get(prn);
            final ClockSeries s = clocks.get(prn);
            return sp != null && s != null && t.durationFrom(sp[0]) >= 1.0 && sp[1].durationFrom(t) >= 1.0
                    && !Double.isNaN(s.at(seconds(t)));
        }

        @Override
        public Frame itrf() {
            return itrf;
        }

        @Override
        public ProductClass productClass() {
            return productClass;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public org.ursc.trajectory.time.AbsoluteDate issueTime() {
            return issue;
        }

        @Override
        public boolean issueTimeVerified() {
            return verified;
        }

        @Override
        public boolean isPredicted(final org.ursc.trajectory.time.AbsoluteDate t) {
            return observedEnd != null && OrekitDates.toOrekit(t).isAfter(observedEnd);
        }
    }

    /** Broadcast GPS LNAV ephemerides: nearest-Toe message within 4 h, evaluated by Orekit. */
    static final class Broadcast implements OrekitGpsSource {
        private final Map<Integer, List<GPSLegacyNavigationMessage>> byPrn = new HashMap<>();
        private final Map<GPSLegacyNavigationMessage, GNSSPropagator> propagators = new HashMap<>();
        private final Map<Integer, PVCoordinatesProvider> providers = new HashMap<>();
        private final String description;
        private final OrekitSession session;

        Broadcast(final List<GPSLegacyNavigationMessage> messages, final String description, final OrekitSession session) {
            this.description = description;
            this.session = session;
            for (final GPSLegacyNavigationMessage m : messages) {
                final List<GPSLegacyNavigationMessage> l = byPrn.computeIfAbsent(m.getPRN(), k -> new ArrayList<>());
                boolean dup = false;
                for (final GPSLegacyNavigationMessage x : l) {
                    if (Math.abs(x.getDate().durationFrom(m.getDate())) < 1.0) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    l.add(m);
                }
            }
        }

        private GPSLegacyNavigationMessage message(final int prn, final AbsoluteDate t) {
            final List<GPSLegacyNavigationMessage> l = byPrn.get(prn);
            if (l == null) {
                return null;
            }
            GPSLegacyNavigationMessage best = null;
            double bestDt = Double.MAX_VALUE;
            for (final GPSLegacyNavigationMessage m : l) {
                final double dt = Math.abs(t.durationFrom(m.getDate()));
                if (dt < bestDt) {
                    bestDt = dt;
                    best = m;
                }
            }
            return bestDt <= MAX_AGE_S ? best : null;
        }

        private synchronized GNSSPropagator propagator(final GPSLegacyNavigationMessage m) {
            return propagators.computeIfAbsent(m, x -> new GNSSPropagatorBuilder(x, session.context.getFrames())
                    .eci(session.eme2000).ecef(session.itrf).build());
        }

        @Override
        public synchronized PVCoordinatesProvider provider(final int prn) {
            return providers.computeIfAbsent(prn, p -> new PVCoordinatesProvider() {
                @Override
                public TimeStampedPVCoordinates getPVCoordinates(final AbsoluteDate date, final Frame frame) {
                    final GPSLegacyNavigationMessage m = message(p, date);
                    if (m == null) {
                        throw new IllegalStateException("no broadcast ephemeris for G" + p + " at " + date);
                    }
                    return propagator(m).getPVCoordinates(date, frame);
                }

                @Override
                public Vector3D getPosition(final AbsoluteDate date, final Frame frame) {
                    return getPVCoordinates(date, frame).getPosition();
                }
            });
        }

        @Override
        public org.ursc.trajectory.gnss.products.GnssOrbitClockSource availableBy(final org.ursc.trajectory.time.AbsoluteDate cutoff) {
            final AbsoluteDate c = OrekitDates.toOrekit(cutoff);
            final List<GPSLegacyNavigationMessage> kept = new ArrayList<>();
            for (final List<GPSLegacyNavigationMessage> l : byPrn.values()) {
                for (final GPSLegacyNavigationMessage m : l) {
                    if (m.getDate().durationFrom(c) <= 0.0) {
                        kept.add(m);
                    }
                }
            }
            return new Broadcast(kept, description + " [messages with Toe <= cutoff]", session);
        }

        @Override
        public double clock(final int prn, final AbsoluteDate t) {
            final GPSLegacyNavigationMessage m = message(prn, t);
            if (m == null) {
                return Double.NaN;
            }
            final double dt = t.durationFrom(m.getEpochToc());
            return m.getAf0() + m.getAf1() * dt + m.getAf2() * dt * dt
                    + relativistic(propagator(m).getPVCoordinates(t, session.itrf));
        }

        @Override
        public boolean covers(final int prn, final AbsoluteDate t) {
            return message(prn, t) != null;
        }

        @Override
        public Frame itrf() {
            return session.itrf;
        }

        @Override
        public ProductClass productClass() {
            return ProductClass.BROADCAST;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public org.ursc.trajectory.time.AbsoluteDate issueTime() {
            return null;
        }

        @Override
        public boolean issueTimeVerified() {
            return false;
        }
    }
}
