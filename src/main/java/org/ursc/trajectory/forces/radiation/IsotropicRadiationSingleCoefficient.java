package org.ursc.trajectory.forces.radiation;

/** Fixed reflection coefficient and cross section (cannonball SRP model). */
public final class IsotropicRadiationSingleCoefficient implements RadiationSensitive {

    private final double reflectionCoefficient;
    private final double crossSection;

    public IsotropicRadiationSingleCoefficient(final double reflectionCoefficient,
                                               final double crossSection) {
        this.reflectionCoefficient = reflectionCoefficient;
        this.crossSection = crossSection;
    }

    @Override
    public double getReflectionCoefficient() {
        return reflectionCoefficient;
    }

    @Override
    public double getCrossSection() {
        return crossSection;
    }
}
