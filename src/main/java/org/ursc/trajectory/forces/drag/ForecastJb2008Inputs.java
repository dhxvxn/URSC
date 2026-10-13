package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Forecast-mode JB2008 driver inputs: for genuine forward prediction you may only use information
 * available at the prediction epoch, never the observed future indices. This wrapper delegates to
 * an observed {@link Jb2008Inputs} up to an anchor epoch, and beyond it forecasts the drivers:
 *
 * <ul>
 *   <li><b>Solar indices</b> (F10.7, S10, M10, Y10 and their 81-day means) are held at their
 *       anchor-epoch values (<em>persistence</em>) — adequate over a ~week horizon, where solar
 *       flux changes little. (A longer horizon would blend toward a smoothed solar-cycle forecast
 *       such as MSAFE; that is a documented extension, not needed at this horizon.)</li>
 *   <li>The <b>geomagnetic storm</b> correction DTC is <em>not</em> forecastable deterministically
 *       days ahead, so it is set from a chosen <b>scenario</b> level (e.g. nominal/quiet vs a
 *       stressed/severe-storm value), producing a prediction band rather than a single value.</li>
 * </ul>
 */
public final class ForecastJb2008Inputs implements Jb2008Inputs {

    private final Jb2008Inputs observed;
    private final AbsoluteDate anchor;
    private final double forecastDtc;

    /**
     * @param observed the observed driver provider (used up to and including the anchor)
     * @param anchor the prediction epoch: data after it is forecast, not read from {@code observed}
     * @param forecastDtc the geomagnetic DTC (K) assumed for the forecast window (the scenario)
     */
    public ForecastJb2008Inputs(final Jb2008Inputs observed, final AbsoluteDate anchor,
                                final double forecastDtc) {
        this.observed = observed;
        this.anchor = anchor;
        this.forecastDtc = forecastDtc;
    }

    private boolean future(final AbsoluteDate date) {
        return date.durationFrom(anchor) > 0.0;
    }

    @Override public double getF10(final AbsoluteDate d)   { return observed.getF10(future(d) ? anchor : d); }
    @Override public double getF10B(final AbsoluteDate d)  { return observed.getF10B(future(d) ? anchor : d); }
    @Override public double getS10(final AbsoluteDate d)   { return observed.getS10(future(d) ? anchor : d); }
    @Override public double getS10B(final AbsoluteDate d)  { return observed.getS10B(future(d) ? anchor : d); }
    @Override public double getXM10(final AbsoluteDate d)  { return observed.getXM10(future(d) ? anchor : d); }
    @Override public double getXM10B(final AbsoluteDate d) { return observed.getXM10B(future(d) ? anchor : d); }
    @Override public double getY10(final AbsoluteDate d)   { return observed.getY10(future(d) ? anchor : d); }
    @Override public double getY10B(final AbsoluteDate d)  { return observed.getY10B(future(d) ? anchor : d); }

    @Override
    public double getDSTDTC(final AbsoluteDate d) {
        return future(d) ? forecastDtc : observed.getDSTDTC(d);
    }
}
