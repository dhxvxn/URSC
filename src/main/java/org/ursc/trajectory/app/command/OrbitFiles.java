package org.ursc.trajectory.app.command;

import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.config.PropagationConfig;
import org.ursc.trajectory.forces.drag.ConstantSpaceWeather;
import org.ursc.trajectory.forces.drag.NRLMSISE00Atmosphere;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.frames.ConstantEop;
import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.Transform;
import org.ursc.trajectory.io.PoeOrbReader;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.ode.DormandPrince54Integrator;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.propagation.SpacecraftState;

/**
 * Shared helpers for the POEORB-driven commands ({@code validate}, {@code fit}):
 * frame setup from the file's UT1, ITRF→GCRF conversion, a full-fidelity force
 * configuration, and the radial/along/cross residual decomposition.
 */
final class OrbitFiles {

    /** Fixed spacecraft/model settings for a run (everything except the fitted state/Cd). */
    static final class Setup {
        double mass = 1130.0;
        double dragArea = 8.0;
        double srpArea = 8.0;
        double cr = 1.3;
        int gravityDegree = 30;
        double f107 = 220.0;
        double f107a = 205.0;
        double ap = 12.0;
    }

    private OrbitFiles() {
    }

    /** Configure the frames for a POEORB file: IAU-2006 with the file's UT1−UTC. */
    static void configureFrames(final PoeOrbReader.Ephemeris eph) {
        FramesFactory.setModel(FramesFactory.Model.IAU_2006);
        FramesFactory.setEopProvider(new ConstantEop(eph.ut1MinusUtc, 0.0, 0.0));
    }

    /** Convert the ITRF state at index {@code i} to a GCRF {@link SpacecraftState}. */
    static SpacecraftState gcrfState(final PoeOrbReader.Ephemeris eph, final int i, final double mass) {
        final Frame itrf = FramesFactory.getITRF();
        final Frame gcrf = FramesFactory.getGCRF();
        final Transform t = FramesFactory.getTransform(itrf, gcrf, eph.dates.get(i));
        final Vector3D r = t.transformPosition(eph.positionItrf.get(i));
        final Vector3D v = t.transformVelocity(eph.velocityItrf.get(i), r);
        return new SpacecraftState(
                new CartesianOrbit(new PVCoordinates(r, v), gcrf, eph.dates.get(i), Constants.EARTH_MU),
                mass);
    }

    /** GCRF truth positions for every record. */
    static List<Vector3D> gcrfPositions(final PoeOrbReader.Ephemeris eph) {
        final Frame itrf = FramesFactory.getITRF();
        final Frame gcrf = FramesFactory.getGCRF();
        final List<Vector3D> out = new ArrayList<>(eph.size());
        for (int i = 0; i < eph.size(); i++) {
            out.add(FramesFactory.getTransform(itrf, gcrf, eph.dates.get(i))
                    .transformPosition(eph.positionItrf.get(i)));
        }
        return out;
    }

    /** A full-fidelity configuration (gravity, Sun/Moon, NRLMSISE drag, SRP, tides, relativity). */
    static PropagationConfig fullConfig(final SpacecraftState s0, final Setup s, final double cd) {
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(
                Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING);
        return new PropagationConfig()
                .initialState(s0)
                .integrator(new DormandPrince54Integrator(1e-7, 1e-9, 1e-3, 120.0), 60.0)
                .gravityField(GravityFieldFactory.getEgm96(s.gravityDegree), s.gravityDegree, s.gravityDegree)
                .thirdBody(CelestialBodyFactory.getSun())
                .thirdBody(CelestialBodyFactory.getMoon())
                .drag(new NRLMSISE00Atmosphere(earth, new ConstantSpaceWeather(s.f107, s.f107a, s.ap)),
                        cd, s.dragArea)
                .solarRadiationPressure(CelestialBodyFactory.getSun(), s.cr, s.srpArea)
                .solidTides(true).oceanTides(true).relativity(true, true)
                .outputType(OrbitType.CARTESIAN);
    }

    /** Radial / along-track / cross-track / total components of {@code comp − truth} (m). */
    static double[] rtn(final Vector3D truth, final Vector3D truthNeighbour, final Vector3D comp,
                        final boolean neighbourIsAhead) {
        final Vector3D e = comp.subtract(truth);
        final Vector3D rHat = truth.normalize();
        Vector3D track = neighbourIsAhead ? truthNeighbour.subtract(truth) : truth.subtract(truthNeighbour);
        final Vector3D cHat = rHat.crossProduct(track).normalize();
        final Vector3D aHat = cHat.crossProduct(rHat);
        return new double[] {e.dotProduct(rHat), e.dotProduct(aHat), e.dotProduct(cHat), e.getNorm()};
    }
}
