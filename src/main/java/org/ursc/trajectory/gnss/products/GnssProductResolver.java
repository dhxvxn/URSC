package org.ursc.trajectory.gnss.products;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ursc.trajectory.gnss.products.GnssOrbitClockSource.ProductClass;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Chooses the GPS orbit/clock source for each observation epoch, implementing
 * {@code docs/GNSS_PRODUCT_PRIORITY.md}:
 * <ol>
 * <li><b>Eligibility first.</b> In {@link Mode#FORECAST}, a precise product is eligible only
 *     if its issue time is known and not after the cutoff (and, under
 *     {@link Eligibility#REQUIRE_VERIFIED}, verified); broadcast messages are restricted to
 *     those with Toe at or before the cutoff. {@link Mode#RECONSTRUCTION} skips this and must
 *     be labelled retrospective.</li>
 * <li><b>Then priority:</b> Final, Rapid, Ultra-rapid, Broadcast; within a class the most
 *     recently issued product wins.</li>
 * <li><b>No mixing within an epoch:</b> one source serves a whole epoch (the first that covers
 *     at least {@code minSatellites} of the satellites in view), so orbit/clock conventions are
 *     never mixed inside a single SPP solve. Fallbacks are recorded with reasons.</li>
 * </ol>
 */
public final class GnssProductResolver {

    public enum Mode { FORECAST, RECONSTRUCTION }

    public enum Eligibility {
        /** Accept documented nominal publication latencies (reported as unverified). */
        NOMINAL_LATENCY,
        /** Accept only products with verified issue-time metadata. */
        REQUIRE_VERIFIED
    }

    /** The source chosen for one epoch, with the reason any higher-priority source was skipped. */
    public static final class Selection {
        public final GnssOrbitClockSource source;
        public final String fallbackReason;

        Selection(final GnssOrbitClockSource source, final String fallbackReason) {
            this.source = source;
            this.fallbackReason = fallbackReason;
        }
    }

    private final Mode mode;
    private final AbsoluteDate cutoff;
    private final List<GnssOrbitClockSource> eligible = new ArrayList<>();
    private final List<String> rejected = new ArrayList<>();
    private final Map<String, Integer> usage = new LinkedHashMap<>();
    private final Map<String, Integer> fallbacks = new LinkedHashMap<>();
    private final int minSatellites;

    /**
     * @param sources     every loaded product
     * @param mode        forecast (cutoff-eligible only) or reconstruction (best present)
     * @param cutoff      forecast cutoff (required in FORECAST mode)
     * @param eligibility issue-time evidence required in FORECAST mode
     * @param forced      restrict to one product class (comparison runs), or {@code null}
     * @param minSatellites satellites a source must cover for an epoch (normally 4)
     */
    public GnssProductResolver(final Collection<? extends GnssOrbitClockSource> sources, final Mode mode,
                               final AbsoluteDate cutoff, final Eligibility eligibility,
                               final ProductClass forced, final int minSatellites) {
        if (mode == Mode.FORECAST && cutoff == null) {
            throw new IllegalArgumentException("forecast mode requires a cutoff");
        }
        this.mode = mode;
        this.cutoff = cutoff;
        this.minSatellites = minSatellites;
        for (final GnssOrbitClockSource s : sources) {
            if (forced != null && s.productClass() != forced) {
                rejected.add(s.description() + ": excluded (forced product class " + forced.label + ")");
                continue;
            }
            if (mode == Mode.FORECAST) {
                if (s.productClass() == GnssOrbitClockSource.ProductClass.BROADCAST) {
                    eligible.add(s.availableBy(cutoff));
                    continue;
                }
                if (s.issueTime() == null) {
                    rejected.add(s.description() + ": excluded (issue time unknown; cannot prove availability by cutoff)");
                    continue;
                }
                if (eligibility == Eligibility.REQUIRE_VERIFIED && !s.issueTimeVerified()) {
                    rejected.add(s.description() + ": excluded (issue time is a nominal-latency assumption, verified metadata required)");
                    continue;
                }
                if (s.issueTime().isAfter(cutoff)) {
                    rejected.add(s.description() + ": excluded (issued " + s.issueTime()
                            + (s.issueTimeVerified() ? "" : " nominal") + ", after cutoff " + cutoff + ")");
                    continue;
                }
            }
            eligible.add(s);
        }
        eligible.sort((a, b) -> {
            final int c = Integer.compare(a.productClass().rank, b.productClass().rank);
            if (c != 0) {
                return c;
            }
            if (a.issueTime() != null && b.issueTime() != null) {
                return b.issueTime().compareTo(a.issueTime()); // fresher first
            }
            return 0;
        });
    }

    public Mode mode() {
        return mode;
    }

    public AbsoluteDate cutoff() {
        return cutoff;
    }

    /** Eligible sources in priority order. */
    public List<GnssOrbitClockSource> eligible() {
        return Collections.unmodifiableList(eligible);
    }

    /** Products excluded before selection, with reasons. */
    public List<String> rejected() {
        return Collections.unmodifiableList(rejected);
    }

    /** Epochs served per source description. */
    public Map<String, Integer> usage() {
        return Collections.unmodifiableMap(usage);
    }

    /** Fallback reasons and how many epochs each occurred. */
    public Map<String, Integer> fallbacks() {
        return Collections.unmodifiableMap(fallbacks);
    }

    /**
     * Pick the source for an epoch.
     *
     * @param t    observation epoch (GPS)
     * @param prns satellites in view
     * @return the selection, or {@code null} if no eligible source covers enough satellites
     */
    public Selection select(final AbsoluteDate t, final Collection<Integer> prns) {
        final StringBuilder why = new StringBuilder();
        for (final GnssOrbitClockSource s : eligible) {
            int n = 0;
            for (final int prn : prns) {
                if (s.covers(prn, t)) {
                    n++;
                }
            }
            if (n >= minSatellites) {
                usage.merge(s.description(), 1, Integer::sum);
                final String reason = why.length() == 0 ? null : why.toString();
                if (reason != null) {
                    fallbacks.merge(reason, 1, Integer::sum);
                }
                return new Selection(s, reason);
            }
            if (why.length() > 0) {
                why.append("; ");
            }
            why.append(s.productClass().label).append(" covers ").append(n).append(" of ")
                    .append(prns.size()).append(" satellites");
        }
        fallbacks.merge("no eligible source: " + why, 1, Integer::sum);
        return null;
    }
}
