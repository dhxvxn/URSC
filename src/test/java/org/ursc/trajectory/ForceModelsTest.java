package org.ursc.trajectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.forces.Relativity;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.forces.gravity.OceanTides;
import org.ursc.trajectory.forces.gravity.SolidTides;
import org.ursc.trajectory.forces.radiation.EarthRadiationPressure;
import org.ursc.trajectory.forces.radiation.IsotropicRadiationSingleCoefficient;
import org.ursc.trajectory.forces.radiation.RadiationSensitive;
import org.ursc.trajectory.forces.radiation.SolarRadiationPressure;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.PositionAngle;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/** Magnitude / sanity checks for the newly added LEO perturbations. */
class ForceModelsTest {

    private SpacecraftState state() {
        final AbsoluteDate d = new AbsoluteDate(2024, 6, 21, 12, 0, 0.0, TimeScalesFactory.getUTC());
        final KeplerianOrbit o = new KeplerianOrbit(
                Constants.EARTH_EQUATORIAL_RADIUS + 600_000.0, 0.001, Math.toRadians(51.6),
                0.0, Math.toRadians(30.0), 0.0, PositionAngle.MEAN,
                FramesFactory.getGCRF(), d, Constants.EARTH_MU);
        return new SpacecraftState(o, 500.0);
    }

    @Test
    void solidTidesAreActiveAndSane() {
        final Vector3D a = new SolidTides().acceleration(state());
        // dominant degree-2 tide at 600 km is of order 1e-7 m/s^2
        assertTrue(a.getNorm() > 1e-9 && a.getNorm() < 1e-6,
                "solid tide magnitude out of range: " + a.getNorm());

        // with no tide-raising bodies, the contribution must vanish
        final Vector3D none = new SolidTides(GravityFieldFactory.EGM96_MU,
                GravityFieldFactory.EGM96_RADIUS).acceleration(state());
        assertEquals(0.0, none.getNorm(), 1e-20);
    }

    @Test
    void oceanTidesAreActiveSaneAndTimeVarying() {
        final OceanTides ocean = new OceanTides();
        final Vector3D a = ocean.acceleration(state());
        // FES2004 8-constituent truncation at 600 km is of order 1e-8 m/s^2
        assertTrue(a.getNorm() > 1e-10 && a.getNorm() < 1e-6,
                "ocean tide magnitude out of range: " + a.getNorm());

        // the tidal argument advances with time, so the contribution must change
        final AbsoluteDate later = new AbsoluteDate(2024, 6, 21, 18, 0, 0.0, TimeScalesFactory.getUTC());
        final KeplerianOrbit o2 = new KeplerianOrbit(
                Constants.EARTH_EQUATORIAL_RADIUS + 600_000.0, 0.001, Math.toRadians(51.6),
                0.0, Math.toRadians(30.0), 0.0, PositionAngle.MEAN,
                FramesFactory.getGCRF(), later, Constants.EARTH_MU);
        final Vector3D b = ocean.acceleration(new SpacecraftState(o2, 500.0));
        assertTrue(a.subtract(b).getNorm() > 1e-12, "ocean tide should vary with the tidal argument");
    }

    @Test
    void earthRadiationIsAFractionOfDirectSrp() {
        final RadiationSensitive rs = new IsotropicRadiationSingleCoefficient(1.5, 2.0);
        final double er = new EarthRadiationPressure(CelestialBodyFactory.getSun(), rs, 10, 20)
                .acceleration(state()).getNorm();
        final double srp = new SolarRadiationPressure(CelestialBodyFactory.getSun(), rs)
                .acceleration(state()).getNorm();
        final double ratio = er / srp;
        assertTrue(ratio > 0.05 && ratio < 0.5,
                "Earth-radiation / SRP ratio out of range: " + ratio);
    }

    @Test
    void earthRadiationVanishesWithZeroArea() {
        final RadiationSensitive zero = new IsotropicRadiationSingleCoefficient(1.5, 0.0);
        final double er = new EarthRadiationPressure(CelestialBodyFactory.getSun(), zero, 8, 16)
                .acceleration(state()).getNorm();
        assertEquals(0.0, er, 1e-20);
    }

    @Test
    void lenseThirringIsSmallAndSchwarzschildUnchanged() {
        final SpacecraftState s = state();
        final Vector3D schOnly = new Relativity(Constants.EARTH_MU, false).acceleration(s);
        final Vector3D withLt = new Relativity(Constants.EARTH_MU, true).acceleration(s);
        final double delta = withLt.subtract(schOnly).getNorm();
        assertTrue(delta > 1e-12 && delta < 1e-9, "Lense-Thirring magnitude out of range: " + delta);
        assertTrue(schOnly.getNorm() > 1e-9, "Schwarzschild term should be non-trivial");
    }
}
