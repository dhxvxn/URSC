package org.ursc.trajectory.frames;

import org.ursc.trajectory.math.RotationMatrix;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * IAU-2006/2000B precession-nutation and Earth rotation, giving the full
 * GCRF&rarr;ITRF rotation (bias + precession + nutation + Earth rotation + polar
 * motion).
 *
 * <p>Precession uses the IAU-2006 Fukushima-Williams angles; nutation uses the
 * IAU-2000B 77-term luni-solar series (accuracy ~1 mas, ample for LEO). Earth
 * rotation uses the Earth Rotation Angle (with UT1 from the {@link EopProvider}),
 * the Greenwich Apparent Sidereal Time with the equation of the equinoxes, and a
 * small-angle polar-motion matrix. Algorithm follows the SOFA reference routines;
 * coefficients are reproduced in {@link Nutation2000BData}.</p>
 */
final class Iau2006 {

    private static final double DAS2R = Math.PI / 648000.0;   // arcsec -> rad
    private static final double U2R = DAS2R / 1.0e7;          // 0.1 uas -> rad
    private static final double TWO_PI = 2.0 * Math.PI;

    // IAU-2000B fixed planetary-bias offsets (arcsec)
    private static final double DPPLAN = -0.000135 * DAS2R;
    private static final double DEPLAN = 0.000388 * DAS2R;

    private Iau2006() {
    }

    /** GCRF &rarr; ITRF rotation at the given date using the supplied EOP. */
    static RotationMatrix gcrfToItrf(final AbsoluteDate date, final EopProvider eop) {
        final double t = date.julianCenturiesTT();

        final double[] nut = nutation2000B(t);
        final double dpsi = nut[0];
        final double deps = nut[1];
        final double epsa = meanObliquity(t);

        final RotationMatrix npb = fukushimaWilliams(t, dpsi, deps);
        final double gast = gast(date, eop, dpsi, epsa, t);
        final RotationMatrix rot = RotationMatrix.rotationZ(gast).multiply(npb); // GCRF -> PEF
        final RotationMatrix w = polarMotion(eop.getXp(date), eop.getYp(date));  // PEF -> ITRF
        return w.multiply(rot);
    }

    /** Mean obliquity of the ecliptic, IAU 2006 (radians). */
    static double meanObliquity(final double t) {
        final double arcsec = 84381.406
                + (-46.836769
                + (-0.0001831
                + (0.00200340
                + (-0.000000576
                + (-0.0000000434) * t) * t) * t) * t) * t;
        return arcsec * DAS2R;
    }

    /** GCRF &rarr; true-of-date rotation from the Fukushima-Williams angles. */
    private static RotationMatrix fukushimaWilliams(final double t, final double dpsi,
                                                    final double deps) {
        final double gamb = (-0.052928
                + (10.556378
                + (0.4932044
                + (-0.00031238
                + (-0.000002788
                + (0.0000000260) * t) * t) * t) * t) * t) * DAS2R;
        final double phib = (84381.412819
                + (-46.811016
                + (0.0511268
                + (0.00053289
                + (-0.000000440
                + (-0.0000000176) * t) * t) * t) * t) * t) * DAS2R;
        final double psib = (-0.041775
                + (5038.481484
                + (1.5584175
                + (-0.00018522
                + (-0.000026452
                + (-0.0000000148) * t) * t) * t) * t) * t) * DAS2R;
        final double epsa = meanObliquity(t);

        final double psi = psib + dpsi;
        final double eps = epsa + deps;
        // NPB = Rx(-eps) Rz(-psi) Rx(phib) Rz(gamb)   (SOFA iau_FW2M)
        return RotationMatrix.rotationX(-eps)
                .multiply(RotationMatrix.rotationZ(-psi))
                .multiply(RotationMatrix.rotationX(phib))
                .multiply(RotationMatrix.rotationZ(gamb));
    }

    /** IAU-2000B nutation in longitude and obliquity (radians): {dpsi, deps}. */
    static double[] nutation2000B(final double t) {
        // fundamental (Delaunay) arguments, arcsec -> rad (SOFA nut00b truncation)
        final double el = (485868.249036 + 1717915923.2178 * t) * DAS2R;
        final double elp = (1287104.79305 + 129596581.0481 * t) * DAS2R;
        final double f = (335779.526232 + 1739527262.8478 * t) * DAS2R;
        final double d = (1072260.70369 + 1602961601.2090 * t) * DAS2R;
        final double om = (450160.398036 - 6962890.5431 * t) * DAS2R;

        double dp = 0.0;
        double de = 0.0;
        // summed in reverse order (small terms first) as in SOFA
        for (int i = Nutation2000BData.TERMS - 1; i >= 0; i--) {
            final int[] m = Nutation2000BData.MULT[i];
            final double[] c = Nutation2000BData.COEF[i];
            final double arg = m[0] * el + m[1] * elp + m[2] * f + m[3] * d + m[4] * om;
            final double sarg = Math.sin(arg);
            final double carg = Math.cos(arg);
            dp += (c[0] + c[1] * t) * sarg + c[2] * carg;
            de += (c[3] + c[4] * t) * carg + c[5] * sarg;
        }
        return new double[] {dp * U2R + DPPLAN, de * U2R + DEPLAN};
    }

    /** Greenwich Apparent Sidereal Time (radians). */
    private static double gast(final AbsoluteDate date, final EopProvider eop,
                               final double dpsi, final double epsa, final double t) {
        final double era = era(date, eop);
        // precession in right ascension (IAU 2006 GMST polynomial), arcsec
        final double gmst = era + (0.014506
                + (4612.156534
                + (1.3915817
                + (-0.00000044
                + (-0.000029956
                + (-0.0000000368) * t) * t) * t) * t) * t) * DAS2R;
        // equation of the equinoxes: leading term + two complementary terms
        final double om = (450160.398036 - 6962890.5431 * t) * DAS2R;
        final double ee = dpsi * Math.cos(epsa)
                + 0.00264096 * DAS2R * Math.sin(om)
                + 0.00006352 * DAS2R * Math.sin(2.0 * om);
        return gmst + ee;
    }

    /** Earth Rotation Angle (radians) from UT1. */
    private static double era(final AbsoluteDate date, final EopProvider eop) {
        final double jdUt1 = date.julianDate(TimeScalesFactory.getUTC())
                + eop.getUT1MinusUTC(date) / 86400.0;
        final double tu = jdUt1 - 2451545.0;
        final double frac = fractionalPart(0.7790572732640 + 1.00273781191135448 * tu);
        return normalize(TWO_PI * frac);
    }

    /** Polar-motion matrix PEF/TIRS &rarr; ITRF (small angles; identity when xp=yp=0). */
    private static RotationMatrix polarMotion(final double xp, final double yp) {
        // SOFA iau_POM00 with s' = 0:  W = Rx(-yp) Ry(-xp)
        return RotationMatrix.rotationX(-yp).multiply(RotationMatrix.rotationY(-xp));
    }

    private static double fractionalPart(final double x) {
        return x - Math.floor(x);
    }

    private static double normalize(final double a) {
        double r = a % TWO_PI;
        if (r < 0) {
            r += TWO_PI;
        }
        return r;
    }
}
