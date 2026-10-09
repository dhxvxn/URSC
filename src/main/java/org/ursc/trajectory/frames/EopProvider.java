package org.ursc.trajectory.frames;

import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Earth Orientation Parameters needed to realise the full GCRF&harr;ITRF
 * transform: UT1-UTC (for Earth rotation angle) and the polar-motion angles.
 * A {@link ConstantEop} with zeros gives the precession-nutation model without
 * sub-arcsecond EOP corrections; a file-backed provider (IERS finals2000A) is a
 * drop-in replacement.
 */
public interface EopProvider {

    /** @return UT1 - UTC (seconds). */
    double getUT1MinusUTC(AbsoluteDate date);

    /** @return polar motion x_p (radians). */
    double getXp(AbsoluteDate date);

    /** @return polar motion y_p (radians). */
    double getYp(AbsoluteDate date);
}
