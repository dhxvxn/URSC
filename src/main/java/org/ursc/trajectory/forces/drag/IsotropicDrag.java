package org.ursc.trajectory.forces.drag;

/** Fixed drag coefficient and cross section (the "cannonball" drag model). */
public final class IsotropicDrag implements DragSensitive {

    private final double dragCoefficient;
    private final double crossSection;

    public IsotropicDrag(final double dragCoefficient, final double crossSection) {
        this.dragCoefficient = dragCoefficient;
        this.crossSection = crossSection;
    }

    @Override
    public double getDragCoefficient() {
        return dragCoefficient;
    }

    @Override
    public double getCrossSection() {
        return crossSection;
    }
}
