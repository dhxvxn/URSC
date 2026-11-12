package org.ursc.trajectory.orekit;

import java.util.ArrayList;
import java.util.List;

import org.hipparchus.CalculusFieldElement;
import org.hipparchus.Field;
import org.hipparchus.geometry.euclidean.threed.FieldRotation;
import org.hipparchus.geometry.euclidean.threed.FieldVector3D;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationOrder;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.orekit.attitudes.Attitude;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.FieldAttitude;
import org.orekit.attitudes.LofOffset;
import org.orekit.forces.BoxAndSolarArraySpacecraft;
import org.orekit.forces.ForceModel;
import org.orekit.forces.drag.DragForce;
import org.orekit.forces.drag.DragSensitive;
import org.orekit.forces.drag.IsotropicDrag;
import org.orekit.forces.empirical.ParametricAcceleration;
import org.orekit.forces.empirical.PolynomialAccelerationModel;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.LenseThirringRelativity;
import org.orekit.forces.gravity.OceanTides;
import org.orekit.forces.gravity.Relativity;
import org.orekit.forces.gravity.SolidTides;
import org.orekit.forces.gravity.ThirdBodyAttraction;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.forces.radiation.IsotropicRadiationSingleCoefficient;
import org.orekit.forces.radiation.KnockeRediffusedForceModel;
import org.orekit.forces.radiation.RadiationSensitive;
import org.orekit.forces.radiation.SolarRadiationPressure;
import org.orekit.frames.Frame;
import org.orekit.frames.LOFType;
import org.orekit.models.earth.atmosphere.Atmosphere;
import org.orekit.models.earth.atmosphere.HarrisPriester;
import org.orekit.models.earth.atmosphere.JB2008;
import org.orekit.models.earth.atmosphere.JB2008InputParameters;
import org.orekit.models.earth.atmosphere.NRLMSISE00;
import org.orekit.models.earth.atmosphere.NRLMSISE00InputParameters;
import org.orekit.models.earth.atmosphere.SimpleExponentialAtmosphere;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.ToleranceProvider;
import org.orekit.propagation.conversion.DormandPrince853IntegratorBuilder;
import org.orekit.propagation.conversion.NumericalPropagatorBuilder;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.FieldAbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.FieldPVCoordinatesProvider;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.PVCoordinatesProvider;
import org.orekit.utils.ParameterDriver;
import org.ursc.trajectory.attitude.BodyFrame;
import org.ursc.trajectory.pipeline.Environment;
import org.ursc.trajectory.pipeline.RunConfig;

/**
 * The force model, attitude and numerical propagator of every run, built from Orekit classes
 * according to the {@link RunConfig} switches:
 *
 * <table>
 *   <caption>Orekit models used</caption>
 *   <tr><td>gravity field</td><td>{@link HolmesFeatherstoneAttractionModel} (EGM96 or ICGEM file)</td></tr>
 *   <tr><td>Sun, Moon</td><td>{@link ThirdBodyAttraction} with JPL DE440</td></tr>
 *   <tr><td>drag</td><td>{@link DragForce} with {@link NRLMSISE00}, {@link JB2008}, {@link HarrisPriester} or
 *       {@link SimpleExponentialAtmosphere}; {@link IsotropicDrag} or {@link BoxAndSolarArraySpacecraft}</td></tr>
 *   <tr><td>solar radiation</td><td>{@link SolarRadiationPressure} (conical eclipse)</td></tr>
 *   <tr><td>Earth albedo + IR</td><td>{@link KnockeRediffusedForceModel}</td></tr>
 *   <tr><td>solid tides</td><td>{@link SolidTides} (IERS 2010)</td></tr>
 *   <tr><td>ocean tides</td><td>{@link OceanTides}, FES2004 (8 main constituents, degree 4)</td></tr>
 *   <tr><td>relativity</td><td>{@link Relativity} (Schwarzschild), {@link LenseThirringRelativity}</td></tr>
 *   <tr><td>empirical</td><td>{@link ParametricAcceleration} in the radial/along/cross frame</td></tr>
 *   <tr><td>integrator</td><td>{@link DormandPrince853Integrator}</td></tr>
 * </table>
 *
 * <p>Body frame (all attitudes): the CPOD convention of the Sentinel-3 POD documents, +Z nadir,
 * X anti-velocity, Y = Z x X, the frame of the GNSS antenna offset in the observation header.
 * Nominal attitude is Orekit's yaw-compensated geodetic nadir pointing ({@link #nominalAttitude}).</p>
 */
