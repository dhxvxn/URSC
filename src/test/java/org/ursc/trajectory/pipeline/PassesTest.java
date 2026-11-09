package org.ursc.trajectory.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Ground-station passes on an analytic overhead track. */
class PassesTest {

    @Test
    void findsAnOverheadPassWithInterpolatedAosLos() {
        // a point moving along the equatorial meridian plane over a station at (0,0): circular, r = 7000 km
        final int n = 2001;
        final double[] t = new double[n];
        final double[] x = new double[3 * n];
        final double r = 7.0e6;
        final double omega = 2 * Math.PI / 6000.0;
        for (int i = 0; i < n; i++) {
            t[i] = i * 3.0;
            final double a = omega * (t[i] - 3000.0); // overhead at t = 3000 s
            x[3 * i] = r * Math.cos(a);
            x[3 * i + 1] = 0;
            x[3 * i + 2] = r * Math.sin(a);
        }
        final Passes.Station st = new Passes.Station();
        st.name = "EQ";
        st.minElevationDeg = 10.0;
        final List<Passes.Pass> p = Passes.compute(st, t, x);
        assertEquals(1, p.size());
        assertEquals(90.0, p.get(0).maxElevationDeg, 0.1);
        assertEquals(3000.0, p.get(0).maxTime, 3.0);
        // symmetric about the overhead time
        assertEquals(3000.0 - p.get(0).aos, p.get(0).los - 3000.0, 0.5);
        // geometry: elevation 10 deg is reached at central angle 90-10-asin(Re*cos(10)/r)
        final double re = 6378137.0;
        final double lambda = Math.toRadians(80.0) - Math.asin(re * Math.cos(Math.toRadians(10)) / r);
        assertEquals(3000.0 - lambda / omega, p.get(0).aos, 1.0);
        assertTrue(!p.get(0).truncated);
    }
}
