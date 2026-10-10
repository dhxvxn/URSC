# Usage guide

The URSC LEO Trajectory Predictor is a zero-dependency Java tool. This guide
covers building it, the terminal commands, and the full scenario configuration
reference.

## Build & run

Requires JDK 17+ and Maven.

```bash
mvn test       # compile + run the verification suite (should be all green)
mvn package    # build target/leo-trajectory-0.1.0.jar (runnable, Main-Class set)
```

Run a command either way:

```bash
java -jar target/leo-trajectory-0.1.0.jar <command> [options]
# or, without packaging:
java -cp target/classes org.ursc.trajectory.app.Cli <command> [options]
```

Output is colourised only when attached to a terminal; piping (`| cat`, `>
file`) yields clean, uncoloured text, and progress is written to **stderr** so
`stdout` stays parseable.

---

## Commands

### `propagate <scenario.properties>`
Propagate a scenario and print an element summary plus an in-terminal plot.

| Option | Default | Meaning |
|--------|---------|---------|
| `--plot alt\|sma\|ecc\|none` | `alt` | series drawn as an ASCII chart vs time |
| `--csv <file>` | — | also stream the full ephemeris to CSV |
| `--quiet` | off | suppress the progress bar |

```bash
java -jar …/leo-trajectory-0.1.0.jar propagate sample-scenario.properties --plot alt
```

### `decay <scenario.properties>`
Propagate to re-entry and report the decay date; optionally Monte-Carlo.

| Option | Default | Meaning |
|--------|---------|---------|
| `--reentry-altitude <m>` | `120000` | altitude treated as re-entry |
| `--max-years <y>` | `25` | propagation cap |
| `--monte-carlo <N>` | `0` | run N trials sampling drag & solar activity |
| `--seed <s>` | `42` | RNG seed (reproducible) |
| `--cd-sigma <frac>` | `0.1` | fractional 1σ on the drag coefficient |
| `--f107-range <lo,hi>` | `70,220` | uniform sampling range for F10.7 |
| `--quiet` | off | suppress progress |

```bash
java -jar …/leo-trajectory-0.1.0.jar decay sat.properties --monte-carlo 50 --seed 1
```

The deterministic run prints the decay date and an altitude-vs-time plot; the
Monte-Carlo run prints 5th/median/95th-percentile decay dates and a histogram.

### `compare <scenario.properties> --vary <key>=v1,v2,...`
Run several model variants over the same span and tabulate divergence.

| Option | Default | Meaning |
|--------|---------|---------|
| `--vary <key>=a,b,c` | required | scenario property to sweep (alias `atmosphere`→`drag.model`, `gravity`→`gravity.model`) |
| `--duration <s>` | scenario's | span to run each variant |
| `--sample-step <s>` | scenario's `output.step` | grid for the position-divergence metric |

```bash
java -jar …/leo-trajectory-0.1.0.jar compare sat.properties \
     --vary atmosphere=exponential,harris-priester,nrlmsise00
java -jar …/leo-trajectory-0.1.0.jar compare sat.properties --vary gravity.order=0,8,20
java -jar …/leo-trajectory-0.1.0.jar compare sat.properties --vary propagator=numerical,dsst
```

The table shows each variant's final elements and the maximum inertial position
difference against the first (reference) variant. Note: comparing a DSST
(mean-element) variant against a numerical (osculating) one is not
position-for-position comparable — read the element columns for the secular
agreement.

### `tle <tle-file>`
Parse a TLE and optionally propagate it (approximate osculating seed; no SGP4
mean-to-osculating recovery).

| Option | Default | Meaning |
|--------|---------|---------|
| `--duration <s>` | — | if set, propagate this long (gravity 10×10 + Sun/Moon + drag + SRP) |
| `--gravity-degree <n>` | `10` | EGM96 degree/order used while propagating |
| `--mass <kg>` | `500` | spacecraft mass |
| `--plot …`, `--quiet` | | as for `propagate` |

### `help`, `version`
Print the command list / the version string.

---

## Scenario file reference

A scenario is a Java `.properties` file. Every line is optional unless marked
**required**; omitted keys use the default shown. See `sample-scenario.properties`
for a fully-commented example.

### Epoch & frames
| Key | Default | Notes |
|-----|---------|-------|
| `epoch` | **required** | UTC, `YYYY-MM-DDThh:mm:ss` |
| `frames.model` | `iau2006` | `iau2006` (precession+nutation+ERA+polar motion) or `simple` (GMST only) |
| `eop.file` | — | IERS EOP C04 (IAU2000) file; improves UT1/polar-motion accuracy |
| `eop.dut1` | `0.0` | constant UT1−UTC (s) when no file |
| `eop.xp`, `eop.yp` | `0.0` | constant polar motion (arcsec) when no file |

