package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.forces.drag.BoxWingDrag;
import org.ursc.trajectory.forces.drag.IsotropicDrag;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/** Verifies the box-wing projected-area model is attitude-dependent and bounded, and that the
 *  cannonball {@link IsotropicDrag} is unaffected by the new direction-aware overload. */
class BoxWingTest {

    // Sentinel-3A geometry
    private static final double BX = 2.2;
    private static final double BY = 2.2;
    private static final double BZ = 3.7;
    private static final double WING = 10.5;
    private static final double AREA_MAX = BY * BZ + BX * BZ + BX * BY + WING; // all faces + wing

    private static SpacecraftState state() {
        final Vector3D r = new Vector3D(7.0e6, 0.0, 0.0);
        final Vector3D v = new Vector3D(0.0, 7500.0, 0.0);
        final AbsoluteDate date = new AbsoluteDate(2024, 10, 11, 12, 0, 0.0, TimeScalesFactory.getUTC());
        final CartesianOrbit orbit = new CartesianOrbit(new PVCoordinates(r, v),
                FramesFactory.getGCRF(), date, Constants.EARTH_MU);
        return new SpacecraftState(orbit, 1093.4);
    }

    @Test
    void projectedAreaIsAttitudeDependentAndBounded() {
        final BoxWingDrag bw = new BoxWingDrag(2.2, BX, BY, BZ, WING, CelestialBodyFactory.getSun());
        final SpacecraftState s = state();

        final double aVel   = bw.getCrossSection(s, new Vector3D(0.0, 1.0, 0.0));  // along velocity
        final double aNadir = bw.getCrossSection(s, new Vector3D(-1.0, 0.0, 0.0)); // nadir

        assertTrue(aVel > 0.0 && aVel <= AREA_MAX, "aVel out of range: " + aVel);
        assertTrue(aNadir > 0.0 && aNadir <= AREA_MAX, "aNadir out of range: " + aNadir);
        assertTrue(Math.abs(aVel - aNadir) > 1.0e-3, "projected area should depend on direction");
    }

    @Test
    void constantFallback() {
        final BoxWingDrag bw = new BoxWingDrag(2.2, BX, BY, BZ, WING, CelestialBodyFactory.getSun());
        final double expected = (BY * BZ + BX * BZ + BX * BY) / 3.0 + WING;
        assertEquals(expected, bw.getCrossSection(), 1.0e-9);
    }

    @Test
    void cannonballUnaffectedByOverload() {
        final IsotropicDrag iso = new IsotropicDrag(2.2, 8.0);
        // the default direction-aware overload must forward to the constant area
        assertEquals(8.0, iso.getCrossSection(state(), new Vector3D(0.0, 1.0, 0.0)), 1.0e-12);
        assertEquals(8.0, iso.getCrossSection(), 1.0e-12);
    }
}
