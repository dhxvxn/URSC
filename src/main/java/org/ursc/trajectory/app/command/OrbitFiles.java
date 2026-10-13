package org.ursc.trajectory.app.command;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.config.PropagationConfig;
import org.ursc.trajectory.forces.drag.Atmosphere;
import org.ursc.trajectory.forces.drag.ConstantSpaceWeather;
import org.ursc.trajectory.forces.drag.CssiSpaceWeatherProvider;
import org.ursc.trajectory.forces.drag.CssiTxtSpaceWeatherProvider;
import org.ursc.trajectory.forces.drag.ExponentialAtmosphere;
import org.ursc.trajectory.forces.drag.HarrisPriesterAtmosphere;
import org.ursc.trajectory.forces.drag.JB2008Atmosphere;
import org.ursc.trajectory.forces.drag.Jb2008Inputs;
import org.ursc.trajectory.forces.drag.NRLMSISE00Atmosphere;
import org.ursc.trajectory.forces.drag.SolfsmyDtcProvider;
import org.ursc.trajectory.forces.drag.SpaceWeatherProvider;
import org.ursc.trajectory.forces.gravity.GravityField;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.frames.ConstantEop;
import org.ursc.trajectory.frames.EopProvider;
import org.ursc.trajectory.frames.Finals2000AEopProvider;
import org.ursc.trajectory.frames.Frame;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.IersEopProvider;
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
        /** Real space-weather provider (from a CelesTrak CSV); null = use the constants above. */
        SpaceWeatherProvider spaceWeather = null;
        /** JB2008 solar/geomagnetic driver provider; null = bundled SOLFSMY/DTCFILE snapshots. */
        Jb2008Inputs jb2008Inputs = null;
        /** Gravity field override (e.g. an orekit-data ICGEM model); null = bundled EGM96. */
        GravityField gravityOverride = null;
    }

    /**
     * Build a file-backed space-weather provider, auto-detecting the format: the AGI/Orekit
     * CSSI text file ({@code SpaceWeather-All-v1.2.txt}, first line {@code DATATYPE
     * CssiSpaceWeather}) or the CelesTrak {@code SW-All.csv}. The {@code Setup} constants are
     * the out-of-file fallback. Parsed once per run (not per propagation).
     */
    static SpaceWeatherProvider loadSpaceWeather(final String file, final Setup s)
            throws IOException {
        final ConstantSpaceWeather fallback = new ConstantSpaceWeather(s.f107, s.f107a, s.ap);
        final java.nio.file.Path path = Paths.get(file);
        String first = "";
        try (java.io.BufferedReader r = java.nio.file.Files.newBufferedReader(path)) {
            final String line = r.readLine();
            if (line != null) {
                first = line;
            }
        }
        if (first.toUpperCase(java.util.Locale.US).contains("CSSISPACEWEATHER")) {
            return new CssiTxtSpaceWeatherProvider(path, fallback);
        }
        return new CssiSpaceWeatherProvider(path, fallback);
    }

    private OrbitFiles() {
    }

    /** Configure the frames for a POEORB file: IAU-2006 with the file's UT1−UTC. */
    static void configureFrames(final PoeOrbReader.Ephemeris eph) {
        FramesFactory.setModel(FramesFactory.Model.IAU_2006);
        FramesFactory.setEopProvider(new ConstantEop(eph.ut1MinusUtc, 0.0, 0.0));
    }

    /** Configure the frames with an explicit EOP provider (e.g. a real orekit-data file). */
    static void configureFrames(final PoeOrbReader.Ephemeris eph, final EopProvider eop) {
        FramesFactory.setModel(FramesFactory.Model.IAU_2006);
        FramesFactory.setEopProvider(eop != null ? eop : new ConstantEop(eph.ut1MinusUtc, 0.0, 0.0));
    }

    /**
     * Build a real Earth-orientation provider from an orekit-data EOP file, auto-detecting the
     * layout: the IERS {@code finals2000A.all} (Bulletin&nbsp;A flags) or the EOP&nbsp;C04
     * whitespace series. Dates outside the file fall back to the POEORB's own UT1−UTC with zero
     * polar motion, so the result is never worse than {@link #configureFrames(PoeOrbReader.Ephemeris)}.
     */
    static EopProvider loadEop(final String file, final PoeOrbReader.Ephemeris eph) throws IOException {
        final java.nio.file.Path path = Paths.get(file);
        final EopProvider fallback = new ConstantEop(eph.ut1MinusUtc, 0.0, 0.0);
        boolean finals;
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(path)) {
            finals = Finals2000AEopProvider.looksLikeFinals2000A(in);
        }
        return finals ? new Finals2000AEopProvider(path, fallback)
                      : new IersEopProvider(path, fallback);
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

    /**
     * A toggleable selection of forces for one backtest combination. Defaults to the
     * full-fidelity stack; individual forces can be switched off, the gravity truncation
     * changed, and the atmosphere model selected, so a force-model ablation can be swept.
     */
    static final class ForceSet {
        String label = "full";
        int gravityDegree = 30;   // 1 ~ central (two-body); 2 (order 0) = J2 only
        int gravityOrder = -1;    // -1 => equal to the degree (full tesseral)
        boolean sun = true;
        boolean moon = true;
        String atmosphere = "nrlmsise00"; // none | exponential | harris-priester | nrlmsise00 | jb2008
        boolean srp = true;
        boolean solidTides = true;
        boolean oceanTides = true;
        boolean relativity = true;
        boolean albedo = true;

        boolean hasDrag() {
            return !"none".equalsIgnoreCase(atmosphere);
        }
    }

    /** Build an atmosphere model by name (drag density source). */
    static Atmosphere buildAtmosphere(final String name, final OneAxisEllipsoid earth, final Setup s) {
        switch (name.toLowerCase()) {
            case "exponential":
                return new ExponentialAtmosphere(earth);
            case "harris-priester":
            case "harrispriester":
            case "hp":
                return new HarrisPriesterAtmosphere();
            case "jb2008":
            case "jb":
            case "jacchia-bowman":
                final Jb2008Inputs jb = (s.jb2008Inputs != null)
                        ? s.jb2008Inputs : SolfsmyDtcProvider.bundled();
                return new JB2008Atmosphere(earth, jb);
            case "nrlmsise00":
            case "nrlmsise-00":
            case "msis":
            default:
                final SpaceWeatherProvider weather = (s.spaceWeather != null)
                        ? s.spaceWeather : new ConstantSpaceWeather(s.f107, s.f107a, s.ap);
                return new NRLMSISE00Atmosphere(earth, weather);
        }
    }

    /** Build a {@link PropagationConfig} for an explicit {@link ForceSet}, with fitted Cd/Cr. */
    static PropagationConfig config(final ForceSet fs, final SpacecraftState s0, final Setup s,
                                    final double cd, final double cr) {
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(
                Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING);
        final int order = fs.gravityOrder < 0 ? fs.gravityDegree : fs.gravityOrder;
        final GravityField field = (s.gravityOverride != null)
                ? s.gravityOverride : GravityFieldFactory.getEgm96(fs.gravityDegree);
        final int degree = Math.min(fs.gravityDegree, field.getMaxDegree());
        final int ord = Math.min(order, field.getMaxOrder());
        final PropagationConfig c = new PropagationConfig()
                .initialState(s0)
                .integrator(new DormandPrince54Integrator(1e-7, 1e-9, 1e-3, 120.0), 60.0)
                .gravityField(field, degree, ord)
                .outputType(OrbitType.CARTESIAN);
        if (fs.sun) {
            c.thirdBody(CelestialBodyFactory.getSun());
        }
        if (fs.moon) {
            c.thirdBody(CelestialBodyFactory.getMoon());
        }
        if (fs.hasDrag()) {
            c.drag(buildAtmosphere(fs.atmosphere, earth, s), cd, s.dragArea);
        }
        if (fs.srp) {
            c.solarRadiationPressure(CelestialBodyFactory.getSun(), cr, s.srpArea);
        }
        if (fs.albedo) {
            c.earthRadiation(CelestialBodyFactory.getSun(), cr, s.srpArea);
        }
        if (fs.solidTides) {
            c.solidTides(true);
        }
        if (fs.oceanTides) {
            c.oceanTides(true);
        }
        if (fs.relativity) {
            c.relativity(true, true);
        }
        return c;
    }

    /** A full-fidelity configuration (gravity, Sun/Moon, NRLMSISE drag, SRP, tides, relativity). */
    static PropagationConfig fullConfig(final SpacecraftState s0, final Setup s, final double cd) {
        final ForceSet fs = new ForceSet();
        fs.gravityDegree = s.gravityDegree;
        return config(fs, s0, s, cd, s.cr);
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
