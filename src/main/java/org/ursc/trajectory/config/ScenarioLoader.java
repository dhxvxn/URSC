package org.ursc.trajectory.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.forces.drag.Atmosphere;
import org.ursc.trajectory.forces.drag.ConstantSpaceWeather;
import org.ursc.trajectory.forces.drag.CssiSpaceWeatherProvider;
import org.ursc.trajectory.forces.drag.ExponentialAtmosphere;
import org.ursc.trajectory.forces.drag.HarrisPriesterAtmosphere;
import org.ursc.trajectory.forces.drag.NRLMSISE00Atmosphere;
import org.ursc.trajectory.forces.drag.SpaceWeatherProvider;
import org.ursc.trajectory.forces.gravity.GravityField;
import org.ursc.trajectory.forces.gravity.GravityFieldFactory;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.ode.ClassicalRungeKutta;
import org.ursc.trajectory.ode.DormandPrince54Integrator;
import org.ursc.trajectory.ode.ODEIntegrator;
import org.ursc.trajectory.orbits.CartesianOrbit;
import org.ursc.trajectory.orbits.KeplerianOrbit;
import org.ursc.trajectory.orbits.Orbit;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.orbits.PVCoordinates;
import org.ursc.trajectory.orbits.PositionAngle;
import org.ursc.trajectory.frames.ConstantEop;
import org.ursc.trajectory.frames.EopProvider;
import org.ursc.trajectory.frames.FramesFactory;
import org.ursc.trajectory.frames.IersEopProvider;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.events.AltitudeDetector;
import org.ursc.trajectory.propagation.events.EventDetector;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;
import org.ursc.trajectory.time.AbsoluteDate;
import org.ursc.trajectory.time.TimeScalesFactory;

/**
 * Builds a complete propagation scenario (initial state, force models,
 * integrator, run span and output settings) from a {@code .properties} file, so
 * a full study can be described and changed without recompiling. The key schema
 * is documented in {@code sample-scenario.properties}.
 */
public final class ScenarioLoader {

    private final Properties props;

    public ScenarioLoader(final Properties props) {
        this.props = props;
    }

    public static ScenarioLoader fromFile(final Path path) throws IOException {
        return new ScenarioLoader(loadProperties(path));
    }

