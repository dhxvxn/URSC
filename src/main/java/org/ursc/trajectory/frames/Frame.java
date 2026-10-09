package org.ursc.trajectory.frames;

/**
 * A reference frame. For this library two frames matter directly:
 * a pseudo-inertial celestial frame (GCRF, treated as equal to EME2000/J2000 to
 * within the sub-milliarcsecond frame bias) in which the equations of motion are
 * integrated, and an Earth-fixed frame (ITRF) in which the gravity field and the
 * atmosphere co-rotate with the Earth.
 *
 * <p>The hierarchy is intentionally small but the {@link FramesFactory} transform
 * machinery is written so that intermediate frames (precession, nutation, polar
 * motion) can be inserted later without touching client code.</p>
 */
public final class Frame {

    private final String name;
    private final boolean pseudoInertial;

    Frame(final String name, final boolean pseudoInertial) {
        this.name = name;
        this.pseudoInertial = pseudoInertial;
    }

    public String getName() {
        return name;
    }

    /** @return {@code true} if the equations of motion may be integrated in this frame. */
    public boolean isPseudoInertial() {
        return pseudoInertial;
    }

    @Override
    public String toString() {
        return name;
    }
}
