package org.ursc.trajectory.attitude;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Nominal geodetic-pointing, yaw-steered attitude for a nadir-pointing LEO satellite such
 * as Sentinel-3, derived purely from the orbit (no telemetry). The body axes follow the
 * CPOD macro-model convention (GMV-CPOD-TN-0027):
 * <ul>
 *   <li>{@code +Zsat} points to nadir ({@code -r̂}) — the altimeter-reflector panel faces Earth;</li>
 *   <li>{@code -Xsat} is ram-facing — {@code Xsat} is anti-velocity ({@code ≈ -v̂});</li>
 *   <li>{@code Ysat} completes a right-handed frame ({@code ≈ +orbit-normal}), the solar-array
 *       rotation axis.</li>
 * </ul>
 *
 * <p>This is the geometric nadir frame; true flight yaw-steering adds a slow rotation about
 * nadir to keep {@code Ysat} out of the Sun line, which leaves the box-wing projected areas
 * essentially unchanged (the array Sun-tracks about {@code Ysat} either way). It captures the
 * geometry well enough to make the drag/SRP area physical, and is the fallback whenever
 * measured attitude is unavailable for the epoch.</p>
 */
public final class NominalYawSteering implements AttitudeProvider {

    @Override
    public BodyFrame attitude(final SpacecraftState state) {
        final Vector3D r = state.getPosition();
        final Vector3D v = state.getVelocity();

        final Vector3D zB = r.normalize().negate();            // +Z nadir
        // -X ram-facing => X ≈ anti-velocity, projected perpendicular to nadir
        Vector3D xB = v.normalize().negate();
        xB = xB.subtract(zB.scalarMultiply(xB.dotProduct(zB))).normalize();
        final Vector3D yB = zB.crossProduct(xB);               // right-handed (xB×yB=zB)
        return new BodyFrame(xB, yB, zB);
    }
}