    /** Load a scenario's raw properties (so callers can clone and override them). */
    public static Properties loadProperties(final Path path) throws IOException {
        final Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        }
        return p;
    }

    /** The assembled scenario, ready to run. */
    public static final class Scenario {
        public final NumericalPropagator propagator;
        public final SpacecraftState initialState;
        public final AbsoluteDate startDate;
        public final AbsoluteDate endDate;
        public final double outputStep;
        public final String outputFile;

        Scenario(final NumericalPropagator propagator, final SpacecraftState initialState,
                 final AbsoluteDate startDate, final AbsoluteDate endDate,
                 final double outputStep, final String outputFile) {
            this.propagator = propagator;
            this.initialState = initialState;
            this.startDate = startDate;
            this.endDate = endDate;
            this.outputStep = outputStep;
            this.outputFile = outputFile;
        }
    }

    public Scenario build() {
        configureFrames();
        final AbsoluteDate epoch = parseDate(required("epoch"));
        final double mass = getDouble("spacecraft.mass", 1000.0);
        final Orbit orbit = buildInitialOrbit(epoch);
        final SpacecraftState initialState = new SpacecraftState(orbit, mass);

        final PropagationConfig config = new PropagationConfig()
                .initialState(initialState)
                .integrator(buildIntegrator(), getDouble("integrator.initialStep", 60.0))
                .outputType(OrbitType.valueOf(getString("output.type", "KEPLERIAN").toUpperCase()))
                .outputStep(getDouble("output.step", 60.0));

        configureForces(config);

        final NumericalPropagator propagator = config.build();

        // optional re-entry stop event
        final double reentryAltitude = getDouble("event.reentryAltitude", Double.NaN);
        if (!Double.isNaN(reentryAltitude)) {
            final OneAxisEllipsoid earth = new OneAxisEllipsoid(
                    Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING);
            propagator.addEventDetector(
                    new AltitudeDetector(reentryAltitude, earth, EventDetector.Action.STOP, 60.0));
        }

        final AbsoluteDate end;
        if (props.containsKey("propagation.end")) {
            end = parseDate(required("propagation.end"));
        } else {
            end = epoch.shiftedBy(getDouble("propagation.duration", 86400.0));
        }

        return new Scenario(propagator, initialState, epoch, end,
                getDouble("output.step", 60.0), getString("output.file", "ephemeris.csv"));
    }

    private void configureFrames() {
        final String fm = getString("frames.model", "iau2006").toLowerCase();
        FramesFactory.setModel(fm.equals("simple")
                ? FramesFactory.Model.SIMPLE : FramesFactory.Model.IAU_2006);

        final double arcsecToRad = Math.PI / (180.0 * 3600.0);
        final EopProvider constant = new ConstantEop(
                getDouble("eop.dut1", 0.0),
                getDouble("eop.xp", 0.0) * arcsecToRad,
                getDouble("eop.yp", 0.0) * arcsecToRad);
        final String eopFile = getString("eop.file", "");
        if (!eopFile.isEmpty()) {
            try {
                FramesFactory.setEopProvider(new IersEopProvider(Paths.get(eopFile), constant));
            } catch (final IOException ex) {
                throw new IllegalStateException("cannot load EOP file " + eopFile, ex);
            }
        } else {
            FramesFactory.setEopProvider(constant);
        }
    }

    private Orbit buildInitialOrbit(final AbsoluteDate epoch) {
        final double mu = getDouble("gravity.mu", Constants.EARTH_MU);
        final String type = getString("orbit.type", "keplerian").toLowerCase();
        if (type.equals("cartesian")) {
            final Vector3D r = parseVector(required("orbit.position"));
            final Vector3D v = parseVector(required("orbit.velocity"));
            return new CartesianOrbit(new PVCoordinates(r, v), FramesFactory.getGCRF(), epoch, mu);
        }
        // keplerian
        final double a;
        if (props.containsKey("orbit.altitude")) {
            a = Constants.EARTH_EQUATORIAL_RADIUS + getDouble("orbit.altitude", 600000.0);
        } else {
            a = getDouble("orbit.a", Constants.EARTH_EQUATORIAL_RADIUS + 600000.0);
        }
        final double e = getDouble("orbit.e", 0.0);
        final double i = Math.toRadians(getDouble("orbit.i", 0.0));
        final double raan = Math.toRadians(getDouble("orbit.raan", 0.0));
        final double pa = Math.toRadians(getDouble("orbit.pa", 0.0));
        final double anomaly = Math.toRadians(getDouble("orbit.anomaly", 0.0));
        final PositionAngle angleType =
                PositionAngle.valueOf(getString("orbit.anomaly.type", "MEAN").toUpperCase());
        return new KeplerianOrbit(a, e, i, pa, raan, anomaly, angleType,
                FramesFactory.getGCRF(), epoch, mu);
    }

    private void configureForces(final PropagationConfig config) {
        if (getBoolean("force.gravity", true)) {
            final int degree = (int) getDouble("gravity.degree", 20);
            final int order = (int) getDouble("gravity.order", 20);
            final double mu = getDouble("gravity.mu", Constants.EARTH_MU);
            final double radius = getDouble("gravity.radius", Constants.EARTH_EQUATORIAL_RADIUS);
            final String file = getString("gravity.file", "");
            final String gravityModel = getString("gravity.model", "egm96").toLowerCase();
            final GravityField field;
            if (!file.isEmpty()) {
                try {
                    field = GravityFieldFactory.loadNormalized(
                            Paths.get(file), mu, radius, degree, Math.max(order, degree));
                } catch (final IOException ex) {
                    throw new IllegalStateException("cannot load gravity file " + file, ex);
                }
            } else if (gravityModel.equals("zonal")) {
                field = GravityFieldFactory.getDefaultZonalField();
            } else {
                // bundled EGM96 (satellite-only) complete to d/o 70
                field = GravityFieldFactory.getEgm96(degree);
            }
            // never request beyond what the chosen field supplies
            final int useDegree = Math.min(degree, field.getMaxDegree());
            final int useOrder = Math.min(order, Math.min(useDegree, field.getMaxOrder()));
            config.gravityField(field, useDegree, useOrder);
        }

        if (getBoolean("force.thirdBody.sun", false)) {
            config.thirdBody(CelestialBodyFactory.getSun());
        }
        if (getBoolean("force.thirdBody.moon", false)) {
            config.thirdBody(CelestialBodyFactory.getMoon());
        }

        if (getBoolean("force.drag", false)) {
            final Atmosphere atmosphere = buildAtmosphere();
            config.drag(atmosphere,
                    getDouble("spacecraft.dragCoefficient", 2.2),
                    getDouble("spacecraft.dragArea", 1.0));
        }

        if (getBoolean("force.srp", false)) {
            config.solarRadiationPressure(CelestialBodyFactory.getSun(),
                    getDouble("spacecraft.reflectionCoefficient", 1.5),
                    getDouble("spacecraft.srpArea", 1.0));
        }

        if (getBoolean("force.solidTides", false)) {
            config.solidTides(true);
        }

        if (getBoolean("force.oceanTides", false)) {
            config.oceanTides(true);
        }

        if (getBoolean("force.earthRadiation", false)) {
            config.earthRadiation(CelestialBodyFactory.getSun(),
                    getDouble("spacecraft.reflectionCoefficient", 1.5),
                    getDouble("spacecraft.srpArea", 1.0));
        }

        if (getBoolean("force.relativity", false)) {
            config.relativity(true, getBoolean("force.relativity.lenseThirring", false));
        }

        if (getBoolean("force.empirical", false)) {
            config.empiricalAcceleration(
                    getDouble("empirical.radial", 0.0),
                    getDouble("empirical.transverse", 0.0),
                    getDouble("empirical.normal", 0.0));
        }
    }

    private Atmosphere buildAtmosphere() {
        final String model = getString("drag.model", "exponential").toLowerCase();
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(
                Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING);
        if (model.equals("harris-priester") || model.equals("harrispriester")) {
            return new HarrisPriesterAtmosphere(earth, CelestialBodyFactory.getSun(),
                    getDouble("drag.harrisPriester.exponent", 2.0));
        }
        if (model.equals("nrlmsise00") || model.equals("nrlmsise-00") || model.equals("msis")) {
            return new NRLMSISE00Atmosphere(earth, buildSpaceWeather());
        }
        return new ExponentialAtmosphere(earth);
    }

    private SpaceWeatherProvider buildSpaceWeather() {
        final SpaceWeatherProvider constant = new ConstantSpaceWeather(
                getDouble("spaceWeather.f107", 150.0),
                getDouble("spaceWeather.f107a", 150.0),
                getDouble("spaceWeather.ap", 4.0));
        final String source = getString("spaceWeather.source", "constant").toLowerCase();
        if (source.equals("file")) {
            final String file = getString("spaceWeather.file", "");
            if (file.isEmpty()) {
                throw new IllegalStateException(
                        "spaceWeather.source=file requires spaceWeather.file to be set");
            }
            try {
                // the constant values act as the fallback for dates outside the file
                return new CssiSpaceWeatherProvider(Paths.get(file), constant);
            } catch (final IOException ex) {
                throw new IllegalStateException("cannot load space-weather file " + file, ex);
            }
        }
        return constant;
    }

    private ODEIntegrator buildIntegrator() {
        final String type = getString("integrator.type", "dp54").toLowerCase();
        if (type.equals("rk4")) {
            return new ClassicalRungeKutta();
        }
        return new DormandPrince54Integrator(
                getDouble("integrator.absTol", 1.0e-6),
                getDouble("integrator.relTol", 1.0e-9),
                getDouble("integrator.minStep", 1.0e-3),
                getDouble("integrator.maxStep", 300.0));
    }

    // ---- parsing helpers ----

    private AbsoluteDate parseDate(final String text) {
        // expected: YYYY-MM-DDThh:mm:ss[.sss] (UTC)
        final String[] dt = text.trim().split("[T ]");
        final String[] d = dt[0].split("-");
        final String[] t = dt.length > 1 ? dt[1].split(":") : new String[] {"0", "0", "0"};
        final int year = Integer.parseInt(d[0]);
        final int month = Integer.parseInt(d[1]);
        final int day = Integer.parseInt(d[2]);
        final int hour = t.length > 0 ? Integer.parseInt(t[0]) : 0;
        final int minute = t.length > 1 ? Integer.parseInt(t[1]) : 0;
        final double second = t.length > 2 ? Double.parseDouble(t[2]) : 0.0;
        return new AbsoluteDate(year, month, day, hour, minute, second, TimeScalesFactory.getUTC());
    }

    private Vector3D parseVector(final String text) {
        final String[] parts = text.split(",");
        return new Vector3D(Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim()));
    }

    private String required(final String key) {
        final String v = props.getProperty(key);
        if (v == null) {
            throw new IllegalStateException("missing required property: " + key);
        }
        return v.trim();
    }

    private String getString(final String key, final String def) {
        final String v = props.getProperty(key);
        return v == null ? def : v.trim();
    }

    private double getDouble(final String key, final double def) {
        final String v = props.getProperty(key);
        return v == null ? def : Double.parseDouble(v.trim());
    }

    private boolean getBoolean(final String key, final boolean def) {
        final String v = props.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }
}
