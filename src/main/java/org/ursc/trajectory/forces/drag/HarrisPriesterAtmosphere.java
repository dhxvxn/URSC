package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Modified Harris-Priester atmospheric density model (Montenbruck &amp; Gill,
 * <i>Satellite Orbits</i> section 3.5.1). It captures the diurnal density bulge
 * that trails the sub-solar point, interpolating between a minimum (antapex) and
 * maximum (apex) density profile:
 * <pre>
 *   rho(h, psi) = rho_min(h) + (rho_max(h) - rho_min(h)) * cos^n(psi/2)
 * </pre>
 * where {@code psi} is the angle from the diurnal bulge apex and {@code n} is an
 * exponent (2 for near-equatorial orbits, up to 6 for polar orbits).
 */
public final class HarrisPriesterAtmosphere implements Atmosphere {

    private static final double[] ALT_KM = {
            100, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250,
            260, 270, 280, 290, 300, 320, 340, 360, 380, 400, 420, 440, 460, 480, 500,
            520, 540, 560, 580, 600, 620, 640, 660, 680, 700, 720, 740, 760, 780, 800,
            840, 880, 920, 960, 1000
    };
    // minimum (antapex) density, g/km^3
    private static final double[] RHO_MIN = {
            497400.0, 24900.0, 8377.0, 3899.0, 2122.0, 1263.0, 800.8, 528.3, 361.7,
            255.7, 183.9, 134.1, 99.49, 74.88, 57.09, 44.03, 34.30, 26.97, 21.39,
            17.08, 10.99, 7.214, 4.824, 3.274, 2.249, 1.558, 1.091, 0.7701, 0.5474,
            0.3916, 0.2819, 0.2042, 0.1488, 0.1092, 0.08070, 0.06012, 0.04519,
            0.03430, 0.02616, 0.02011, 0.01560, 0.01219, 0.009604, 0.007627,
            0.006097, 0.003931, 0.002578, 0.001724, 0.001175, 0.0008196
    };
    // maximum (apex) density, g/km^3
    private static final double[] RHO_MAX = {
            497400.0, 24900.0, 8710.0, 4059.0, 2215.0, 1344.0, 875.8, 601.0, 429.7,
            316.2, 239.6, 185.5, 145.5, 115.7, 92.77, 74.96, 61.14, 50.46, 41.89,
            34.95, 23.70, 16.32, 11.41, 8.079, 5.741, 4.111, 2.971, 2.162, 1.586,
            1.170, 0.8703, 0.6502, 0.4895, 0.3711, 0.2821, 0.2160, 0.1665, 0.1288,
            0.1005, 0.07878, 0.06200, 0.04904, 0.03897, 0.03111, 0.02492, 0.01617,
            0.01064, 0.007100, 0.004800, 0.003300
    };

    private static final double G_PER_KM3_TO_KG_PER_M3 = 1.0e-12;

    private final OneAxisEllipsoid earth;
    private final CelestialBody sun;
    private final double bulgeLag;   // rad, eastward lag of the bulge apex from the Sun
    private final double exponent;   // n in cos^n(psi/2)

    public HarrisPriesterAtmosphere(final OneAxisEllipsoid earth, final CelestialBody sun,
                                    final double exponent) {
        this.earth = earth;
        this.sun = sun;
        this.bulgeLag = Math.toRadians(30.0);
        this.exponent = exponent;
    }

    /** Convenience constructor: WGS84 Earth, built-in Sun, exponent n = 2. */
    public HarrisPriesterAtmosphere() {
        this(new OneAxisEllipsoid(Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING),
                CelestialBodyFactory.getSun(), 2.0);
    }

    @Override
    public double getDensity(final AbsoluteDate date, final Vector3D positionBodyFixed) {
        final double hKm = earth.getAltitude(positionBodyFixed) / 1000.0;
        if (hKm <= ALT_KM[0]) {
            return RHO_MIN[0] * G_PER_KM3_TO_KG_PER_M3;
        }
        if (hKm >= ALT_KM[ALT_KM.length - 1]) {
            return RHO_MIN[ALT_KM.length - 1] * G_PER_KM3_TO_KG_PER_M3;
        }

        int i = 0;
        for (int k = 0; k < ALT_KM.length - 1; k++) {
            if (hKm >= ALT_KM[k] && hKm < ALT_KM[k + 1]) {
                i = k;
                break;
            }
        }

        final double hMin = (ALT_KM[i + 1] - ALT_KM[i]) / Math.log(RHO_MIN[i] / RHO_MIN[i + 1]);
        final double hMax = (ALT_KM[i + 1] - ALT_KM[i]) / Math.log(RHO_MAX[i] / RHO_MAX[i + 1]);
        final double rhoMin = RHO_MIN[i] * Math.exp((ALT_KM[i] - hKm) / hMin);
        final double rhoMax = RHO_MAX[i] * Math.exp((ALT_KM[i] - hKm) / hMax);

        // diurnal bulge apex direction in the Earth-fixed frame
        final Vector3D sunBodyFixed = FramesFactory
                .getTransform(FramesFactory.getGCRF(), FramesFactory.getITRF(), date)
                .transformPosition(sun.getPosition(date, FramesFactory.getGCRF()));
        final double raSun = Math.atan2(sunBodyFixed.getY(), sunBodyFixed.getX());
        final double decSun = Math.asin(sunBodyFixed.getZ() / sunBodyFixed.getNorm());
        final double raApex = raSun + bulgeLag;
        final Vector3D apex = new Vector3D(
                Math.cos(decSun) * Math.cos(raApex),
                Math.cos(decSun) * Math.sin(raApex),
                Math.sin(decSun));

        final double cosPsi = positionBodyFixed.normalize().dotProduct(apex);
        final double cosPsiHalfPow = Math.pow(Math.max(0.0, (1.0 + cosPsi) / 2.0), exponent / 2.0);

        final double rhoGPerKm3 = rhoMin + (rhoMax - rhoMin) * cosPsiHalfPow;
        return rhoGPerKm3 * G_PER_KM3_TO_KG_PER_M3;
    }

    @Override
    public String getName() {
        return "HarrisPriesterAtmosphere(n=" + exponent + ")";
    }
}
