package org.ursc.trajectory.gnss.products;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * A source of GPS satellite orbits and clocks for Single Point Positioning: an IGS precise
 * product (Final, Rapid, Ultra-rapid) or the broadcast navigation message.
 *
 * <p>Conventions every implementation must honour so that sources are interchangeable:
 * positions are Earth-fixed (ITRF / IGS realisation), metres, at the requested GPS epoch;
 * clock offsets are seconds and <b>include</b> the periodic relativistic correction
 * (as the broadcast clock model does), so the SPP measurement model is identical for every
 * source. Clocks refer to the ionosphere-free P1/P2 combination.</p>
 */
public interface GnssOrbitClockSource {

    /** Product classes in the requested priority order (lower rank = preferred). */
    enum ProductClass {
        FINAL(1, "IGS Final"),
        RAPID(2, "IGS Rapid"),
        ULTRA_RAPID(3, "IGS Ultra-rapid"),
        BROADCAST(4, "Broadcast navigation");

        public final int rank;
        public final String label;

        ProductClass(final int rank, final String label) {
            this.rank = rank;
            this.label = label;
        }
    }

    ProductClass productClass();

    /** Human-readable identity (provider and file names) for provenance records. */
    String description();

    /**
     * Earliest time the product is known/assumed to have been available, used for forecast
     * cutoff eligibility; {@code null} if unknown.
     */
    AbsoluteDate issueTime();

    /** Whether {@code issueTime()} comes from verified metadata rather than a nominal latency. */
    boolean issueTimeVerified();

    /** @return true if both an orbit and a clock can be evaluated for {@code prn} at {@code t}. */
    boolean covers(int prn, AbsoluteDate t);

    /** Earth-fixed satellite position (m) at GPS epoch {@code t}. */
    Vector3D positionEcef(int prn, AbsoluteDate t);

    /** Satellite clock offset (s) at {@code t}, including the relativistic correction. */
    double clockBias(int prn, AbsoluteDate t);

    /**
     * Broadcast products: the same source restricted to messages with Toe at or before
     * {@code cutoff} (what a receiver could have had by then). Other products return themselves;
     * their availability is decided by the issue time.
     */
    default GnssOrbitClockSource availableBy(final AbsoluteDate cutoff) {
        return this;
    }

    /** @return true if the value at {@code t} comes from a predicted (not observed) segment. */
    default boolean isPredicted(final AbsoluteDate t) {
        return false;
    }
}
