package org.ursc.trajectory.orekit;

import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinatesProvider;
import org.ursc.trajectory.gnss.products.GnssOrbitClockSource;

/**
 * A GPS orbit/clock product evaluated by Orekit. It is also a {@link GnssOrbitClockSource}, so the
 * program's product-tier and cutoff-eligibility logic ({@code GnssProductResolver}) selects
 * between Orekit-backed sources exactly as before.
 */
public interface OrekitGpsSource extends GnssOrbitClockSource {

    /** Orekit position/velocity provider of satellite {@code prn} (any frame, any date covered). */
    PVCoordinatesProvider provider(int prn);

    /** Satellite clock offset (s) at {@code t}, including the periodic relativistic correction. */
    double clock(int prn, AbsoluteDate t);

    /** Whether orbit and clock are available for {@code prn} at {@code t}. */
    boolean covers(int prn, AbsoluteDate t);

    // ---- the program-type view used by the product resolver and the SPP seed --------------

    @Override
    default boolean covers(final int prn, final org.ursc.trajectory.time.AbsoluteDate t) {
        return covers(prn, OrekitDates.toOrekit(t));
    }

    @Override
    default org.ursc.trajectory.math.Vector3D positionEcef(final int prn, final org.ursc.trajectory.time.AbsoluteDate t) {
        final AbsoluteDate d = OrekitDates.toOrekit(t);
        return OrekitDates.fromOrekit(provider(prn).getPosition(d, itrf()));
    }

    @Override
    default double clockBias(final int prn, final org.ursc.trajectory.time.AbsoluteDate t) {
        return clock(prn, OrekitDates.toOrekit(t));
    }

    /**
     * Whether the orbits refer to the satellite centre of mass (IGS precise products) rather
     * than to the antenna phase centre (broadcast ephemerides).
     */
    default boolean centreOfMassOrbits() {
        return false;
    }

    /** The Earth-fixed frame the product is expressed in. */
    org.orekit.frames.Frame itrf();
}
