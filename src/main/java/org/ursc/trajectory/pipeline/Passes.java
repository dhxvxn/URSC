package org.ursc.trajectory.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Ground-station visibility passes from an Earth-fixed trajectory sampled in time. Elevation is
 * geodetic (WGS-84 local vertical); AOS/LOS are interpolated linearly between samples where the
 * elevation crosses the station's mask.
 */
public final class Passes {

    private static final double A = 6378137.0;
    private static final double F = 1.0 / 298.257223563;
    private static final double E2 = F * (2 - F);

    private Passes() {
    }

    /** A ground station: geodetic latitude/longitude (deg), height (m) and elevation mask (deg). */
    public static final class Station {
        public String name = "";
        public double latDeg;
        public double lonDeg;
        public double heightM;
        public double minElevationDeg = 5.0;
    }

    /** One pass. Times are seconds from the trajectory start. */
    public static final class Pass {
        public String station;
        public double aos;
        public double los;
        public double maxElevationDeg;
        public double maxTime;
        /** True if the pass is cut by the start or end of the trajectory. */
        public boolean truncated;
    }

    /** Station position (m, Earth-fixed) and local "up" unit vector. */
    static double[][] site(final Station s) {
        final double phi = Math.toRadians(s.latDeg);
        final double lam = Math.toRadians(s.lonDeg);
        final double n = A / Math.sqrt(1 - E2 * Math.sin(phi) * Math.sin(phi));
        final double[] p = {(n + s.heightM) * Math.cos(phi) * Math.cos(lam), (n + s.heightM) * Math.cos(phi) * Math.sin(lam),
                            (n * (1 - E2) + s.heightM) * Math.sin(phi)};
        final double[] up = {Math.cos(phi) * Math.cos(lam), Math.cos(phi) * Math.sin(lam), Math.sin(phi)};
        return new double[][] {p, up};
    }

    /** Elevation (deg) of an Earth-fixed point (m) seen from the station. */
    static double elevation(final double[][] site, final double x, final double y, final double z) {
        final double dx = x - site[0][0];
        final double dy = y - site[0][1];
        final double dz = z - site[0][2];
        final double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Math.toDegrees(Math.asin((dx * site[1][0] + dy * site[1][1] + dz * site[1][2]) / r));
    }

    /**
     * @param t    sample times (s from start)
     * @param itrf Earth-fixed positions, flattened x,y,z in metres
     */
    public static List<Pass> compute(final Station st, final double[] t, final double[] itrf) {
        final double[][] site = site(st);
        final List<Pass> out = new ArrayList<>();
        Pass cur = null;
        double prevEl = Double.NaN;
        for (int i = 0; i < t.length; i++) {
            final double el = elevation(site, itrf[3 * i], itrf[3 * i + 1], itrf[3 * i + 2]);
            final boolean up = el >= st.minElevationDeg;
            if (up && cur == null) {
                cur = new Pass();
                cur.station = st.name;
                if (i == 0) {
                    cur.aos = t[0];
                    cur.truncated = true;
                } else {
                    cur.aos = t[i - 1] + (t[i] - t[i - 1]) * (st.minElevationDeg - prevEl) / (el - prevEl);
                }
                cur.maxElevationDeg = el;
                cur.maxTime = t[i];
            }
            if (cur != null) {
                if (up && el > cur.maxElevationDeg) {
                    cur.maxElevationDeg = el;
                    cur.maxTime = t[i];
                }
                if (!up) {
                    cur.los = t[i - 1] + (t[i] - t[i - 1]) * (st.minElevationDeg - prevEl) / (el - prevEl);
                    out.add(cur);
                    cur = null;
                }
            }
            prevEl = el;
        }
        if (cur != null) {
            cur.los = t[t.length - 1];
            cur.truncated = true;
            out.add(cur);
        }
        return out;
    }
}