public final class OrekitDynamics {

    /** Space-weather adapter names used for the force-model parameter list. */
    public static final String EMPIRICAL_PREFIX = "empirical-";

    private OrekitDynamics() {
    }

    /**
     * Nominal attitude: Orekit's geodetic {@link org.orekit.attitudes.NadirPointing} with
     * {@link org.orekit.attitudes.YawCompensation} (the ground-track yaw that Sentinel-3 flies for
     * its altimeter), in the CPOD body convention. Checked against the measured PROQUA
     * quaternions of 15 Oct 2024: 0.005 deg mean, 0.014 deg maximum difference.
     */
    public static AttitudeProvider nominalAttitude(final OrekitSession s) {
        return new CpodBodyFrame(new org.orekit.attitudes.YawCompensation(s.gcrf,
                new org.orekit.attitudes.NadirPointing(s.gcrf, s.earth)));
    }

    /**
     * The run's attitude: measured PROQUA quaternions (through the program's cutoff-safe reader,
     * nominal outside the files) when selected with the box-wing model, else nominal.
     */
    public static AttitudeProvider attitude(final RunConfig cfg, final Environment env, final OrekitSession s) {
        if (cfg.spacecraft.shape == RunConfig.ShapeModel.BOX_WING
                && cfg.spacecraft.attitude == RunConfig.AttitudeSource.MEASURED_PROQUA && env.attitude != null) {
            return new MeasuredAttitude(env.attitude, s);
        }
        return nominalAttitude(s);
    }

