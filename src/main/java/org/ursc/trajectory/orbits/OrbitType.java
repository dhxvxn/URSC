package org.ursc.trajectory.orbits;

/** The supported orbital parameter sets. Any orbit can be converted between them. */
public enum OrbitType {
    /** Position/velocity Cartesian coordinates. */
    CARTESIAN,
    /** Classical Keplerian elements (a, e, i, pa, raan, anomaly). */
    KEPLERIAN,
    /** Equinoctial elements (a, ex, ey, hx, hy, lv) — singularity-free for e~0, i~0. */
    EQUINOCTIAL
}
