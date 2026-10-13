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
import org.orekit.models.earth.atmosphere.DTM2000;
import org.orekit.models.earth.atmosphere.JB2008;
import org.orekit.models.earth.atmosphere.NRLMSISE00;
import org.orekit.models.earth.atmosphere.data.CssiSpaceWeatherData;
import org.orekit.models.earth.atmosphere.data.JB2008SpaceEnvironmentData;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.BoundedPropagator;
import org.orekit.propagation.EphemerisGenerator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;

/**
 * Storm-capable density A/B: propagate the true Sentinel-3A Oct-7 state ~8 days with the SAME full
 * force stack (gravity 40x40, Sun/Moon, SRP, solid+ocean tides, relativity, albedo) but three
 * different atmosphere models — NRLMSISE-00, JB2008 (storm-aware via DTC + S10/M10/Y10 solar flare
 * indices), DTM2000 — and compare the 8-day position RMS vs the real Oct-15/16 orbit. For each model
 * a small drag-area sweep reports its OWN best ballistic coefficient (fair comparison).
 *
 * Usage: OrekitAtmos <nearEof> <farEof> <orekit-data-dir>
 */
public final class OrekitAtmos {

    static final double MASS = 1093.408;      // latest documented S3A mass
    static final double CR = 1.3;
    static final double SRP_AREA = 8.0;
    static final int    GRAV = 40;
    static final double CD = 2.2;
    static final double[] AREAS = {6.0, 7.0, 8.0, 8.25, 9.0, 10.0};

    static final double ABS_TOL = 1.0e-6, REL_TOL = 1.0e-9, MIN_STEP = 1.0e-3, MAX_STEP = 300.0;

    record Osv(AbsoluteDate utc, Vector3D posItrf, Vector3D velItrf) { }
    record Stats(double rms, double max, int n) { }

    public static void main(String[] args) throws Exception {
        Path nearEof = Path.of(args[0]), farEof = Path.of(args[1]), dataDir = Path.of(args[2]);
        DataContext.getDefault().getDataProvidersManager()
                .addProvider(new DirectoryCrawler(dataDir.toFile()));
        final TimeScale utc = TimeScalesFactory.getUTC();

        List<Osv> near = parseEof(nearEof, utc);
        List<Osv> far  = parseEof(farEof, utc);
        Osv seed = near.get(0);

        final Frame gcrf = FramesFactory.getGCRF();
        final Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, false);
        final var ut1 = TimeScalesFactory.getUT1(IERSConventions.IERS_2010, false);
        final NormalizedSphericalHarmonicsProvider provider =
                GravityFieldFactory.getNormalizedProvider(GRAV, GRAV);
        final double mu = provider.getMu(), ae = provider.getAe();
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(ae, Constants.WGS84_EARTH_FLATTENING, itrf);
        final CelestialBody sun = CelestialBodyFactory.getSun(), moon = CelestialBodyFactory.getMoon();

        final AbsoluteDate t0 = seed.utc();
        final Transform itrfToGcrf = itrf.getTransformTo(gcrf, t0);
        final PVCoordinates pv = itrfToGcrf.transformPVCoordinates(
                new PVCoordinates(seed.posItrf(), seed.velItrf()));
        final CartesianOrbit seedOrbit = new CartesianOrbit(pv, gcrf, t0, mu);
        final AbsoluteDate end = far.get(far.size() - 1).utc();

        System.out.printf(Locale.ROOT, "Storm-density A/B  mass=%.3f  Cd=%.2f  gravity %dx%d%n", MASS, CD, GRAV, GRAV);
        System.out.printf(Locale.ROOT, "seed %s -> %s   near=%d far=%d%n%n", t0, end, near.size(), far.size());

        String[] models = {"NRLMSISE00", "JB2008", "DTM2000"};
        System.out.printf(Locale.ROOT, "%-12s %9s %12s %14s %14s%n",
                "atmosphere", "area_m2", "B_m2/kg", "far8d_RMS_m", "near1d_RMS_m");
        System.out.println("-".repeat(66));

