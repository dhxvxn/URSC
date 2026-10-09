package org.ursc.trajectory.propagation.events;

import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Detects entry into and exit from the Earth umbra. The switching function is the
 * angular elevation of the Sun's centre above the Earth's limb as seen from the
 * spacecraft: positive in sunlight, negative in shadow.
 */
public final class EclipseDetector implements EventDetector {

    private final CelestialBody sun;
    private final double occultingRadius;
    private final Action action;

    public EclipseDetector(final CelestialBody sun, final Action action) {
        this.sun = sun;
        this.occultingRadius = Constants.EARTH_EQUATORIAL_RADIUS;
        this.action = action;
    }

    @Override
    public double g(final SpacecraftState state) {
        final Vector3D r = state.getPosition();
        final Vector3D satToSun = sun.getPosition(state.getDate(), state.getFrame()).subtract(r);
        final Vector3D satToEarth = r.negate();

        final double rNorm = r.getNorm();
        final double aEarth = Math.asin(Math.min(1.0, occultingRadius / rNorm));
        final double separation = Vector3D.angle(satToSun, satToEarth);
        return separation - aEarth;
    }

    @Override
    public Action eventOccurred(final SpacecraftState state, final boolean increasing) {
        return action;
    }

    @Override
    public double getMaxCheckInterval() {
        return 60.0;
    }
}
