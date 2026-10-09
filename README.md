# URSC LEO Trajectory Predictor

A **modular numerical orbit-propagation library in Java** for determining and
predicting the **long-term trajectory of Low Earth Orbit (LEO) satellites**.

Every force model, integrator, orbit representation, and output is a pluggable
component, and every physical variable is selectable from configuration — so you
can start from a coarse two-body analysis and refine to a high-fidelity
multi-perturbation lifetime simulation by changing data, not code.

The architecture and physics are modelled on (but fully independent of) the
[Orekit](https://www.orekit.org/) library. It has **no runtime dependencies** —
the entire physics core is plain, auditable Java.

---

## Why this design

The request was a *modular* model where you "plug in necessary force models and
every variable is flexible to choose." That maps directly onto one extension
point and one configuration surface:

- **`ForceModel`** — the single interface every perturbation implements. The
  propagator simply sums whatever models you register. Add solid tides, albedo,
  thrust, a new atmosphere — implement `ForceModel`, register it, done.
- **`PropagationConfig` / `.properties` scenarios** — turn individual forces on
  and off and set their parameters, the integrator, tolerances, the orbit
  representation, and the output, all without recompiling.

---

## Features implemented

### Time
- Absolute dates on a uniform atomic timeline (sub-microsecond precision)
- Time scales: **TAI, UTC, TT, UT1, GPS**, with the full 1972–2017 leap-second table
- Leap-second-free durations; calendar ↔ instant conversion in any scale

### Geometry / frames
- Pseudo-inertial **GCRF/J2000** frame (propagation frame)
- Earth-fixed **ITRF** frame via Greenwich Mean Sidereal Time rotation
- Kinematic `Transform` (rotation + angular velocity → correct velocity transforms)
- `OneAxisEllipsoid` with Bowring geodetic conversion (latitude, longitude, altitude)

### Bodies
- Earth (central body), **Sun and Moon** low-precision analytical ephemerides
  (Montenbruck & Gill)

### Orbits
- **Cartesian, Keplerian, Equinoctial** representations with loss-free conversions
- Mean / eccentric / true anomaly handling (Kepler's equation solved by Newton)

### Force models (all pluggable via `ForceModel`)
| Model | Class | Notes |
|-------|-------|-------|
| Central attraction | `NewtonianAttraction` | point mass |
| **Spherical-harmonic gravity** | `SphericalHarmonicGravity` | Cunningham/Gottlieb recursion, degree/order selectable; default J2–J6 zonal, or load a full normalised field |
| **Third-body** | `ThirdBodyAttraction` | Sun, Moon, or any `CelestialBody` |
| **Atmospheric drag** | `DragForce` | pluggable `Atmosphere`: `ExponentialAtmosphere`, `HarrisPriesterAtmosphere` (diurnal bulge), **`NRLMSISE00Atmosphere`** (full empirical thermosphere, solar/geomagnetic driven via a `SpaceWeatherProvider`) |
| **Solar radiation pressure** | `SolarRadiationPressure` | conical umbra/penumbra eclipse model |
| **General relativity** | `Relativity` | Schwarzschild correction |
| **Empirical** | `EmpiricalAcceleration` | constant RTN, for unmodelled forces |

### Integrators (pluggable via `ODEIntegrator`)
- **Dormand-Prince 5(4)** adaptive step with error control (recommended)
- Classical **RK4** fixed step

### Propagation
- `NumericalPropagator` — Cowell integration of the summed forces
- `KeplerianPropagator` — analytical two-body baseline
- Dense (cubic-Hermite) output on a user-defined grid; multiple step handlers
- In-memory ephemeris collection and CSV export (with ground track)
- **Event detection** (g-stop with bracketing + bisection): apside, node, altitude
  (re-entry), eclipse

### I/O & configuration
- `.properties` scenario files (see `sample-scenario.properties`)
- CSV ephemeris writer
- TLE parser (approximate seeding)

---

## Build & run

Requires JDK 17+ and Maven.

```bash
mvn test          # compile + run the verification suite
mvn package       # build the jar

# run a scenario
java -cp target/classes org.ursc.trajectory.app.PropagatorApp sample-scenario.properties
```

Example output for a 600 km, 51.6° orbit over one day (gravity 6×0 + Sun + Moon +
Harris-Priester drag + SRP) shows a nodal regression of about **−4.55°/day**,
matching the classical J2 secular rate to within the contribution of the other
perturbations.

### Programmatic use

```java
AbsoluteDate epoch = new AbsoluteDate(2024, 1, 1, 0, 0, 0.0, TimeScalesFactory.getUTC());
KeplerianOrbit orbit = new KeplerianOrbit(
        Constants.EARTH_EQUATORIAL_RADIUS + 600_000, 0.001, Math.toRadians(51.6),
        0, Math.toRadians(30), 0, PositionAngle.MEAN,
        FramesFactory.getGCRF(), epoch, Constants.EARTH_MU);

NumericalPropagator propagator = new PropagationConfig()
        .initialState(new SpacecraftState(orbit, 500.0))
        .integrator(new DormandPrince54Integrator(1e-6, 1e-9, 1e-3, 300.0), 60.0)
        .gravityField(GravityFieldFactory.getDefaultZonalField(), 6, 0)
        .thirdBody(CelestialBodyFactory.getSun())
        .thirdBody(CelestialBodyFactory.getMoon())
        .drag(new HarrisPriesterAtmosphere(), 2.2, 2.0)
        .solarRadiationPressure(CelestialBodyFactory.getSun(), 1.5, 2.0)
        .outputType(OrbitType.KEPLERIAN)
        .build();

SpacecraftState finalState = propagator.propagate(epoch.shiftedBy(86400));
```

## Scenario configuration

See `sample-scenario.properties` — every force is an independent `force.* = true/false`
toggle with its own parameters; the integrator, orbit, spacecraft, output and
optional re-entry event are all data-driven.

## Adding a new force model

```java
public final class MyPerturbation implements ForceModel {
    public Vector3D acceleration(SpacecraftState state) {
        // return the acceleration (m/s^2) in the state's inertial frame
    }
    public String getName() { return "MyPerturbation"; }
}
```
Register it with `propagator.addForceModel(new MyPerturbation())` (or add a knob
to `PropagationConfig`). Nothing else changes.

---

## Verification

The test suite (`mvn test`) checks:
- loss-free orbit-parameter conversions and anomaly consistency
- specific-energy conservation under two-body dynamics over 10 orbits
- numerical vs. analytical Keplerian agreement to sub-metre over an hour
- J2 nodal regression matching the secular formula `dΩ/dt = -1.5 n J2 (Re/p)² cos i`
- time-scale offsets, leap seconds, and calendar round-trips
- **NRLMSISE-00 reproduces the official reference test case to 7 significant figures**
  (densities, total mass density, and both temperatures)

---

## Modelling assumptions & accuracy

This is a faithful first release focused on the perturbations that dominate
**long-term LEO** behaviour. Known simplifications (each is an isolated, clearly
marked extension point):

- GCRF→ITRF uses sidereal rotation only; precession, nutation and polar motion
  are neglected (sub-km over long LEO arcs).
- Sun/Moon ephemerides are low-precision analytical series (≈0.1° / few-hundred-km),
  ample for perturbation forcing.
- UT1 ≈ UTC (dUT1 = 0); no IERS Earth-orientation data yet.
- Atmosphere models range from static (exponential) through diurnal
  (Harris-Priester) to the full solar/geomagnetic-driven **NRLMSISE-00**; the
  bundled `SpaceWeatherProvider` is constant (plug in a CSSI/CelesTrak file
  reader for historical/forecast F10.7 and Ap).
- TLE seeding is approximate (no SGP4 mean-to-osculating recovery).

## Roadmap (toward broader Orekit parity + beyond)

Natural next modules, all building on the existing interfaces:
- **Atmosphere:** Jacchia-Bowman 2008, DTM2000; a file-backed `SpaceWeatherProvider`
  (NRLMSISE-00 itself is implemented)
- **Gravity:** time-dependent (tides) terms; embedded higher-degree EGM2008 subset
- **Perturbations:** solid & ocean tides, Earth albedo/IR, relativistic Lense-Thirring/De Sitter
- **Frames:** IAU-2006/2000A precession-nutation, polar motion, IERS EOP loading
- **Propagation:** semi-analytical (DSST) for very-long-term/lifetime runs, state-transition matrix & covariance propagation
- **Determination:** batch least-squares and Kalman/Unscented filters over measurements
- **Maneuvers:** impulsive and finite-burn models
- **I/O:** CCSDS OEM/OMM/OPM, SP3, RINEX

---

*Built as a reference-quality, extensible foundation. Contributions extend it one
`ForceModel`, `Atmosphere`, `ODEIntegrator`, or `Propagator` at a time.*
