package org.ursc.trajectory.propagation.events;

import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Detects crossing of a geodetic altitude threshold &mdash; typically used to
 * stop a propagation at atmospheric re-entry (the natural end of a LEO lifetime
 * study).
 */
public final class AltitudeDetector implements EventDetector {

    private final double targetAltitude;
    private final OneAxisEllipsoid earth;
    private final Action action;
    private final double maxCheck;

    public AltitudeDetector(final double targetAltitude, final OneAxisEllipsoid earth,
                            final Action action, final double maxCheck) {
        this.targetAltitude = targetAltitude;
        this.earth = earth;
        this.action = action;
        this.maxCheck = maxCheck;
    }

    @Override
    public double g(final SpacecraftState state) {
        final Vector3D rBody = FramesFactory
                .getTransform(state.getFrame(), FramesFactory.getITRF(), state.getDate())
                .transformPosition(state.getPosition());
        return earth.getAltitude(rBody) - targetAltitude;
    }

    @Override
    public Action eventOccurred(final SpacecraftState state, final boolean increasing) {
        return action;
    }

    @Override
    public double getMaxCheckInterval() {
        return maxCheck;
    }
}
