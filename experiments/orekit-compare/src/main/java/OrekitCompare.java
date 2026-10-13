import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.ODEIntegrator;
import org.hipparchus.ode.nonstiff.DormandPrince54Integrator;
import org.orekit.bodies.CelestialBody;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.forces.ForceModel;
import org.orekit.forces.drag.DragForce;
import org.orekit.forces.drag.IsotropicDrag;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.OceanTides;
import org.orekit.forces.gravity.Relativity;
import org.orekit.forces.gravity.SolidTides;
import org.orekit.forces.gravity.ThirdBodyAttraction;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.forces.radiation.IsotropicRadiationSingleCoefficient;
import org.orekit.forces.radiation.KnockeRediffusedForceModel;
import org.orekit.forces.radiation.SolarRadiationPressure;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.models.earth.atmosphere.NRLMSISE00;
import org.orekit.models.earth.atmosphere.data.CssiSpaceWeatherData;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.BoundedPropagator;
import org.orekit.propagation.EphemerisGenerator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.time.UT1Scale;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;

/**
 * Head-to-head cross-check: propagate the exact true Sentinel-3A Oct-7 state ~8 days with
 * OREKIT's own force models and compare the position error vs the actual S3A orbit on Oct-15/16,
 * rung by rung, against URSC's from-scratch result (1935.9 m full stack).
 *
 * Mirrors docs/reports/perturbation-ladder-truestate-7day exactly: same seed, EGM-class 20x20
 * gravity (here EIGEN-6S from orekit-data; ~=EGM96 at 20x20), Sun+Moon, NRLMSISE-00 drag with a
 * FIXED Cd=2.2 (area 8 m^2), conical SRP Cr=1.3 (area 8 m^2), solid+ocean tides, relativity,
 * Knocke albedo/IR; mass 1130 kg; DP54 integrator with URSC's tolerances; real finals2000A EOP +
 * the same CSSI space-weather file.
 *
 * Usage: OrekitCompare <nearEof Oct-7/8> <farEof Oct-15/16> <orekit-data-dir>
 */
public final class OrekitCompare {

    // ---- spacecraft / force constants (identical to URSC's true-state run) ----
    static final double MASS      = 1130.0;
    static final double DRAG_AREA = 8.0;
    static final double SRP_AREA  = 8.0;
    static final double CD        = 2.2;
    static final double CR        = 1.3;
    static final int    GRAV_DEG  = 20;
    static final int    GRAV_ORD  = 20;

    // DP54 tolerances copied from URSC ScenarioLoader defaults
    static final double ABS_TOL  = 1.0e-6;
    static final double REL_TOL  = 1.0e-9;
    static final double MIN_STEP = 1.0e-3;
    static final double MAX_STEP = 300.0;

