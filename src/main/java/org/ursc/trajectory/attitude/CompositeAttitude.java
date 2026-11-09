package org.ursc.trajectory.attitude;

import java.util.List;

import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Measured attitude where a quaternion file covers the date (and, for cutoff-safe forecasts,
 * only up to the cutoff); otherwise a fallback law such as {@link NominalYawSteering}.
 */
public final class CompositeAttitude implements AttitudeProvider {

    private final List<QuaternionFileAttitude> measured;
    private final AttitudeProvider fallback;
    private final AbsoluteDate lastUsable;

    /**
     * @param measured   measured attitude files (searched in order)
     * @param fallback   law used outside measured coverage
     * @param lastUsable measured attitude is ignored after this date ({@code null} = no limit)
     */
    public CompositeAttitude(final List<QuaternionFileAttitude> measured, final AttitudeProvider fallback,
                             final AbsoluteDate lastUsable) {
        this.measured = List.copyOf(measured);
        this.fallback = fallback;
        this.lastUsable = lastUsable;
    }

    @Override
    public BodyFrame attitude(final SpacecraftState state) {
        final AbsoluteDate d = state.getDate();
        if (lastUsable == null || !d.isAfter(lastUsable)) {
            for (final QuaternionFileAttitude q : measured) {
                if (q.covers(d)) {
                    return q.attitude(state);
                }
            }
        }
        return fallback.attitude(state);
    }
}
