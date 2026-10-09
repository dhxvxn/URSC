package org.ursc.trajectory.forces;

import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Gravitational perturbation from a third body (Sun, Moon or a planet). Uses the
 * standard third-body formulation with the indirect (central-body acceleration)
 * term:
 * <pre>
 *   a = GM_body * [ (s - r) / |s - r|^3  -  s / |s|^3 ]
 * </pre>
 * where {@code r} is the satellite position and {@code s} the body position,
 * both geocentric in the inertial frame.
 */
public final class ThirdBodyAttraction implements ForceModel {

    private final CelestialBody body;

    public ThirdBodyAttraction(final CelestialBody body) {
        this.body = body;
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final Vector3D r = state.getPosition();
        final Vector3D s = body.getPosition(state.getDate(), state.getFrame());

        final Vector3D d = s.subtract(r);
        final double dCubed = Math.pow(d.getNorm(), 3);
        final double sCubed = Math.pow(s.getNorm(), 3);

        final Vector3D direct = d.scalarMultiply(1.0 / dCubed);
        final Vector3D indirect = s.scalarMultiply(1.0 / sCubed);
        return direct.subtract(indirect).scalarMultiply(body.getMu());
    }

    @Override
    public String getName() {
        return "ThirdBody(" + body.getName() + ")";
    }
}
