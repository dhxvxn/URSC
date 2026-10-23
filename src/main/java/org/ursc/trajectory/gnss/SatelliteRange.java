package org.ursc.trajectory.gnss;

import org.ursc.trajectory.math.Vector3D;

/**
 * One satellite's contribution to a single-epoch Single Point Positioning solve:
 * the transmitting satellite's Earth-fixed position, the measured pseudorange, and
 * the satellite clock offset, all resolved from ephemeris before the solve.
 *
 * <p>The satellite position must already be expressed in the <em>same</em>
 * Earth-fixed (ECEF/ITRF) frame as the receiver position being solved for, at the
 * signal reception epoch — i.e. the Earth-rotation (Sagnac) correction over the
 * signal transit time is the caller's responsibility, so the solver stays a pure
 * geometric least-squares step. The satellite clock offset {@code satClockBias}
 * follows the usual convention (positive means the satellite clock reads ahead of
 * system time); the solver adds {@code c * satClockBias} back onto the pseudorange.</p>
 */
public final class SatelliteRange {

    private final int prn;
    private final Vector3D satPosition;
    private final double pseudorange;
    private final double satClockBias;
    private final double weight;

    /**
     * @param prn          GPS PRN (satellite id), carried through for reporting
     * @param satPosition  satellite ECEF position (m) at transmit, in the receive-epoch frame
     * @param pseudorange  measured pseudorange (m); ionosphere-free combination for dual-frequency
     * @param satClockBias satellite clock offset (s); {@code c * satClockBias} is added to the pseudorange
     * @param weight       relative observation weight (&gt; 0); use 1.0 for equal weighting
     */
    public SatelliteRange(final int prn, final Vector3D satPosition, final double pseudorange,
                          final double satClockBias, final double weight) {
        if (weight <= 0.0) {
            throw new IllegalArgumentException("weight must be positive, got " + weight);
        }
        this.prn = prn;
        this.satPosition = satPosition;
        this.pseudorange = pseudorange;
        this.satClockBias = satClockBias;
        this.weight = weight;
    }

    /** Convenience constructor with unit weight. */
    public SatelliteRange(final int prn, final Vector3D satPosition, final double pseudorange,
                          final double satClockBias) {
        this(prn, satPosition, pseudorange, satClockBias, 1.0);
    }

    public int getPrn() {
        return prn;
    }

    public Vector3D getSatPosition() {
        return satPosition;
    }

    public double getPseudorange() {
        return pseudorange;
    }

    public double getSatClockBias() {
        return satClockBias;
    }

    public double getWeight() {
        return weight;
    }
}
