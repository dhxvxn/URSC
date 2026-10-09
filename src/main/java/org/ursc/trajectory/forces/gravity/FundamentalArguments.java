package org.ursc.trajectory.forces.gravity;

import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * The six Doodson fundamental arguments used by the tide models:
 * {@code [tau, s, h, p, N', p_s]} (lunar time, Moon mean longitude, Sun mean
 * longitude, lunar perigee, negative lunar node, solar perigee), in radians.
 *
 * <p>Uses the standard mean-element polynomials (Simon et al. 1994 / IERS) to the
 * leading terms &mdash; ample for ocean-tide forcing, which is itself a tiny LEO
 * perturbation.</p>
 */
final class FundamentalArguments {

    private FundamentalArguments() {
    }

    /** @return the six Doodson arguments (radians), each normalised to [0, 2*pi). */
    static double[] doodson(final AbsoluteDate date) {
        final double t = date.julianCenturiesTT();

        // mean longitudes / anomalies (degrees)
        final double s = 218.31664563 + 481267.88194 * t - 0.0014663 * t * t;      // Moon
        final double h = 280.46645 + 36000.7697489 * t + 0.0003032 * t * t;        // Sun
        final double p = 83.35324312 + 4069.01363525 * t - 0.01032173 * t * t;     // Moon perigee
        final double omega = 125.04455501 - 1934.13626197 * t + 0.00207756 * t * t; // Moon node
        final double ps = 282.93734098 + 1.71945766 * t;                            // Sun perigee

        final double gmst = FramesFactory.gmst(date); // radians
        final double sRad = Math.toRadians(s);
        // tau = GMST + pi - s  (Doodson lunar time)
        final double tau = gmst + Math.PI - sRad;

        return new double[] {
                norm(tau),
                norm(sRad),
                norm(Math.toRadians(h)),
                norm(Math.toRadians(p)),
                norm(Math.toRadians(-omega)), // N' = -Omega
                norm(Math.toRadians(ps))
        };
    }

    private static double norm(final double a) {
        return Constants.normalizeAngleZeroTwoPi(a);
    }
}
