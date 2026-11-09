package org.ursc.trajectory.gnss.products;

import org.ursc.trajectory.gnss.GpsConstants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * An IGS precise product: SP3 orbits, with clocks from a matching RINEX CLK file when
 * supplied (preferred, 30 s/5 min) or else from the SP3 clock column. IGS clocks exclude
 * the periodic relativistic term, so {@code -2 r.v / c^2} is added here to match the
 * broadcast clock convention used by the SPP measurement model.
 *
 * <p>Note: IGS orbits refer to the satellite centre of mass and broadcast orbits to the
 * antenna phase centre (~1-2 m apart); this is below SPP code noise and not corrected.</p>
 */
public final class PreciseSource implements GnssOrbitClockSource {

    private final Sp3Reader.Data orbit;
    private final RinexClkReader.Data clock;
    private final ProductClass productClass;
    private final String description;
    private final AbsoluteDate issueTime;
    private final boolean issueVerified;
    private final AbsoluteDate observedEnd;

    /**
     * @param orbit         SP3 orbit (required)
     * @param clock         RINEX clock of the same product, or {@code null} to use SP3 clocks
     * @param productClass  Final / Rapid / Ultra-rapid
     * @param description   provider + file names for provenance
     * @param issueTime     publication time used for cutoff eligibility, or {@code null} if unknown
     * @param issueVerified whether {@code issueTime} is verified metadata (else nominal latency)
     * @param observedEnd   end of the observed segment (ultra-rapid), or {@code null}
     */
    public PreciseSource(final Sp3Reader.Data orbit, final RinexClkReader.Data clock,
                         final ProductClass productClass, final String description,
                         final AbsoluteDate issueTime, final boolean issueVerified,
                         final AbsoluteDate observedEnd) {
        if (productClass == ProductClass.BROADCAST) {
            throw new IllegalArgumentException("precise source cannot be BROADCAST");
        }
        this.orbit = orbit;
        this.clock = clock;
        this.productClass = productClass;
        this.description = description;
        this.issueTime = issueTime;
        this.issueVerified = issueVerified;
        this.observedEnd = observedEnd;
    }

    public Sp3Reader.Data orbit() {
        return orbit;
    }

    public RinexClkReader.Data clockData() {
        return clock;
    }

    @Override
    public ProductClass productClass() {
        return productClass;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public AbsoluteDate issueTime() {
        return issueTime;
    }

    @Override
    public boolean issueTimeVerified() {
        return issueVerified;
    }

    private double rawClock(final int prn, final AbsoluteDate t) {
        if (clock != null) {
            return clock.clock(prn, t);
        }
        return orbit.clock(prn, t);
    }

    @Override
    public boolean covers(final int prn, final AbsoluteDate t) {
        return orbit.coversOrbit(prn, t) && orbit.coversOrbit(prn, t.shiftedBy(-1.0))
                && orbit.coversOrbit(prn, t.shiftedBy(1.0)) && !Double.isNaN(rawClock(prn, t));
    }

    @Override
    public Vector3D positionEcef(final int prn, final AbsoluteDate t) {
        return orbit.position(prn, t);
    }

    @Override
    public double clockBias(final int prn, final AbsoluteDate t) {
        final double c = GpsConstants.SPEED_OF_LIGHT;
        final Vector3D r = orbit.position(prn, t);
        final Vector3D v = orbit.position(prn, t.shiftedBy(0.5))
                .subtract(orbit.position(prn, t.shiftedBy(-0.5)));   // central difference, 1 s
        final double relativistic = -2.0 * r.dotProduct(v) / (c * c);
        return rawClock(prn, t) + relativistic;
    }

    @Override
    public boolean isPredicted(final AbsoluteDate t) {
        return observedEnd != null && t.isAfter(observedEnd);
    }
}