### Initial orbit
| Key | Default | Notes |
|-----|---------|-------|
| `orbit.type` | `keplerian` | `keplerian` or `cartesian` |
| `orbit.a` / `orbit.altitude` | — | semi-major axis (m) *or* altitude above equatorial radius (m) |
| `orbit.e`, `orbit.i`, `orbit.raan`, `orbit.pa`, `orbit.anomaly` | 0 | angles in degrees |
| `orbit.anomaly.type` | `MEAN` | `MEAN`, `ECCENTRIC`, or `TRUE` |
| `orbit.position`, `orbit.velocity` | — | `x,y,z` / `vx,vy,vz` (m, m/s) for `cartesian` |

### Spacecraft
| Key | Default | Notes |
|-----|---------|-------|
| `spacecraft.mass` | `1000` | kg |
| `spacecraft.dragArea`, `spacecraft.dragCoefficient` | `1.0`, `2.2` | drag cross-section (m²), Cd |
| `spacecraft.srpArea`, `spacecraft.reflectionCoefficient` | `1.0`, `1.5` | SRP cross-section (m²), Cr |

### Force models (each an independent toggle)
| Key | Default | Notes |
|-----|---------|-------|
| `force.gravity` | `true` | central + spherical-harmonic field |
| `gravity.model` | `egm96` | `egm96` (bundled, d/o ≤70) or `zonal` (J2–J6) |
| `gravity.degree`, `gravity.order` | `20`, `20` | truncation (≤70 for EGM96) |
| `gravity.file` | — | external normalised ICGEM/EGM `.gfc` overrides the bundled field |
| `gravity.mu`, `gravity.radius` | EGM96 | GM (m³/s²), reference radius (m) |
| `force.thirdBody.sun`, `force.thirdBody.moon` | `false` | luni-solar third-body |
| `force.drag` | `false` | atmospheric drag |
| `drag.model` | `exponential` | `exponential`, `harris-priester`, `nrlmsise00` |
| `drag.harrisPriester.exponent` | `2.0` | cosine exponent (2 equatorial … 6 polar) |
| `spaceWeather.source` | `constant` | `constant` or `file` (NRLMSISE-00) |
| `spaceWeather.file` | — | CelesTrak CSSI `SW-All.csv` |
| `spaceWeather.f107`, `spaceWeather.f107a`, `spaceWeather.ap` | `150,150,4` | constant values / fallback |
| `force.srp` | `false` | solar radiation pressure (conical eclipse) |
| `force.solidTides` | `false` | IERS-2010 solid Earth tides (Sun+Moon) |
| `force.oceanTides` | `false` | FES2004 8-constituent ocean tides |
| `force.earthRadiation` | `false` | Knocke-Ries albedo + IR (uses SRP Cr/area) |
| `force.relativity` | `false` | Schwarzschild correction |
| `force.relativity.lenseThirring` | `false` | add frame-dragging term |
| `force.empirical` | `false` | constant RTN acceleration |
| `empirical.radial`, `empirical.transverse`, `empirical.normal` | `0` | m/s² |

### Propagator & integrator
| Key | Default | Notes |
|-----|---------|-------|
| `propagator` | `numerical` | `numerical` (Cowell, osculating) or `dsst` (mean elements) |
| `dsst.step` | `43200` | DSST mean-element step (s) |
| `dsst.averagingPoints` | `24` | quadrature nodes per revolution |
| `integrator.type` | `dp54` | `dp54` (adaptive) or `rk4` (fixed) — numerical only |
| `integrator.initialStep` | `60` | s |
| `integrator.minStep`, `integrator.maxStep` | `1e-3`, `300` | s (adaptive bounds) |
| `integrator.absTol`, `integrator.relTol` | `1e-6`, `1e-9` | DP54 error tolerances |

### Run span, output & events
| Key | Default | Notes |
|-----|---------|-------|
| `propagation.duration` | `86400` | seconds from epoch |
| `propagation.end` | — | absolute UTC end date (overrides duration) |
| `output.type` | `keplerian` | `keplerian`, `cartesian`, `equinoctial` |
| `output.step` | `60` | output/sampling grid (s) |
| `output.file` | `ephemeris.csv` | CSV path |
| `event.reentryAltitude` | — | stop propagation at this geodetic altitude (m) |

### CSV columns
`epoch_utc, x_m, y_m, z_m, vx_ms, vy_ms, vz_ms, a_m, e, i_deg, raan_deg,
argPerigee_deg, trueAnomaly_deg, lat_deg, lon_deg, alt_m`.
