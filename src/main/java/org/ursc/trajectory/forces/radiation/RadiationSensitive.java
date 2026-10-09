package org.ursc.trajectory.forces.radiation;

/**
 * Spacecraft properties relevant to solar radiation pressure. Implement this for
 * box-and-wing models; {@link IsotropicRadiationSingleCoefficient} provides the
 * simple fixed-area model with a single reflection coefficient.
 */
public interface RadiationSensitive {

    /** @return the radiation pressure coefficient Cr (1 = black body, 2 = fully reflective). */
    double getReflectionCoefficient();

    /** @return the cross-sectional area exposed to the Sun (m^2). */
    double getCrossSection();
}
