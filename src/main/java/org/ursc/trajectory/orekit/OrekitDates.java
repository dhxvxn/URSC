package org.ursc.trajectory.orekit;

import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * Conversions at the boundary between the Orekit engine and the program's own lightweight value
 * types (used by the GUI, the data catalogue and the OEM writer). Both date types count SI
 * seconds on the uniform TAI time line from the J2000.0 (TT) epoch, so the conversion is exact
 * to the double-precision resolution (about 0.1 microsecond).
 */
public final class OrekitDates {

    private OrekitDates() {
    }

    public static org.orekit.time.AbsoluteDate toOrekit(final org.ursc.trajectory.time.AbsoluteDate d) {
        return org.orekit.time.AbsoluteDate.J2000_EPOCH.shiftedBy(d.durationFromJ2000());
    }

    public static org.ursc.trajectory.time.AbsoluteDate fromOrekit(final org.orekit.time.AbsoluteDate d) {
        return org.ursc.trajectory.time.AbsoluteDate.J2000_EPOCH
                .shiftedBy(d.durationFrom(org.orekit.time.AbsoluteDate.J2000_EPOCH));
    }

    public static Vector3D toOrekit(final org.ursc.trajectory.math.Vector3D v) {
        return new Vector3D(v.getX(), v.getY(), v.getZ());
    }

    public static org.ursc.trajectory.math.Vector3D fromOrekit(final Vector3D v) {
        return new org.ursc.trajectory.math.Vector3D(v.getX(), v.getY(), v.getZ());
    }
}
