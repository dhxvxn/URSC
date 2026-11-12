# Architecture

Java 17. **Every OD + forecast and propagation run executes on the Orekit engine**
(Orekit 13.1.2 + Hipparchus 4.0.2, Apache 2.0, shaded into the executable jar): the
package `orekit` builds the Orekit data context, GPS products, force models, attitude,
carrier-phase batch least squares and forecast. The program's own packages below
provide the GUI, the data catalogue, file formats (OEM, POEORB, CSV), the cutoff-safe
EOP / space-weather providers that feed Orekit, the single-point positions used to
start the orbit, and the stand-alone research CLI commands (`propagate`, `decay`,
`compare`, `fit`, `backtest`, ...), which still use the earlier in-house physics
library. Root package: `org.ursc.trajectory`.

## Run engine (Orekit)

| Class | Role (Orekit classes used) |
|-------|----------------------------|
| `orekit/OrekitSession` | per-run `LazyLoadedDataContext`: leap seconds, EOP (`EopHistoryLoader`), gravity (`ICGEMFormatReader`), FES2004 (`FESCnmSnmReader`), DE440 |
| `orekit/OrekitGpsProducts` | `SP3Parser`, `RinexClockParser`, `RinexNavigationParser`, `GNSSPropagator` behind the product-tier resolver |
| `pipeline/ObsLoader.loadWithOrekit` | `RinexObservationParser` for GNSSRD |
| `orekit/OrekitDynamics` | force models, `LofOffset` attitude, `NumericalPropagatorBuilder` / `NumericalPropagator`, Dormand-Prince 8(5,3) |
| `orekit/OrekitCarrierPhaseOd` | `BatchLSEstimator`, `OneWayGNSSRange`, `OneWayGNSSPhase`, `AmbiguityDriver`, Shapiro and on-board-antenna modifiers |
| `orekit/SparseGaussNewtonOptimizer` | Hipparchus `LeastSquaresOptimizer` forming the normal equations from the sparse Jacobian rows |
| `orekit/OrekitForecast` | forecast propagation, covariance through `MatricesHarvester` |
| `pipeline/OdForecastPipeline`, `PropagateFromState` | the two run workflows |

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

- **One engine for every run** — Orekit, bundled, so the program still runs offline.
- **Validated, not just compiling** — models are checked against published
  reference values (see [PHYSICS.md](PHYSICS.md)); `mvn test` is the gate.
- **SI units, GCRF inertial frame** for all force-model accelerations.
- **Data is generated, not transcribed** — coefficient tables come from a parser
  run over the authoritative source, eliminating transcription error.

## Agreed merged engine diagram

See [the user-approved architecture](MERGED_ARCHITECTURE.md): batch OD, physical
forces and numerical propagation form one conceptual estimation/prediction engine.
The diagram is preserved for a later algorithm refactor.