    /** A single OSV: epoch + ITRF position/velocity (metres, m/s). */
    record Osv(AbsoluteDate utc, Vector3D posItrf, Vector3D velItrf) { }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: OrekitCompare <nearEof> <farEof> <orekit-data-dir>");
            System.exit(2);
        }
        Path nearEof = Path.of(args[0]);
        Path farEof  = Path.of(args[1]);
        Path dataDir = Path.of(args[2]);

        // --- Orekit data context (EOP, gravity, DE440, FES2004, space weather) ---
        DataContext.getDefault().getDataProvidersManager()
                .addProvider(new DirectoryCrawler(dataDir.toFile()));

        final TimeScale utc = TimeScalesFactory.getUTC();

        // --- parse the two POEORB files (ITRF / EARTH_FIXED) ---
        List<Osv> near = parseEof(nearEof, utc);
        List<Osv> far  = parseEof(farEof, utc);
        if (near.isEmpty() || far.isEmpty()) {
            throw new IllegalStateException("empty OSV list (near=" + near.size() + ", far=" + far.size() + ")");
        }
        Osv seed = near.get(0);

        // --- frames ---
        final Frame gcrf = FramesFactory.getGCRF();
        final Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, false);
        final UT1Scale ut1 = TimeScalesFactory.getUT1(IERSConventions.IERS_2010, false);

        // --- gravity field (its mu/ae drive the whole model, for self-consistency) ---
        final NormalizedSphericalHarmonicsProvider provider =
                GravityFieldFactory.getNormalizedProvider(GRAV_DEG, GRAV_ORD);
        final double mu = provider.getMu();
        final double ae = provider.getAe();
        final OneAxisEllipsoid earth =
                new OneAxisEllipsoid(ae, Constants.WGS84_EARTH_FLATTENING, itrf);

        final CelestialBody sun  = CelestialBodyFactory.getSun();
        final CelestialBody moon = CelestialBodyFactory.getMoon();

        // --- seed GCRF state from the ITRF seed OSV ---
        final AbsoluteDate t0 = seed.utc();
        final Transform itrfToGcrf = itrf.getTransformTo(gcrf, t0);
        final PVCoordinates pvGcrf =
                itrfToGcrf.transformPVCoordinates(new PVCoordinates(seed.posItrf(), seed.velItrf()));
        final CartesianOrbit seedOrbit = new CartesianOrbit(pvGcrf, gcrf, t0, mu);
        final SpacecraftState seedState = new SpacecraftState(seedOrbit).withMass(MASS);

        final AbsoluteDate end = far.get(far.size() - 1).utc();

        System.out.println("Orekit " + orekitVersion());
        System.out.printf(Locale.ROOT, "seed epoch %s  mu=%.6e  ae=%.3f%n", t0, mu, ae);
        System.out.printf(Locale.ROOT, "near OSVs=%d  far OSVs=%d  propagate %s -> %s%n%n",
                near.size(), far.size(), t0, end);

        // --- the rungs we compare (same cumulative ladder as URSC's key steps) ---
        String[] labels = {
                "two-body (central only)",
                "gravity 20x20 + Sun/Moon",
                "+ drag (NRLMSISE fixed Cd)",
                "FULL stack (+SRP+tides+rel+albedo)"
        };
        // URSC reference numbers (8-day RMS, m) from ladder_summary.csv
        double[] urscRms = { 13171319.549, 28590.704, 1874.803, 1935.881 };

        System.out.printf(Locale.ROOT, "%-38s %14s %14s %14s | %14s %12s%n",
                "rung", "near1d_RMS_m", "far8d_RMS_m", "far8d_max_m", "URSC_8d_m", "delta_m");
        System.out.println("-".repeat(118));

        for (int level = 0; level <= 3; level++) {
            NumericalPropagator prop = buildPropagator(seedState, mu,
                    itrf, provider, sun, moon, earth, ut1, level);

            EphemerisGenerator gen = prop.getEphemerisGenerator();
            prop.propagate(t0, end);
            BoundedPropagator ephem = gen.getGeneratedEphemeris();

            Stats nearS = score(ephem, near, gcrf, itrf);
            Stats farS  = score(ephem, far, gcrf, itrf);

            double delta = farS.rms - urscRms[level];
            System.out.printf(Locale.ROOT, "%-38s %14.3f %14.3f %14.3f | %14.3f %12.3f%n",
                    labels[level], nearS.rms, farS.rms, farS.max, urscRms[level], delta);
        }
    }

    /** Build a numerical propagator with the cumulative force set for the given rung. */
    static NumericalPropagator buildPropagator(SpacecraftState seedState, double mu,
                                               Frame itrf, NormalizedSphericalHarmonicsProvider provider,
                                               CelestialBody sun, CelestialBody moon,
                                               OneAxisEllipsoid earth, UT1Scale ut1, int level) {
        ODEIntegrator integ = new DormandPrince54Integrator(MIN_STEP, MAX_STEP, ABS_TOL, REL_TOL);
        NumericalPropagator prop = new NumericalPropagator(integ);
        prop.setOrbitType(OrbitType.CARTESIAN);
        prop.setMu(mu);
        prop.setInitialState(seedState);

        List<ForceModel> forces = new ArrayList<>();
        if (level >= 1) {
            forces.add(new HolmesFeatherstoneAttractionModel(itrf, provider));
            forces.add(new ThirdBodyAttraction(sun));
            forces.add(new ThirdBodyAttraction(moon));
        }
        if (level >= 2) {
            CssiSpaceWeatherData sw = new CssiSpaceWeatherData("SpaceWeather-All-v1.2.txt");
            Atmosphere atm = new NRLMSISE00(sw, sun, earth);
            forces.add(new DragForce(atm, new IsotropicDrag(DRAG_AREA, CD)));
        }
        if (level >= 3) {
            IsotropicRadiationSingleCoefficient rad =
                    new IsotropicRadiationSingleCoefficient(SRP_AREA, CR);
            forces.add(new SolarRadiationPressure(sun, earth, rad));
            forces.add(new SolidTides(itrf, provider.getAe(), provider.getMu(),
                    provider.getTideSystem(), IERSConventions.IERS_2010, ut1, sun, moon));
            forces.add(new OceanTides(itrf, provider.getAe(), provider.getMu(),
                    6, 6, IERSConventions.IERS_2010, ut1));
            forces.add(new Relativity(mu));
            forces.add(new KnockeRediffusedForceModel(sun, rad, provider.getAe(),
                    org.hipparchus.util.FastMath.toRadians(15.0)));
        }
        for (ForceModel f : forces) {
            prop.addForceModel(f);
        }
        return prop;
    }

    record Stats(double rms, double max, double last, int n) { }

    /** RMS / max / last of |Δr| in ITRF over the OSVs of a window. */
    static Stats score(BoundedPropagator ephem, List<Osv> truth, Frame gcrf, Frame itrf) {
        double sumSq = 0.0, max = 0.0, last = 0.0;
        int n = 0;
        for (Osv o : truth) {
            if (o.utc().compareTo(ephem.getMinDate()) < 0 || o.utc().compareTo(ephem.getMaxDate()) > 0) {
                continue;
            }
            SpacecraftState s = ephem.propagate(o.utc());
            Vector3D posGcrf = s.getPosition(gcrf);
            Transform gcrfToItrf = gcrf.getTransformTo(itrf, o.utc());
            Vector3D posItrf = gcrfToItrf.transformPosition(posGcrf);
            double d = posItrf.distance(o.posItrf());
            sumSq += d * d;
            if (d > max) max = d;
            last = d;
            n++;
        }
        return new Stats(n > 0 ? Math.sqrt(sumSq / n) : Double.NaN, max, last, n);
    }

    // ---- POEORB .EOF parser (XML, ITRF/EARTH_FIXED OSVs) ----
    private static final Pattern P_UTC = Pattern.compile("<UTC>UTC=([0-9T:.\\-]+)</UTC>");
    private static final Pattern P_X  = Pattern.compile("<X unit=\"m\">([-0-9.eE]+)</X>");
    private static final Pattern P_Y  = Pattern.compile("<Y unit=\"m\">([-0-9.eE]+)</Y>");
    private static final Pattern P_Z  = Pattern.compile("<Z unit=\"m\">([-0-9.eE]+)</Z>");
    private static final Pattern P_VX = Pattern.compile("<VX unit=\"m/s\">([-0-9.eE]+)</VX>");
    private static final Pattern P_VY = Pattern.compile("<VY unit=\"m/s\">([-0-9.eE]+)</VY>");
    private static final Pattern P_VZ = Pattern.compile("<VZ unit=\"m/s\">([-0-9.eE]+)</VZ>");

    static List<Osv> parseEof(Path file, TimeScale utc) throws IOException {
        String content = Files.readString(file);
        List<Osv> out = new ArrayList<>();
        int i = 0;
        while (true) {
            int a = content.indexOf("<OSV>", i);
            if (a < 0) break;
            int b = content.indexOf("</OSV>", a);
            if (b < 0) break;
            String blk = content.substring(a, b);
            i = b + 6;
            AbsoluteDate t = new AbsoluteDate(group(P_UTC, blk), utc);
            Vector3D pos = new Vector3D(dbl(P_X, blk), dbl(P_Y, blk), dbl(P_Z, blk));
            Vector3D vel = new Vector3D(dbl(P_VX, blk), dbl(P_VY, blk), dbl(P_VZ, blk));
            out.add(new Osv(t, pos, vel));
        }
        return out;
    }

    private static String group(Pattern p, String s) {
        Matcher m = p.matcher(s);
        if (!m.find()) throw new IllegalArgumentException("no match for " + p.pattern());
        return m.group(1);
    }

    private static double dbl(Pattern p, String s) {
        return Double.parseDouble(group(p, s));
    }

    private static String orekitVersion() {
        Package pkg = NumericalPropagator.class.getPackage();
        String v = pkg == null ? null : pkg.getImplementationVersion();
        return v == null ? "(version n/a)" : v;
    }
}
