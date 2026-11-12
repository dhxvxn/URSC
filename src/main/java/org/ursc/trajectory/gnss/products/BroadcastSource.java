package org.ursc.trajectory.gnss.products;

import org.ursc.trajectory.gnss.GpsBroadcastEphemeris;
import org.ursc.trajectory.gnss.RinexNavReader;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.time.AbsoluteDate;

/** Broadcast GPS LNAV ephemerides as a {@link GnssOrbitClockSource} (lowest priority). */
public final class BroadcastSource implements GnssOrbitClockSource {

    /** Broadcast messages are fitted for about +/-2 h around Toe; beyond 4 h they are rejected. */
    public static final double MAX_AGE_S = 4.0 * 3600.0;

    private final RinexNavReader.Data nav;
    private final String description;

    public BroadcastSource(final RinexNavReader.Data nav, final String description) {
        this.nav = nav;
        this.description = description;
    }

    public RinexNavReader.Data data() {
        return nav;
    }

    @Override
    public ProductClass productClass() {
        return ProductClass.BROADCAST;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public GnssOrbitClockSource availableBy(final AbsoluteDate cutoff) {
        return new BroadcastSource(nav.availableBy(cutoff), description + " [messages with Toe <= cutoff]");
    }

    /** Broadcast eligibility is enforced per message ({@link RinexNavReader.Data#availableBy}). */
    @Override
    public AbsoluteDate issueTime() {
        return null;
    }

    @Override
    public boolean issueTimeVerified() {
        return false;
    }

    private GpsBroadcastEphemeris eph(final int prn, final AbsoluteDate t) {
        final GpsBroadcastEphemeris e = nav.ephemerisFor(prn, t);
        if (e == null || Math.abs(t.durationFrom(e.getToe())) > MAX_AGE_S) {
            return null;
        }
        return e;
    }

    @Override
    public boolean covers(final int prn, final AbsoluteDate t) {
        return eph(prn, t) != null;
    }

    @Override
    public Vector3D positionEcef(final int prn, final AbsoluteDate t) {
        return eph(prn, t).positionEcef(t);
    }

    @Override
    public double clockBias(final int prn, final AbsoluteDate t) {
        return eph(prn, t).clockBias(t);
    }
}
