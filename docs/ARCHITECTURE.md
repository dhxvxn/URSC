# Architecture

The library is plain Java 17 with **no runtime dependencies** (JUnit for tests
only). Everything is built around a few small interfaces so new models plug in
without touching the engine. Root package: `org.ursc.trajectory`.

## Package map

| Package | Responsibility |
|---------|----------------|
| `math` | `Vector3D`, `RotationMatrix`, physical `Constants` |
| `time` | `AbsoluteDate`, time scales (TAI/UTC/TT/UT1/GPS), leap seconds |
| `frames` | `Frame`, kinematic `Transform`, `FramesFactory` (GCRF↔ITRF), IAU-2006/2000B model, EOP providers |
| `bodies` | `CelestialBody` (Earth/Sun/Moon), `OneAxisEllipsoid`, `GeodeticPoint` |
| `orbits` | `Orbit` + `Cartesian`/`Keplerian`/`Equinoctial`, `PVCoordinates`, conversions |
| `forces` | `ForceModel` and all perturbations (`gravity`, `drag`, `radiation` subpackages) |
| `ode` | `ODEIntegrator` (RK4, Dormand-Prince 5(4)), dense-output interpolator |
| `propagation` | `Propagator`/`SampledPropagator`, `SpacecraftState`, `numerical` (Cowell), `semianalytical` (DSST), `analytical` (Kepler), `events`, `sampling` |
| `analysis` | `DecayPredictor` (lifetime + Monte-Carlo), `ModelComparison` |
| `config` | `PropagationConfig` (builder), `ScenarioLoader` (`.properties`) |
| `app` | `Cli` dispatcher, `Args`, subcommands, terminal rendering (`term`) |
| `io` | CSV ephemeris writer, TLE parser |

## Extension points

Every capability is an interface you can implement and register — no engine
changes required.

| Interface | File | Add one to… |
|-----------|------|-------------|
| `ForceModel` | `forces/ForceModel.java` | model a new perturbation |
| `Atmosphere` | `forces/drag/Atmosphere.java` | add a density model |
| `SpaceWeatherProvider` | `forces/drag/SpaceWeatherProvider.java` | feed F10.7 / Ap |
| `ODEIntegrator` | `ode/ODEIntegrator.java` | add an integrator |
| `EventDetector` | `propagation/events/EventDetector.java` | detect an event (g-stop) |
| `StepHandler` | `propagation/sampling/StepHandler.java` | consume sampled output |
| `EopProvider` | `frames/EopProvider.java` | supply Earth-orientation parameters |
| `SampledPropagator` | `propagation/SampledPropagator.java` | add a propagation engine |

### Adding a force model

```java
public final class MyPerturbation implements ForceModel {
    @Override public Vector3D acceleration(SpacecraftState state) {
        // return the acceleration (m/s^2) in the state's inertial (GCRF) frame
    }
    @Override public String getName() { return "MyPerturbation"; }
}
```

Register it: `propagator.addForceModel(new MyPerturbation())`, or add a
`PropagationConfig` toggle plus a `force.*` key in `ScenarioLoader`. The numerical
propagator sums all registered models; the DSST propagator orbit-averages the same
set — so one implementation serves both engines.

## Propagation engines

Both implement `SampledPropagator` (force models + step handlers + events), so the
CLI, `DecayPredictor` and `ModelComparison` work with either:

- **`numerical/NumericalPropagator`** — Cowell integration of the full osculating
  equations with a pluggable `ODEIntegrator`; dense-output sampling and g-stop
  events.
- **`semianalytical/DSSTPropagator`** — numerically-averaged mean-element DSST:
  removes the fast angle by orbit-averaging, steps the six mean equinoctial
  elements in large steps (~20× faster at 30 days, ~200× at 1 year). Outputs mean
  elements.
- **`analytical/KeplerianPropagator`** — two-body baseline.

## Bundled data

Large reference tables are generated verbatim from authoritative public sources and
stored as classpath resources or generated `*Data.java`, never hand-typed:

- EGM96 gravity to d/o 70 — `resources/gravity/egm96s_to70.gfc.gz`
- FES2004 ocean-tide subset — `resources/gravity/fes2004_8const.dat`
- NRLMSISE-00 coefficients — `forces/drag/msis/Nrlmsise00Data.java` (from the SOFA-adjacent reference)
- IAU-2000B nutation (77 terms) — `frames/Nutation2000BData.java` (from SOFA `nut00b.c`)

## Design principles

- **Zero runtime dependencies** — the physics core is fully auditable.
- **Validated, not just compiling** — models are checked against published
  reference values (see [PHYSICS.md](PHYSICS.md)); `mvn test` is the gate.
- **SI units, GCRF inertial frame** for all force-model accelerations.
- **Data is generated, not transcribed** — coefficient tables come from a parser
  run over the authoritative source, eliminating transcription error.
