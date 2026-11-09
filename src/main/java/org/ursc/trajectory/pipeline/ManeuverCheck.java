package org.ursc.trajectory.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Flags orbit-determination fits whose residuals look like an unmodelled manoeuvre (or another
 * unmodelled force / bad data) inside the fitting arc. A thruster burn makes a single-arc fit
 * impossible, so the residuals become much larger than the measurement noise and grow
 * systematically towards the arc edges or jump abruptly. Heuristic: a flag is a warning to
 * inspect the residual plot, not a detection with a guaranteed false-alarm rate.
 */
public final class ManeuverCheck {

    /** Weighted variance factor above which residuals are ~3x the assumed noise. */
    static final double VARIANCE_FACTOR_LIMIT = 9.0;

    private ManeuverCheck() {
    }

    private static double median(final List<Double> v) {
        final List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        final int n = s.size();
        return n == 0 ? Double.NaN : n % 2 == 1 ? s.get(n / 2) : 0.5 * (s.get(n / 2 - 1) + s.get(n / 2));
    }

    public static List<String> assess(final RunResult.Od od, final double pseudorangeSigma) {
        final List<String> flags = new ArrayList<>();
        if (od == null || od.residualNorm.size() < 9) {
            return flags;
        }
        final List<Double> r = od.residualNorm;
        if (od.weighted && od.varianceFactor > VARIANCE_FACTOR_LIMIT) {
            flags.add(String.format(Locale.US, "OD residuals are %.1fx larger than the assumed %.1f m noise "
                    + "(variance factor %.1f): possible manoeuvre, bad data or unsuitable force settings in the arc.",
                    Math.sqrt(od.varianceFactor), pseudorangeSigma, od.varianceFactor));
        }
        final int n = r.size();
        final double first = median(r.subList(0, n / 3));
        final double mid = median(r.subList(n / 3, 2 * n / 3));
        final double last = median(r.subList(2 * n / 3, n));
        final double floor = Math.max(mid, 0.5 * pseudorangeSigma);
        if (last > 3 * floor || first > 3 * floor) {
            flags.add(String.format(Locale.US, "OD residuals grow towards the %s of the arc (median %.1f m vs %.1f m in "
                    + "the middle): typical of a manoeuvre near that edge. The forecast may be compromised.",
                    last > first ? "end" : "start", Math.max(first, last), mid));
        }
        // abrupt change: compare 5-epoch windows either side of each epoch
        final double robust = 1.4826 * median(absDev(r, median(r)));
        final int w = 5;
        double best = 0;
        int at = -1;
        for (int i = w; i + w <= n; i++) {
            double a = 0;
            double b = 0;
            for (int k = 0; k < w; k++) {
                a += r.get(i - 1 - k);
                b += r.get(i + k);
            }
            final double d = Math.abs(b - a) / w;
            if (d > best) {
                best = d;
                at = i;
            }
        }
        if (at > 0 && robust > 0 && best > 6 * robust && best > 2 * pseudorangeSigma) {
            flags.add(String.format(Locale.US, "Abrupt change in OD residuals near %s UTC (%.1f m): possible manoeuvre.",
                    od.residualUtc.get(at), best));
        }
        return flags;
    }

    private static List<Double> absDev(final List<Double> v, final double m) {
        final List<Double> o = new ArrayList<>();
        for (final double x : v) {
            o.add(Math.abs(x - m));
        }
        return o;
    }
}