        for (String model : models) {
            double bestRms = Double.MAX_VALUE, bestArea = 0;
            for (double area : AREAS) {
                NumericalPropagator prop = new NumericalPropagator(
                        (ODEIntegrator) new DormandPrince54Integrator(MIN_STEP, MAX_STEP, ABS_TOL, REL_TOL));
                prop.setOrbitType(OrbitType.CARTESIAN);
                prop.setMu(mu);
                prop.setInitialState(new SpacecraftState(seedOrbit).withMass(MASS));

                prop.addForceModel(new HolmesFeatherstoneAttractionModel(itrf, provider));
                prop.addForceModel(new ThirdBodyAttraction(sun));
                prop.addForceModel(new ThirdBodyAttraction(moon));
                Atmosphere atm = buildAtmosphere(model, sun, earth, utc);
                prop.addForceModel(new DragForce(atm, new IsotropicDrag(area, CD)));
                IsotropicRadiationSingleCoefficient rad = new IsotropicRadiationSingleCoefficient(SRP_AREA, CR);
                prop.addForceModel(new SolarRadiationPressure(sun, earth, rad));
                prop.addForceModel(new SolidTides(itrf, ae, mu, provider.getTideSystem(),
                        IERSConventions.IERS_2010, ut1, sun, moon));
                prop.addForceModel(new OceanTides(itrf, ae, mu, 6, 6, IERSConventions.IERS_2010, ut1));
                prop.addForceModel(new Relativity(mu));
                prop.addForceModel(new KnockeRediffusedForceModel(sun, rad, ae,
                        org.hipparchus.util.FastMath.toRadians(15.0)));

                EphemerisGenerator gen = prop.getEphemerisGenerator();
                prop.propagate(t0, end);
                BoundedPropagator ephem = gen.getGeneratedEphemeris();

                Stats f = score(ephem, far, gcrf, itrf);
                Stats n = score(ephem, near, gcrf, itrf);
                double b = CD * area / MASS;
                System.out.printf(Locale.ROOT, "%-12s %9.3f %12.6f %14.1f %14.1f%n",
                        model, area, b, f.rms, n.rms);
                if (f.rms < bestRms) { bestRms = f.rms; bestArea = area; }
            }
            System.out.printf(Locale.ROOT, ">>> %-10s best far8d RMS %.1f m at area %.3f m^2 (B=%.6f)%n%n",
                    model, bestRms, bestArea, CD * bestArea / MASS);
        }
    }

    static Atmosphere buildAtmosphere(String model, CelestialBody sun, OneAxisEllipsoid earth,
                                      TimeScale utc) throws Exception {
        switch (model) {
            case "NRLMSISE00":
                return new NRLMSISE00(new CssiSpaceWeatherData("SpaceWeather-All-v1.2.txt"), sun, earth);
            case "DTM2000":
                return new DTM2000(new CssiSpaceWeatherData("SpaceWeather-All-v1.2.txt"), sun, earth);
            case "JB2008":
                JB2008SpaceEnvironmentData jb =
                        new JB2008SpaceEnvironmentData("SOLFSMY.TXT", "DTCFILE.TXT");
                return new JB2008(jb, sun, earth, utc);
            default:
                throw new IllegalArgumentException(model);
        }
    }

    static Stats score(BoundedPropagator ephem, List<Osv> truth, Frame gcrf, Frame itrf) {
        double sumSq = 0, max = 0; int n = 0;
        for (Osv o : truth) {
            if (o.utc().compareTo(ephem.getMinDate()) < 0 || o.utc().compareTo(ephem.getMaxDate()) > 0) continue;
            Vector3D posGcrf = ephem.propagate(o.utc()).getPosition(gcrf);
            Vector3D posItrf = gcrf.getTransformTo(itrf, o.utc()).transformPosition(posGcrf);
            double d = posItrf.distance(o.posItrf());
            sumSq += d * d; if (d > max) max = d; n++;
        }
        return new Stats(n > 0 ? Math.sqrt(sumSq / n) : Double.NaN, max, n);
    }

    private static final Pattern P_UTC = Pattern.compile("<UTC>UTC=([0-9T:.\\-]+)</UTC>");
    private static final Pattern P_X  = Pattern.compile("<X unit=\"m\">([-0-9.eE]+)</X>");
    private static final Pattern P_Y  = Pattern.compile("<Y unit=\"m\">([-0-9.eE]+)</Y>");
    private static final Pattern P_Z  = Pattern.compile("<Z unit=\"m\">([-0-9.eE]+)</Z>");
    private static final Pattern P_VX = Pattern.compile("<VX unit=\"m/s\">([-0-9.eE]+)</VX>");
    private static final Pattern P_VY = Pattern.compile("<VY unit=\"m/s\">([-0-9.eE]+)</VY>");
    private static final Pattern P_VZ = Pattern.compile("<VZ unit=\"m/s\">([-0-9.eE]+)</VZ>");

    static List<Osv> parseEof(Path file, TimeScale utc) throws IOException {
        String c = Files.readString(file);
        List<Osv> out = new ArrayList<>();
        int i = 0;
        while (true) {
            int a = c.indexOf("<OSV>", i); if (a < 0) break;
            int b = c.indexOf("</OSV>", a); if (b < 0) break;
            String blk = c.substring(a, b); i = b + 6;
            out.add(new Osv(new AbsoluteDate(g(P_UTC, blk), utc),
                    new Vector3D(d(P_X, blk), d(P_Y, blk), d(P_Z, blk)),
                    new Vector3D(d(P_VX, blk), d(P_VY, blk), d(P_VZ, blk))));
        }
        return out;
    }

    private static String g(Pattern p, String s) { Matcher m = p.matcher(s); if (!m.find()) throw new IllegalArgumentException(p.pattern()); return m.group(1); }
    private static double d(Pattern p, String s) { return Double.parseDouble(g(p, s)); }

    private OrekitAtmos() { }
}
