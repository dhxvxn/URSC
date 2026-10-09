package org.ursc.trajectory.frames;

import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.RotationMatrix;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Access point for the predefined frames and the transforms between them.
 *
 * <p>The celestial frame {@link #getGCRF()} is pseudo-inertial and is where
 * propagation is performed. The Earth-fixed frame {@link #getITRF()} co-rotates
 * with the Earth. The GCRF&rarr;ITRF transform is currently modelled as a single
 * rotation about the celestial pole by the Greenwich Mean Sidereal Time, with the
 * Earth's rotation rate as the angular velocity. Precession, nutation and polar
 * motion are neglected (sub-km over the long arcs typical of LEO lifetime
 * studies); they can be layered in as extra transforms without changing callers.</p>
 */
public final class FramesFactory {

    /** How the GCRF&harr;ITRF transform is realised. */
    public enum Model {
        /** Sidereal rotation only (GMST); fast, no precession/nutation. */
        SIMPLE,
        /** Full IAU-2006 precession + IAU-2000B nutation + ERA/GAST + polar motion. */
        IAU_2006
    }

    private static final Frame GCRF = new Frame("GCRF", true);
    private static final Frame ITRF = new Frame("ITRF", false);

    private static final Vector3D EARTH_ANGULAR_VELOCITY =
            new Vector3D(0.0, 0.0, Constants.EARTH_ROTATION_RATE);

    private static volatile Model model = Model.IAU_2006;
    private static volatile EopProvider eop = ConstantEop.ZERO;

    private FramesFactory() {
    }

    /** Select the frame model (default {@link Model#IAU_2006}). */
    public static void setModel(final Model m) {
        model = m;
    }

    public static Model getModel() {
        return model;
    }

    /** Inject Earth Orientation Parameters (default {@link ConstantEop#ZERO}). */
    public static void setEopProvider(final EopProvider provider) {
        eop = provider;
    }

    public static EopProvider getEopProvider() {
        return eop;
    }

    /** @return the pseudo-inertial geocentric celestial reference frame. */
    public static Frame getGCRF() {
        return GCRF;
    }

    /** @return the Earth-fixed international terrestrial reference frame. */
    public static Frame getITRF() {
        return ITRF;
    }

    /**
     * Greenwich Mean Sidereal Time, IAU-82 polynomial, using UT1 &asymp; UTC.
     *
     * @param date the instant
     * @return GMST in radians, in [0, 2*pi)
     */
    public static double gmst(final AbsoluteDate date) {
        final double jdUt1 = date.julianDate(TimeScalesFactory.getUT1());
        final double d = jdUt1 - 2451545.0;
        final double t = d / 36525.0;
        double gmstDeg = 280.46061837
                + 360.98564736629 * d
                + 0.000387933 * t * t
                - t * t * t / 38710000.0;
        final double gmstRad = Math.toRadians(gmstDeg);
        return Constants.normalizeAngleZeroTwoPi(gmstRad);
    }

    /**
     * Build the transform converting vectors from {@code from} to {@code to} at
     * the given date. Only GCRF&harr;ITRF pairs (and identity) are supported.
     */
    public static Transform getTransform(final Frame from, final Frame to, final AbsoluteDate date) {
        if (from == to) {
            return new Transform(RotationMatrix.IDENTITY, Vector3D.ZERO);
        }
        if (from == GCRF && to == ITRF) {
            final RotationMatrix rotation;
            if (model == Model.IAU_2006) {
                rotation = Iau2006.gcrfToItrf(date, eop);
            } else {
                rotation = RotationMatrix.rotationZ(gmst(date));
            }
            // ITRF angular velocity relative to GCRF, expressed in ITRF axes (+Z).
            // Precession/nutation rates (~1e-12 rad/s) are negligible beside Earth spin.
            return new Transform(rotation, EARTH_ANGULAR_VELOCITY);
        }
        if (from == ITRF && to == GCRF) {
            return getTransform(GCRF, ITRF, date).getInverse();
        }
        throw new IllegalArgumentException(
                "no transform defined between " + from + " and " + to);
    }
}
