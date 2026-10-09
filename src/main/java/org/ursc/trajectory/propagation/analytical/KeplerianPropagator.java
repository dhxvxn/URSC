package org.ursc.trajectory.propagation.analytical;

import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.PositionAngle;
import org.ursc.trajectory.propagation.Propagator;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.time.AbsoluteDate;

/**
 * Analytical unperturbed (two-body) Keplerian propagator. The orbit shape stays
 * fixed and only the mean anomaly advances linearly with time. Useful as a fast
 * baseline and for verifying the numerical propagator in the force-free case.
 */
public final class KeplerianPropagator implements Propagator {

    private final SpacecraftState initialState;
    private final KeplerianOrbit initialOrbit;

    public KeplerianPropagator(final SpacecraftState initialState) {
        this.initialState = initialState;
        this.initialOrbit = (initialState.getOrbit() instanceof KeplerianOrbit)
                ? (KeplerianOrbit) initialState.getOrbit()
                : new KeplerianOrbit(initialState.getOrbit());
    }

    @Override
    public SpacecraftState getInitialState() {
        return initialState;
    }

    @Override
    public SpacecraftState propagate(final AbsoluteDate target) {
        final double dt = target.durationFrom(initialOrbit.getDate());
        final double meanMotion = initialOrbit.getKeplerianMeanMotion();
        final double newMeanAnomaly = initialOrbit.getMeanAnomaly() + meanMotion * dt;

        final KeplerianOrbit orbit = new KeplerianOrbit(
                initialOrbit.getA(), initialOrbit.getE(), initialOrbit.getI(),
                initialOrbit.getPerigeeArgument(), initialOrbit.getRightAscensionOfAscendingNode(),
                newMeanAnomaly, PositionAngle.MEAN,
                initialOrbit.getFrame(), target, initialOrbit.getMu());
        return new SpacecraftState(orbit, initialState.getMass());
    }
}
