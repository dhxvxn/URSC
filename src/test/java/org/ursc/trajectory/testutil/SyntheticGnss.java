package org.ursc.trajectory.testutil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.PVCoordinatesProvider;
import org.orekit.utils.TimeStampedPVCoordinates;
import org.ursc.trajectory.gnss.GpsConstants;
import org.ursc.trajectory.gnss.RinexObsReader;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource;
import org.ursc.trajectory.orekit.OrekitDates;
import org.ursc.trajectory.orekit.OrekitGpsSource;
import org.ursc.trajectory.orekit.OrekitSession;

/**
 * Test data: a 24-satellite GPS-like constellation fixed in the Earth frame, and dual-frequency
 * code and carrier phase simulated with an independent light-time model (inertial frame,
 * Shapiro delay, receiver clock, integer ambiguities, optional cycle slips).
 */
public final class SyntheticGnss {

    private static final double C = Constants.SPEED_OF_LIGHT;
    private static final double MU = Constants.EIGEN5C_EARTH_MU;

    private SyntheticGnss() {
    }

    /** The synthetic constellation as an Orekit-backed GPS product. */
    public static final class Constellation implements OrekitGpsSource {
        private final AbsoluteDate t0;
        private final OrekitSession s;
        private final GnssOrbitClockSource.ProductClass productClass;
        private final org.ursc.trajectory.time.AbsoluteDate issue;

        public Constellation(final AbsoluteDate t0, final OrekitSession s, final GnssOrbitClockSource.ProductClass pc,
                             final org.ursc.trajectory.time.AbsoluteDate issue) {
            this.t0 = t0;
            this.s = s;
            this.productClass = pc;
            this.issue = issue;
        }

        public Vector3D itrfPosition(final int prn, final AbsoluteDate t) {
            final double a = 26_560e3;
            final double n = Math.sqrt(MU / (a * a * a));
            final int plane = (prn - 1) / 4;
            final double raan = Math.toRadians(60.0 * plane);
            final double inc = Math.toRadians(55.0);
            final double u = Math.toRadians(90.0 * ((prn - 1) % 4) + 15.0 * plane) + n * t.durationFrom(t0);
            final double xo = a * Math.cos(u);
            final double yo = a * Math.sin(u);
            return new Vector3D(xo * Math.cos(raan) - yo * Math.cos(inc) * Math.sin(raan),
                    xo * Math.sin(raan) + yo * Math.cos(inc) * Math.cos(raan), yo * Math.sin(inc));
        }

        @Override
        public PVCoordinatesProvider provider(final int prn) {
            return new PVCoordinatesProvider() {
                @Override
                public TimeStampedPVCoordinates getPVCoordinates(final AbsoluteDate date, final Frame frame) {
                    final Vector3D p = itrfPosition(prn, date);
                    final Vector3D v = itrfPosition(prn, date.shiftedBy(0.5)).subtract(itrfPosition(prn, date.shiftedBy(-0.5)));
                    return s.itrf.getTransformTo(frame, date).transformPVCoordinates(
                            new TimeStampedPVCoordinates(date, new PVCoordinates(p, v)));
                }

                @Override
                public Vector3D getPosition(final AbsoluteDate date, final Frame frame) {
                    return getPVCoordinates(date, frame).getPosition();
                }
            };
        }

        @Override
        public double clock(final int prn, final AbsoluteDate t) {
            return 1.0e-5 * Math.sin(prn) + 1.0e-11 * prn * t.durationFrom(t0);
        }

        @Override
        public boolean covers(final int prn, final AbsoluteDate t) {
            return prn >= 1 && prn <= 24;
        }

        @Override
        public Frame itrf() {
            return s.itrf;
        }

        @Override
        public GnssOrbitClockSource.ProductClass productClass() {
            return productClass;
        }

        @Override
        public String description() {
            return "synthetic " + productClass.label;
        }

        @Override
        public org.ursc.trajectory.time.AbsoluteDate issueTime() {
            return issue;
        }

        @Override
        public boolean issueTimeVerified() {
            return issue != null;
        }
    }

    /** Antenna positions (inertial) at the epochs, and how the simulation is perturbed. */
    public static final class Options {
        public double codeNoise = 0.3;
        public double phaseNoise = 0.002;
        public long seed = 11;
        /** Epoch index of a 5-cycle L1 slip on one satellite (Melbourne-Wubbena detectable), or -1. */
        public int wideLaneSlipAt = -1;
        /** Epoch index of equal 1-cycle L1/L2 slips on one satellite, or -1. */
        public int equalSlipAt = -1;
        public int wideLanePrn = -1;
        public int equalPrn = -1;
    }

    /**
     * Simulate dual-frequency observations.
     *
     * @param dates      reception epochs
     * @param antenna    antenna phase centre (GCRF) at each epoch
     */
    public static List<RinexObsReader.Epoch> observe(final List<AbsoluteDate> dates, final List<Vector3D> antenna,
                                                     final Constellation gps, final OrekitSession s, final Options o) {
        final Random rnd = new Random(o.seed);
        final double l1 = C / GpsConstants.F_L1;
        final double l2 = C / GpsConstants.F_L2;
        final Map<Integer, double[]> amb = new HashMap<>();
        final List<RinexObsReader.Epoch> epochs = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            final AbsoluteDate t = dates.get(i);
            final Vector3D ant = antenna.get(i);
            final Vector3D antItrf = s.gcrf.getStaticTransformTo(s.itrf, t).transformPosition(ant);
            final double clk = 150.0 + 0.02 * i;
            final Map<Integer, double[]> obs = new HashMap<>();
            final Set<Integer> lli = new HashSet<>();
            for (int prn = 1; prn <= 24; prn++) {
                if (gps.itrfPosition(prn, t).subtract(antItrf).dotProduct(antItrf) <= 0.0) {
                    continue;
                }
                double tau = 0.07;
                Vector3D rs = null;
                for (int k = 0; k < 5; k++) {
                    rs = gps.provider(prn).getPosition(t.shiftedBy(-tau), s.gcrf);
                    tau = rs.distance(ant) / C;
                }
                final double rho = rs.distance(ant);
                final double sum = rs.getNorm() + ant.getNorm();
                final double range = rho + clk - C * gps.clock(prn, t.shiftedBy(-tau))
                        + 2.0 * MU / (C * C) * Math.log((sum + rho) / (sum - rho));
                final boolean known = amb.containsKey(prn);
                final double[] nn = amb.computeIfAbsent(prn,
                        p -> new double[] {Math.round(rnd.nextGaussian() * 1e5), Math.round(rnd.nextGaussian() * 1e5)});
                if (i == o.wideLaneSlipAt && o.wideLanePrn < 0 && known) {
                    o.wideLanePrn = prn;
                    nn[0] += 5.0;
                }
                if (i == o.equalSlipAt && o.equalPrn < 0 && known && prn != o.wideLanePrn) {
                    o.equalPrn = prn;
                    nn[0] += 1.0;
                    nn[1] += 1.0;
                }
                final double noise = o.phaseNoise * rnd.nextGaussian();
                obs.put(prn, new double[] {range + o.codeNoise * rnd.nextGaussian(), (range + noise) / l1 + nn[0],
                    range + o.codeNoise * rnd.nextGaussian(), (range + noise) / l2 + nn[1]});
            }
            epochs.add(RinexObsReader.Epoch.syntheticDual(OrekitDates.fromOrekit(t), obs, lli));
        }
        return epochs;
    }
}
