package org.ursc.trajectory.forces.drag;

import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Space weather held constant over the run. Convenient for sensitivity studies
 * (e.g. quiet vs. active Sun) and as a default when no space-weather file is
 * available. The reference "nominal" values are F10.7 = F10.7A = 150, Ap = 4.
 */
public final class ConstantSpaceWeather implements SpaceWeatherProvider {

    private final double f107;
    private final double f107A;
    private final double ap;

    public ConstantSpaceWeather(final double f107, final double f107A, final double ap) {
        this.f107 = f107;
        this.f107A = f107A;
        this.ap = ap;
    }

    /** Nominal quiet-to-moderate conditions (150, 150, 4). */
    public static ConstantSpaceWeather nominal() {
        return new ConstantSpaceWeather(150.0, 150.0, 4.0);
    }

    @Override
    public double getDailyF107(final AbsoluteDate date) {
        return f107;
    }

    @Override
    public double getAverageF107(final AbsoluteDate date) {
        return f107A;
    }

    @Override
    public double getDailyAp(final AbsoluteDate date) {
        return ap;
    }
}
