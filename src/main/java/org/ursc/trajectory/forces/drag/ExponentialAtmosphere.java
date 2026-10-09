package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Piecewise-exponential static atmosphere using the standard reference table of
 * Vallado (Table 8-4), valid from 0 to 1000 km:
 * <pre>
 *   rho(h) = rho0 * exp(-(h - h0) / H)
 * </pre>
 * where {@code (h0, rho0, H)} are the base altitude, reference density and scale
 * height of the layer bracketing the geodetic altitude {@code h}.
 *
 * <p>Static (no diurnal/solar-activity variation) but robust and dependency-free;
 * use it for lifetime trend studies and as the baseline against which
 * {@link HarrisPriesterAtmosphere} and future NRLMSISE/Jacchia models are
 * compared.</p>
 */
public final class ExponentialAtmosphere implements Atmosphere {

    // base altitude (km), reference density (kg/m^3), scale height (km)
    private static final double[] H0 = {
            0, 25, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150,
            180, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000
    };
    private static final double[] RHO0 = {
            1.225, 3.899e-2, 1.774e-2, 3.972e-3, 1.057e-3, 3.206e-4, 8.770e-5,
            1.905e-5, 3.396e-6, 5.297e-7, 9.661e-8, 2.438e-8, 8.484e-9, 3.845e-9,
            2.070e-9, 5.464e-10, 2.789e-10, 7.248e-11, 2.418e-11, 9.518e-12,
            3.725e-12, 1.585e-12, 6.967e-13, 1.454e-13, 3.614e-14, 1.170e-14,
            5.245e-15, 3.019e-15
    };
    private static final double[] SCALE_H = {
            7.249, 6.349, 6.682, 7.554, 8.382, 7.714, 6.549, 5.799, 5.382, 5.877,
            7.263, 9.473, 12.636, 16.149, 22.523, 29.740, 37.105, 45.546, 53.628,
            53.298, 58.515, 60.828, 63.822, 71.835, 88.667, 124.64, 181.05, 268.00
    };

    private final OneAxisEllipsoid earth;

    public ExponentialAtmosphere(final OneAxisEllipsoid earth) {
        this.earth = earth;
    }

    /** Convenience constructor using a WGS84 Earth ellipsoid. */
    public ExponentialAtmosphere() {
        this(new OneAxisEllipsoid(Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING));
    }

    @Override
    public double getDensity(final AbsoluteDate date, final Vector3D positionBodyFixed) {
        final double hKm = earth.getAltitude(positionBodyFixed) / 1000.0;
        if (hKm <= 0.0) {
            return RHO0[0];
        }
        if (hKm >= H0[H0.length - 1]) {
            // beyond table: continue the last exponential layer
            final int last = H0.length - 1;
            return RHO0[last] * Math.exp(-(hKm - H0[last]) / SCALE_H[last]);
        }
        int idx = 0;
        for (int i = 0; i < H0.length - 1; i++) {
            if (hKm >= H0[i] && hKm < H0[i + 1]) {
                idx = i;
                break;
            }
        }
        return RHO0[idx] * Math.exp(-(hKm - H0[idx]) / SCALE_H[idx]);
    }

    @Override
    public String getName() {
        return "ExponentialAtmosphere";
    }
}
