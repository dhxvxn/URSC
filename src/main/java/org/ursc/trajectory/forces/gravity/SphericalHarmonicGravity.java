package org.ursc.trajectory.forces.gravity;

import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Earth gravitational attraction from a spherical-harmonic expansion, including
 * the central (point-mass) term. The acceleration is evaluated in the Earth-fixed
 * frame &mdash; where the field is static &mdash; and rotated back to the inertial
 * frame.
 *
 * <p>Implements the Cunningham/Gottlieb recursion for the normalised-free
 * potential derivatives exactly as given by Montenbruck &amp; Gill,
 * <i>Satellite Orbits</i> (section 3.2.4, eqs 3.29-3.33). The expansion can be
 * truncated independently in degree and order, so a user can ask for e.g. a
 * zonal-only J2-J6 field (order 0) or a full tesseral field.</p>
 */
public final class SphericalHarmonicGravity implements ForceModel {

    private final GravityField field;
    private final int degree;
    private final int order;
    private final Frame bodyFrame;

    /**
     * @param field  the gravity field coefficients
     * @param degree maximum degree to use (&le; field max degree)
     * @param order  maximum order to use (&le; degree and field max order)
     */
    public SphericalHarmonicGravity(final GravityField field, final int degree, final int order) {
        if (degree > field.getMaxDegree() || order > field.getMaxOrder() || order > degree) {
            throw new IllegalArgumentException(
                    "requested degree/order (" + degree + "/" + order + ") out of range for field "
                            + field.getMaxDegree() + "/" + field.getMaxOrder());
        }
        this.field = field;
        this.degree = degree;
        this.order = order;
        this.bodyFrame = FramesFactory.getITRF();
    }

    @Override
    public Vector3D acceleration(final SpacecraftState state) {
        final Transform inertialToBody =
                FramesFactory.getTransform(state.getFrame(), bodyFrame, state.getDate());
        final Vector3D rBody = inertialToBody.transformPosition(state.getPosition());

        final Vector3D accBody = GravitationalGradient.acceleration(field, degree, order, rBody);

        // rotate the acceleration back to the inertial frame
        return inertialToBody.getRotation().applyInverseTo(accBody);
    }

    @Override
    public String getName() {
        return "SphericalHarmonicGravity(" + degree + "x" + order + ")";
    }
}
