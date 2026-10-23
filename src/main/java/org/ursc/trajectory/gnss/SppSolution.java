package org.ursc.trajectory.gnss;

import org.ursc.trajectory.math.Vector3D;

/**
 * Result of a single-epoch Single Point Positioning solve: the estimated receiver
 * position and clock offset, fit diagnostics, and dilution-of-precision figures.
 */
public final class SppSolution {

    private final Vector3D position;
    private final double clockBias;
    private final int usedSatellites;
    private final int iterations;
    private final boolean converged;
    private final double postFitRms;
    private final double gdop;
    private final double pdop;
    private final double tdop;
    private final double[] residuals;

    SppSolution(final Vector3D position, final double clockBias, final int usedSatellites,
                final int iterations, final boolean converged, final double postFitRms,
                final double gdop, final double pdop, final double tdop, final double[] residuals) {
        this.position = position;
        this.clockBias = clockBias;
        this.usedSatellites = usedSatellites;
        this.iterations = iterations;
        this.converged = converged;
        this.postFitRms = postFitRms;
        this.gdop = gdop;
        this.pdop = pdop;
        this.tdop = tdop;
        this.residuals = residuals;
    }

    /** @return estimated receiver position (m) in the input Earth-fixed frame. */
    public Vector3D getPosition() {
        return position;
    }

    /** @return estimated receiver clock offset expressed as a range (m); {@code c * dt_rcv}. */
    public double getClockBias() {
        return clockBias;
    }

    /** @return estimated receiver clock offset (s). */
    public double getClockBiasSeconds() {
        return clockBias / GpsConstants.SPEED_OF_LIGHT;
    }

    /** @return number of satellites used in the solve. */
    public int getUsedSatellites() {
        return usedSatellites;
    }

    public int getIterations() {
        return iterations;
    }

    public boolean isConverged() {
        return converged;
    }

    /** @return root-mean-square of the post-fit pseudorange residuals (m). */
    public double getPostFitRms() {
        return postFitRms;
    }

    /** @return geometric dilution of precision (position + clock). */
    public double getGdop() {
        return gdop;
    }

    /** @return position dilution of precision. */
    public double getPdop() {
        return pdop;
    }

    /** @return time (clock) dilution of precision. */
    public double getTdop() {
        return tdop;
    }

    /** @return post-fit pseudorange residuals (m), one per used satellite, in input order. */
    public double[] getResiduals() {
        return residuals.clone();
    }
}
