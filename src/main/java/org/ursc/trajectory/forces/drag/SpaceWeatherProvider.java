package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Supplies the solar and geomagnetic activity indices that drive the
 * NRLMSISE-00 (and other) thermosphere models. Implement this to read CSSI/CelesTrak
 * space-weather files or a live feed; {@link ConstantSpaceWeather} provides fixed
 * values for quick studies.
 */
public interface SpaceWeatherProvider {

    /** @return daily F10.7 solar radio flux for the previous day (sfu). */
    double getDailyF107(AbsoluteDate date);

    /** @return 81-day average F10.7 centred on the date (sfu). */
    double getAverageF107(AbsoluteDate date);

    /** @return daily geomagnetic Ap index. */
    double getDailyAp(AbsoluteDate date);
}