    /** Force models of the run; {@code cd}, {@code cr} are the drag and radiation coefficients. */
    public static List<ForceModel> forces(final RunConfig cfg, final Environment env, final OrekitSession s,
                                          final double cd, final double cr, final List<String> warnings) {
        final RunConfig.Forces f = cfg.forces;
        final RunConfig.Spacecraft sc = cfg.spacecraft;
        final List<ForceModel> out = new ArrayList<>();
        final double mu;
        if (f.gravity.enabled) {
            final NormalizedSphericalHarmonicsProvider g = s.gravity(env.gravityDegree, env.gravityOrder);
            out.add(new HolmesFeatherstoneAttractionModel(s.itrf, g));
            mu = g.getMu();
            if (f.solidTides.enabled) {
                out.add(new SolidTides(s.itrf, g.getAe(), g.getMu(), g.getTideSystem(), IERSConventions.IERS_2010,
                        s.ut1, s.sun, s.moon));
            }
            if (f.oceanTides.enabled) {
                out.add(new OceanTides(s.itrf, g.getAe(), g.getMu(), 4, 4, IERSConventions.IERS_2010, s.ut1));
            }
        } else {
            mu = Constants.EIGEN5C_EARTH_MU;
        }
        if (f.sun.enabled) {
            out.add(new ThirdBodyAttraction(s.sun));
        }
        if (f.moon.enabled) {
            out.add(new ThirdBodyAttraction(s.moon));
        }
        final boolean box = sc.shape == RunConfig.ShapeModel.BOX_WING;
        final DragSensitive dragCraft;
        final RadiationSensitive radCraft;
        if (box) {
            // face areas -> box dimensions: Ax = y*z, Ay = x*z, Az = x*y
            final double x = Math.sqrt(sc.faceAreaY * sc.faceAreaZ / sc.faceAreaX);
            final double y = Math.sqrt(sc.faceAreaX * sc.faceAreaZ / sc.faceAreaY);
            final double z = Math.sqrt(sc.faceAreaX * sc.faceAreaY / sc.faceAreaZ);
            final double specular = Math.max(0.0, Math.min(1.0, cr - 1.0));
            final BoxAndSolarArraySpacecraft b = new BoxAndSolarArraySpacecraft(x, y, z, s.sun, sc.wingAreaM2,
                    Vector3D.PLUS_J, cd, 0.0, 1.0 - specular, specular);
            dragCraft = b;
            radCraft = b;
        } else {
            dragCraft = new IsotropicDrag(sc.dragAreaM2, cd);
            radCraft = new IsotropicRadiationSingleCoefficient(sc.srpAreaM2, cr);
        }
        if (f.drag.enabled) {
            out.add(new DragForce(atmosphere(cfg, env, s), dragCraft));
        }
        if (f.srp.enabled) {
            out.add(new SolarRadiationPressure(s.sun, s.earth, radCraft));
        }
        if (f.albedo.enabled) {
            out.add(new KnockeRediffusedForceModel(s.sun, radCraft, Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                    Math.toRadians(15.0)));
        }
        if (f.relativity.enabled) {
            out.add(new Relativity(mu));
            if (f.lenseThirring) {
                out.add(new LenseThirringRelativity(mu, s.itrf));
            }
        }
        if (f.empirical.enabled) {
            final AttitudeProvider rtn = new LofOffset(s.gcrf, LOFType.QSW);
            final double[] a = {f.empirical.radial, f.empirical.transverse, f.empirical.normal};
            final Vector3D[] axis = {Vector3D.PLUS_I, Vector3D.PLUS_J, Vector3D.PLUS_K};
            final String[] n = {"R", "T", "N"};
            for (int i = 0; i < 3; i++) {
                final PolynomialAccelerationModel m = new PolynomialAccelerationModel(EMPIRICAL_PREFIX + n[i], null, 0);
                m.getParametersDrivers().get(0).setValue(a[i]);
                out.add(new ParametricAcceleration(axis[i], rtn, m));
            }
        }
        return out;
    }

    static Atmosphere atmosphere(final RunConfig cfg, final Environment env, final OrekitSession s) {
        switch (cfg.forces.dragModel) {
            case JB2008:
                return new JB2008(new Jb2008Adapter(env.jb2008), s.sun, s.earth, s.utc);
            case HARRIS_PRIESTER:
                return new HarrisPriester(s.sun, s.earth);
            case EXPONENTIAL:
                // the 800-900 km layer of the Vallado exponential table (LEO, Sentinel-3 altitude)
                return new SimpleExponentialAtmosphere(s.earth, 1.170e-14, 800.0e3, 124.64e3);
            case NRLMSISE00:
            default:
                return new NRLMSISE00(new MsisAdapter(env.weather), s.sun, s.earth, s.utc);
        }
    }

    /** Drivers of the drag and radiation coefficients among the force models' parameters. */
    public static List<ParameterDriver> coefficientDrivers(final List<ForceModel> forces, final boolean drag,
                                                         final boolean radiation) {
        final List<ParameterDriver> out = new ArrayList<>();
        for (final ForceModel fm : forces) {
            for (final ParameterDriver d : fm.getParametersDrivers()) {
                final String n = d.getName();
                // only the coefficient itself: Orekit also exposes a global scale factor, which is
                // perfectly correlated with it (estimating both makes the problem singular)
                if (drag && fm instanceof DragForce && n.equals(DragSensitive.DRAG_COEFFICIENT)
                        || radiation && fm instanceof SolarRadiationPressure
                        && n.equals(RadiationSensitive.REFLECTION_COEFFICIENT)) {
                    if (!out.contains(d)) {
                        out.add(d);
                    }
                }
            }
        }
        return out;
    }

