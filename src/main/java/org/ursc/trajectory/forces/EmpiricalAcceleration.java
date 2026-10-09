package org.ursc.trajectory.forces;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Constant empirical acceleration expressed in the RTN (radial, transverse,
 * normal) local orbital frame, used to absorb un-modelled forces (e.g. residual
 * drag mismodelling or outgassing) in fit/estimation studies.
 *
 * <p>The three components are given in m/s^2 and converted to the inertial frame
 * using the instantaneous orbital basis.</p>
 */
public final class EmpiricalAcceleration implements ForceModel {

    private final double radial;
    private final double transverse;
    private final double normal;

    public EmpiricalAcceleration(final double radial, final double transverse, final double normal) {
        this.radial = radial;
        this.transverse = transverse;
        this.normal = normal;
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final PVCoordinates pv = state.getPVCoordinates();
        final Vector3D r = pv.getPosition();
        final Vector3D v = pv.getVelocity();

        final Vector3D radialDir = r.normalize();
        final Vector3D normalDir = r.crossProduct(v).normalize();  // orbit normal
        final Vector3D transverseDir = normalDir.crossProduct(radialDir); // completes the triad

        return radialDir.scalarMultiply(radial)
                .add(transverseDir.scalarMultiply(transverse))
                .add(normalDir.scalarMultiply(normal));
    }

    @Override
    public String getName() {
        return "EmpiricalAcceleration";
    }
}
