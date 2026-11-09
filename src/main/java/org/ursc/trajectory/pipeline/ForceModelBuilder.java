package org.ursc.trajectory.pipeline;

import org.ursc.trajectory.bodies.CelestialBodyFactory;
import org.ursc.trajectory.bodies.OneAxisEllipsoid;
import org.ursc.trajectory.config.PropagationConfig;
import org.ursc.trajectory.forces.drag.Atmosphere;
import org.ursc.trajectory.forces.drag.ExponentialAtmosphere;
import org.ursc.trajectory.forces.drag.HarrisPriesterAtmosphere;
import org.ursc.trajectory.forces.drag.JB2008Atmosphere;
import org.ursc.trajectory.forces.drag.NRLMSISE00Atmosphere;
import org.ursc.trajectory.math.Constants;
import org.ursc.trajectory.ode.DormandPrince54Integrator;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.propagation.SampledPropagator;
import org.ursc.trajectory.propagation.SpacecraftState;

/** Maps the {@link RunConfig} force switches and model choices onto a numerical propagator. */
public final class ForceModelBuilder {

    /** Gravity degree used for the covariance (state-transition) propagations. */
    public static final int COVARIANCE_GRAVITY_DEGREE = 12;

    private ForceModelBuilder() {
    }

    /**
     * @param simplified if true, a reduced model for state-transition (covariance) propagation:
     *                   gravity truncated to degree 12, no tides, relativity or Earth radiation
     */
    public static SampledPropagator build(final SpacecraftState s0, final double cd, final double cr,
                                          final RunConfig cfg, final Environment env, final boolean simplified) {
        final RunConfig.Forces f = cfg.forces;
        final RunConfig.Spacecraft sc = cfg.spacecraft;
        final RunConfig.Integrator it = cfg.integrator;
        final OneAxisEllipsoid earth = new OneAxisEllipsoid(Constants.EARTH_EQUATORIAL_RADIUS, Constants.EARTH_FLATTENING);
        final PropagationConfig c = new PropagationConfig()
                .initialState(s0)
                .integrator(new DormandPrince54Integrator(it.absTol, it.relTol, it.minStepSeconds, it.maxStepSeconds), 60.0)
                .outputType(OrbitType.CARTESIAN);
        if (f.gravity.enabled) {
            final int deg = simplified ? Math.min(COVARIANCE_GRAVITY_DEGREE, env.gravityDegree) : env.gravityDegree;
            final int ord = Math.min(deg, env.gravityOrder);
            c.gravityField(env.gravity, deg, ord);
        }
        if (f.sun.enabled) {
            c.thirdBody(CelestialBodyFactory.getSun());
        }
        if (f.moon.enabled) {
            c.thirdBody(CelestialBodyFactory.getMoon());
        }
        if (f.drag.enabled) {
            c.drag(atmosphere(cfg, env, earth), cd, sc.dragAreaM2);
        }
        if (f.srp.enabled) {
            c.solarRadiationPressure(CelestialBodyFactory.getSun(), cr, sc.srpAreaM2);
        }
        if (sc.shape == RunConfig.ShapeModel.BOX_WING) {
            c.boxWing(sc.faceAreaX, sc.faceAreaY, sc.faceAreaZ, sc.wingAreaM2, Math.toRadians(sc.arrayCantDeg));
            if (env.attitude != null) {
                c.attitude(env.attitude);
            }
        }
        if (!simplified) {
            if (f.albedo.enabled) {
                c.earthRadiation(CelestialBodyFactory.getSun(), cr, sc.srpAreaM2);
            }
            if (f.solidTides.enabled) {
                c.solidTides(true);
            }
            if (f.oceanTides.enabled) {
                c.oceanTides(true);
            }
            if (f.relativity.enabled) {
                c.relativity(true, f.lenseThirring);
            }
            if (f.empirical.enabled) {
                c.empiricalAcceleration(f.empirical.radial, f.empirical.transverse, f.empirical.normal);
            }
        }
        return (SampledPropagator) c.build();
    }

    static Atmosphere atmosphere(final RunConfig cfg, final Environment env, final OneAxisEllipsoid earth) {
        switch (cfg.forces.dragModel) {
            case JB2008:
                return new JB2008Atmosphere(earth, env.jb2008);
            case HARRIS_PRIESTER:
                return new HarrisPriesterAtmosphere();
            case EXPONENTIAL:
                return new ExponentialAtmosphere(earth);
            case NRLMSISE00:
            default:
                return new NRLMSISE00Atmosphere(earth, env.weather);
        }
    }
}
