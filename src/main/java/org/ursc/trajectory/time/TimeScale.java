package org.ursc.trajectory.time;

/**
 * A time scale, i.e. a way of reading a physical instant as a count of seconds.
 *
 * <p>Following the convention used by Orekit, every scale is defined by its
 * offset from International Atomic Time (TAI):
 * {@code reading(scale) = reading(TAI) + offsetFromTAI}.</p>
 */
public interface TimeScale {

    /**
     * Offset, in SI seconds, to add to the TAI reading to obtain this scale's
     * reading at the given instant. For uniform scales this is a constant; for
     * UTC it is the (negated) accumulated leap-second count.
     *
     * @param date the instant at which the offset is evaluated
     * @return the offset in seconds
     */
    double offsetFromTAI(AbsoluteDate date);

    /** @return the short name of the scale (e.g. "UTC", "TT"). */
    String getName();
}