    /** Orbit-determination propagator builder (Cartesian, Dormand-Prince 8(5,3)). */
    public static NumericalPropagatorBuilder builder(final Orbit orbit0, final double mass, final RunConfig cfg,
                                                     final List<ForceModel> forces, final AttitudeProvider attitude) {
        final RunConfig.Integrator it = cfg.integrator;
        final NumericalPropagatorBuilder b = new NumericalPropagatorBuilder(
                new CartesianOrbit(orbit0), new DormandPrince853IntegratorBuilder(it.minStepSeconds, it.maxStepSeconds,
                        ToleranceProvider.getDefaultToleranceProvider(it.positionToleranceM)),
                PositionAngleType.TRUE, 1.0, attitude);
        b.setMass(mass);
        for (final ForceModel fm : forces) {
            b.addForceModel(fm);
        }
        return b;
    }

    /** Forecast propagator from an initial state. */
    public static NumericalPropagator propagator(final SpacecraftState s0, final RunConfig cfg,
                                                 final List<ForceModel> forces, final AttitudeProvider attitude) {
        final RunConfig.Integrator it = cfg.integrator;
        final double[][] tol = ToleranceProvider.getDefaultToleranceProvider(it.positionToleranceM)
                .getTolerances(s0.getOrbit(), OrbitType.CARTESIAN);
        final NumericalPropagator p = new NumericalPropagator(
                new DormandPrince853Integrator(it.minStepSeconds, it.maxStepSeconds, tol[0], tol[1]), attitude);
        p.setOrbitType(OrbitType.CARTESIAN);
        for (final ForceModel fm : forces) {
            p.addForceModel(fm);
        }
        p.setInitialState(s0);
        return p;
    }

    // ---- space-weather adapters: Orekit atmosphere models fed by the program's cutoff-safe weather

    /** NRLMSISE-00 inputs from the run's space-weather provider (daily Ap for all ap slots). */
    static final class MsisAdapter implements NRLMSISE00InputParameters {
        private final org.ursc.trajectory.forces.drag.SpaceWeatherProvider w;

        MsisAdapter(final org.ursc.trajectory.forces.drag.SpaceWeatherProvider w) {
            this.w = w;
        }

        @Override
        public AbsoluteDate getMinDate() {
            return AbsoluteDate.PAST_INFINITY;
        }

        @Override
        public AbsoluteDate getMaxDate() {
            return AbsoluteDate.FUTURE_INFINITY;
        }

        @Override
        public double getDailyFlux(final AbsoluteDate date) {
            return w.getDailyF107(OrekitDates.fromOrekit(date));
        }

        @Override
        public double getAverageFlux(final AbsoluteDate date) {
            return w.getAverageF107(OrekitDates.fromOrekit(date));
        }

        @Override
        public double[] getAp(final AbsoluteDate date) {
            final double ap = w.getDailyAp(OrekitDates.fromOrekit(date));
            return new double[] {ap, ap, ap, ap, ap, ap, ap};
        }
    }

    /** JB2008 inputs from the run's (cutoff-safe) SOLFSMY/DTC provider. */
    static final class Jb2008Adapter implements JB2008InputParameters {
        private final org.ursc.trajectory.forces.drag.Jb2008Inputs j;

        Jb2008Adapter(final org.ursc.trajectory.forces.drag.Jb2008Inputs j) {
            this.j = j;
        }

        private static org.ursc.trajectory.time.AbsoluteDate d(final AbsoluteDate date) {
            return OrekitDates.fromOrekit(date);
        }

        @Override
        public AbsoluteDate getMinDate() {
            return AbsoluteDate.PAST_INFINITY;
        }

        @Override
        public AbsoluteDate getMaxDate() {
            return AbsoluteDate.FUTURE_INFINITY;
        }

