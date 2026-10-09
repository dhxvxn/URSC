package org.ursc.trajectory.config;

import java.util.ArrayList;
import java.util.List;

import org.ursc.trajectory.bodies.CelestialBody;
import org.ursc.trajectory.forces.EmpiricalAcceleration;
import org.ursc.trajectory.forces.ForceModel;
import org.ursc.trajectory.forces.NewtonianAttraction;
import org.ursc.trajectory.forces.Relativity;
import org.ursc.trajectory.forces.ThirdBodyAttraction;
import org.ursc.trajectory.forces.drag.Atmosphere;
import org.ursc.trajectory.forces.drag.DragForce;
import org.ursc.trajectory.forces.drag.IsotropicDrag;
import org.ursc.trajectory.forces.gravity.GravityField;
import org.ursc.trajectory.forces.gravity.SolidTides;
import org.ursc.trajectory.forces.gravity.SphericalHarmonicGravity;
import org.ursc.trajectory.forces.radiation.EarthRadiationPressure;
import org.ursc.trajectory.forces.radiation.IsotropicRadiationSingleCoefficient;
import org.ursc.trajectory.forces.radiation.SolarRadiationPressure;
import org.ursc.trajectory.ode.ODEIntegrator;
import org.ursc.trajectory.orbits.OrbitType;
import org.ursc.trajectory.propagation.SpacecraftState;
import org.ursc.trajectory.propagation.numerical.NumericalPropagator;

/**
 * Fluent builder assembling a fully-configured {@link NumericalPropagator}. Every
 * choice &mdash; which force models are active, their parameters, the integrator,
 * the output representation &mdash; is set here, so the dynamics of a run are
 * described by data rather than code. This is the central "plug in what you need"
 * entry point of the library.
 */
public final class PropagationConfig {

    private SpacecraftState initialState;
    private ODEIntegrator integrator;
    private double initialStep = 60.0;
    private OrbitType outputType = OrbitType.KEPLERIAN;
    private double outputStep = 0.0;

    // central / gravity
    private boolean useGravityField = false;
    private GravityField gravityField;
    private int gravityDegree;
    private int gravityOrder;
    private double centralMu;

    // perturbations
    private final List<CelestialBody> thirdBodies = new ArrayList<>();
    private Atmosphere atmosphere;
    private double dragCoefficient;
    private double dragArea;
    private boolean useDrag = false;
    private CelestialBody srpSun;
    private double reflectionCoefficient;
    private double srpArea;
    private boolean useSrp = false;
    private boolean useRelativity = false;
    private boolean relativityLenseThirring = false;
    private boolean useSolidTides = false;
    private boolean useEarthRadiation = false;
    private CelestialBody earthRadiationSun;
    private double earthRadiationCr;
    private double earthRadiationArea;
    private double[] empiricalRtn;

    public PropagationConfig initialState(final SpacecraftState state) {
        this.initialState = state;
        this.centralMu = state.getMu();
        return this;
    }

    public PropagationConfig integrator(final ODEIntegrator integrator, final double initialStep) {
        this.integrator = integrator;
        this.initialStep = initialStep;
        return this;
    }

    public PropagationConfig outputType(final OrbitType type) {
        this.outputType = type;
        return this;
    }

    public PropagationConfig outputStep(final double step) {
        this.outputStep = step;
        return this;
    }

    /** Use a spherical-harmonic gravity field (includes the central term). */
    public PropagationConfig gravityField(final GravityField field, final int degree, final int order) {
        this.useGravityField = true;
        this.gravityField = field;
        this.gravityDegree = degree;
        this.gravityOrder = order;
        return this;
    }

    public PropagationConfig thirdBody(final CelestialBody body) {
        this.thirdBodies.add(body);
        return this;
    }

    public PropagationConfig drag(final Atmosphere atmosphere, final double cd, final double area) {
        this.useDrag = true;
        this.atmosphere = atmosphere;
        this.dragCoefficient = cd;
        this.dragArea = area;
        return this;
    }

    public PropagationConfig solarRadiationPressure(final CelestialBody sun,
                                                    final double cr, final double area) {
        this.useSrp = true;
        this.srpSun = sun;
        this.reflectionCoefficient = cr;
        this.srpArea = area;
        return this;
    }

    public PropagationConfig relativity(final boolean enabled) {
        return relativity(enabled, false);
    }

    public PropagationConfig relativity(final boolean enabled, final boolean lenseThirring) {
        this.useRelativity = enabled;
        this.relativityLenseThirring = lenseThirring;
        return this;
    }

    public PropagationConfig solidTides(final boolean enabled) {
        this.useSolidTides = enabled;
        return this;
    }

    public PropagationConfig earthRadiation(final CelestialBody sun, final double cr,
                                            final double area) {
        this.useEarthRadiation = true;
        this.earthRadiationSun = sun;
        this.earthRadiationCr = cr;
        this.earthRadiationArea = area;
        return this;
    }

    public PropagationConfig empiricalAcceleration(final double radial, final double transverse,
                                                   final double normal) {
        this.empiricalRtn = new double[] {radial, transverse, normal};
        return this;
    }

    /** Assemble the propagator. The caller may still add step handlers and events. */
    public NumericalPropagator build() {
        if (initialState == null) {
            throw new IllegalStateException("initial state is required");
        }
        if (integrator == null) {
            throw new IllegalStateException("integrator is required");
        }

        final NumericalPropagator propagator =
                new NumericalPropagator(initialState, integrator, initialStep);
        propagator.setOutputType(outputType);
        propagator.setOutputStep(outputStep);

        for (final ForceModel f : buildForceModels()) {
            propagator.addForceModel(f);
        }
        return propagator;
    }

    /** @return the ordered list of force models described by this configuration. */
    public List<ForceModel> buildForceModels() {
        final List<ForceModel> forces = new ArrayList<>();

        if (useGravityField) {
            forces.add(new SphericalHarmonicGravity(gravityField, gravityDegree, gravityOrder));
        } else {
            // central attraction must always be present
            forces.add(new NewtonianAttraction(centralMu));
        }
        for (final CelestialBody body : thirdBodies) {
            forces.add(new ThirdBodyAttraction(body));
        }
        if (useDrag) {
            forces.add(new DragForce(atmosphere, new IsotropicDrag(dragCoefficient, dragArea)));
        }
        if (useSrp) {
            forces.add(new SolarRadiationPressure(srpSun,
                    new IsotropicRadiationSingleCoefficient(reflectionCoefficient, srpArea)));
        }
        if (useSolidTides) {
            forces.add(new SolidTides());
        }
        if (useEarthRadiation) {
            forces.add(new EarthRadiationPressure(earthRadiationSun,
                    new IsotropicRadiationSingleCoefficient(earthRadiationCr, earthRadiationArea), 10, 20));
        }
        if (useRelativity) {
            forces.add(new Relativity(centralMu, relativityLenseThirring));
        }
        if (empiricalRtn != null) {
            forces.add(new EmpiricalAcceleration(empiricalRtn[0], empiricalRtn[1], empiricalRtn[2]));
        }
        return forces;
    }
}
