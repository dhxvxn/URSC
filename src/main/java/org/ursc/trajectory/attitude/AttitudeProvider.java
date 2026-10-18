package org.ursc.trajectory.attitude;

import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Supplies the spacecraft body orientation at a given state, for attitude-dependent
 * force models (box-wing drag and solar-radiation pressure). Two implementations ship:
 * {@link NominalYawSteering} (a geometric nadir-pointing, yaw-steered law that works at
 * any epoch) and {@link QuaternionFileAttitude} (measured attitude read from a
 * Sentinel-3 AUX_PROQUA quaternion file, used when the epoch is covered).
 */
public interface AttitudeProvider {

    /**
     * @param state the current spacecraft state (its frame is the inertial frame the
     *              returned axes are expressed in)
     * @return the body frame (Xsat/Ysat/Zsat unit vectors) at that state
     */
    BodyFrame attitude(SpacecraftState state);
}