        @Override
        public double getF10(final AbsoluteDate date) {
            return j.getF10(d(date));
        }

        @Override
        public double getF10B(final AbsoluteDate date) {
            return j.getF10B(d(date));
        }

        @Override
        public double getS10(final AbsoluteDate date) {
            return j.getS10(d(date));
        }

        @Override
        public double getS10B(final AbsoluteDate date) {
            return j.getS10B(d(date));
        }

        @Override
        public double getXM10(final AbsoluteDate date) {
            return j.getXM10(d(date));
        }

        @Override
        public double getXM10B(final AbsoluteDate date) {
            return j.getXM10B(d(date));
        }

        @Override
        public double getY10(final AbsoluteDate date) {
            return j.getY10(d(date));
        }

        @Override
        public double getY10B(final AbsoluteDate date) {
            return j.getY10B(d(date));
        }

        @Override
        public double getDSTDTC(final AbsoluteDate date) {
            return j.getDSTDTC(d(date));
        }
    }

    /**
     * Measured attitude (PROQUA quaternions through the program's cutoff-safe reader, nominal
     * outside the files) as an Orekit attitude provider. The field version carries the
     * attitude as a constant (its sensitivity to the orbit state is not needed for drag/SRP
     * partials at OD accuracy).
     */
    static final class MeasuredAttitude implements AttitudeProvider {
        private final org.ursc.trajectory.attitude.AttitudeProvider measured;
        private final OrekitSession s;

        MeasuredAttitude(final org.ursc.trajectory.attitude.AttitudeProvider measured, final OrekitSession s) {
            this.measured = measured;
            this.s = s;
        }

        private Rotation rotation(final PVCoordinates pvGcrf, final AbsoluteDate date) {
            final org.ursc.trajectory.propagation.SpacecraftState st = new org.ursc.trajectory.propagation.SpacecraftState(
                    new org.ursc.trajectory.orbits.CartesianOrbit(new org.ursc.trajectory.orbits.PVCoordinates(
                            OrekitDates.fromOrekit(pvGcrf.getPosition()), OrekitDates.fromOrekit(pvGcrf.getVelocity())),
                            org.ursc.trajectory.frames.FramesFactory.getGCRF(), OrekitDates.fromOrekit(date),
                            Constants.EIGEN5C_EARTH_MU));
            final BodyFrame b = measured.attitude(st);
            // rotation from GCRF to body: maps the body axes (expressed in GCRF) onto +I, +J
            return new Rotation(OrekitDates.toOrekit(b.x()), OrekitDates.toOrekit(b.y()), Vector3D.PLUS_I, Vector3D.PLUS_J);
        }

        @Override
        public Attitude getAttitude(final PVCoordinatesProvider pv, final AbsoluteDate date, final Frame frame) {
            final PVCoordinates g = pv.getPVCoordinates(date, s.gcrf);
            final Attitude inGcrf = new Attitude(date, s.gcrf, rotation(g, date), Vector3D.ZERO, Vector3D.ZERO);
            return inGcrf.withReferenceFrame(frame);
        }

        @Override
        public <T extends CalculusFieldElement<T>> FieldAttitude<T> getAttitude(final FieldPVCoordinatesProvider<T> pv,
                                                                                final FieldAbsoluteDate<T> date, final Frame frame) {
            final AbsoluteDate d = date.toAbsoluteDate();
            final PVCoordinates g = pv.getPVCoordinates(date, s.gcrf).toPVCoordinates();
            final Field<T> field = date.getField();
            final Attitude a = new Attitude(d, s.gcrf, rotation(g, d), Vector3D.ZERO, Vector3D.ZERO).withReferenceFrame(frame);
            return new FieldAttitude<>(date, frame, new FieldRotation<>(field, a.getRotation()),
                    FieldVector3D.getZero(field), FieldVector3D.getZero(field));
        }
    }
}
