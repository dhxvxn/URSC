package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Solar and geomagnetic driver inputs required by the {@link org.ursc.trajectory.forces.drag.jb2008.Jb2008
 * JB2008} atmosphere: the four solar indices (F10.7, S10, M10, Y10) with their 81-day centered
 * averages, plus the DTC geomagnetic-storm temperature correction. Each getter returns the value
 * already adjusted for the JB2008 tabular lags (F10/S10 = 1 day, M10 = 2 days, Y10 = 5 days) for
 * the given epoch.
 *
 * <p>This is intentionally separate from {@link SpaceWeatherProvider} (which carries only
 * F10.7/F10.7a/Ap for the MSIS-class models) so the simpler providers are unaffected.</p>
 */
public interface Jb2008Inputs {

    /** @param date epoch @return 10.7-cm solar flux (1-day lag). */
    double getF10(AbsoluteDate date);

    /** @param date epoch @return 81-day centered F10.7 (1-day lag). */
    double getF10B(AbsoluteDate date);

    /** @param date epoch @return EUV S10 index (1-day lag). */
    double getS10(AbsoluteDate date);

    /** @param date epoch @return 81-day centered S10 (1-day lag). */
    double getS10B(AbsoluteDate date);

    /** @param date epoch @return Mg II M10 index (2-day lag). */
    double getXM10(AbsoluteDate date);

    /** @param date epoch @return 81-day centered M10 (2-day lag). */
    double getXM10B(AbsoluteDate date);

    /** @param date epoch @return X-ray/Lya Y10 index (5-day lag). */
    double getY10(AbsoluteDate date);

    /** @param date epoch @return 81-day centered Y10 (5-day lag). */
    double getY10B(AbsoluteDate date);

    /** @param date epoch @return DTC geomagnetic-storm temperature correction (K). */
    double getDSTDTC(AbsoluteDate date);
}
