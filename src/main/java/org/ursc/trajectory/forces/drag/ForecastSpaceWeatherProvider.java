package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Forecast-mode space weather for the MSIS-class models (F10.7 / 81-day F10.7 / Ap): delegates to
 * an observed {@link SpaceWeatherProvider} up to an anchor epoch, and beyond it forecasts the
 * drivers — solar flux by <em>persistence</em> of the anchor value (adequate over a ~week horizon),
 * and the geomagnetic index Ap from a chosen <b>scenario</b> level (nominal/quiet vs stressed),
 * because a storm is not deterministically predictable days ahead. This yields a prediction band
 * rather than a single deterministic value.
 */
public final class ForecastSpaceWeatherProvider implements SpaceWeatherProvider {

    private final SpaceWeatherProvider observed;
    private final AbsoluteDate anchor;
    private final double forecastAp;

    /**
     * @param observed observed provider (used up to and including the anchor)
     * @param anchor prediction epoch: data after it is forecast, not read from {@code observed}
     * @param forecastAp the daily Ap assumed for the forecast window (the scenario)
     */
    public ForecastSpaceWeatherProvider(final SpaceWeatherProvider observed, final AbsoluteDate anchor,
                                        final double forecastAp) {
        this.observed = observed;
        this.anchor = anchor;
        this.forecastAp = forecastAp;
    }

    private boolean future(final AbsoluteDate date) {
        return date.durationFrom(anchor) > 0.0;
    }

    @Override
    public double getDailyF107(final AbsoluteDate date) {
        return observed.getDailyF107(future(date) ? anchor : date);
    }

    @Override
    public double getAverageF107(final AbsoluteDate date) {
        return observed.getAverageF107(future(date) ? anchor : date);
    }

    @Override
    public double getDailyAp(final AbsoluteDate date) {
        return future(date) ? forecastAp : observed.getDailyAp(date);
    }
}
