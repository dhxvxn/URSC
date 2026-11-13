# Orbit determination: GNSS carrier phase on the Orekit engine

Every OD + forecast run determines the orbit in one way: an **Orekit batch least-squares fit
to the undifferenced ionosphere-free GPS code and carrier phase** recorded by the satellite
(the GNSSRD files). It then forecasts that orbit with the **Orekit numerical propagator**.
Single-point positions (SPP) from the code are used only to start the orbit; they are not an
orbit-determination method any more.

All physics on the run path is Orekit 13.1.2 (Apache 2.0), bundled into the single
executable jar so the program still runs offline:

| Step | Orekit classes |
|---|---|
| Data context (per run) | `LazyLoadedDataContext`, `DirectoryCrawler`, `EopHistoryLoader`, `ICGEMFormatReader`, `FESCnmSnmReader` |
| GNSS observations | `RinexObservationParser` (RINEX 3.03 GNSSRD) |
| GPS orbits / clocks | `SP3Parser` + SP3 ephemeris propagators, `RinexClockParser`, `RinexNavigationParser` + `GNSSPropagatorBuilder` / `GNSSPropagator` |
| Frames, time | `FramesFactory`-equivalent `Frames` of the context (GCRF, EME2000, ITRF IERS 2010), `TimeScales` (UTC, GPS, UT1) |
| Initial orbit | `IodGibbs`, then `BatchLSEstimator` with `Position` measurements |
| **Orbit determination** | **`BatchLSEstimator`** with **`OneWayGNSSRange`** + **`OneWayGNSSPhase`**, `QuadraticClockModel`, `AmbiguityDriver`, `ShapiroOneWayGNSSRange/PhaseModifier`, `OnBoardAntennaOneWayGNSSRange/PhaseModifier` |
| Dynamics | `NumericalPropagatorBuilder` / `NumericalPropagator`, `DormandPrince853Integrator`, `HolmesFeatherstoneAttractionModel`, `ThirdBodyAttraction` (DE440), `DragForce` + `NRLMSISE00` / `JB2008` / `HarrisPriester` / `SimpleExponentialAtmosphere`, `IsotropicDrag` / `BoxAndSolarArraySpacecraft`, `SolarRadiationPressure`, `KnockeRediffusedForceModel`, `SolidTides`, `OceanTides`, `Relativity`, `LenseThirringRelativity`, `ParametricAcceleration` |
| Attitude | `YawCompensation(NadirPointing)` in the CPOD body convention (nominal; matches PROQUA to 0.005°), or measured PROQUA quaternions through an Orekit `AttitudeProvider` |
| GPS antennas | `AntexLoader` (IGS igs20), `SatelliteType.buildAttitudeProvider` (GPS yaw steering), `OnBoardAntennaOneWayGNSSRange/PhaseModifier` with `FrequencyPattern`s; phase wind-up as an `EstimationModifier` |
| Forecast covariance | `MatricesHarvester` (state-transition and parameter Jacobians) |

Code: package `org.ursc.trajectory.orekit` (`OrekitSession`, `OrekitGpsProducts`,
`OrekitDynamics`, `OrekitCarrierPhaseOd`, `OrekitForecast`, `SparseGaussNewtonOptimizer`)
and `pipeline/OdForecastPipeline.java`. Tests: `OrekitCarrierPhaseOdTest`, `PipelineTest`.

## Flow of a run

1. **Orekit data context.** The run opens a fresh `OrekitSession` with the following contents:
   * the leap seconds, written from the program's own table (so Orekit and the rest of the
     program share one UTC);
   * the Earth orientation, sampled from the run's EOP file and held at its cut-off values
     after the cut-off;
   * the gravity field (bundled EGM96 to 70×70, or an ICGEM file);
   * the FES2004 ocean-tide coefficients;
   * the JPL DE440 ephemeris.

   Because the context is per run, nothing from one run's future can leak into another through
   Orekit's caches.
2. **Inputs.** The GNSSRD files are thinned to 10 s on the text (for slip detection), then
   parsed by Orekit's RINEX parser. The GPS products are read by Orekit's SP3, RINEX-clock and
   navigation parsers. The product tier and the cut-off-eligibility rules are unchanged:
   * Final > Rapid > Ultra-rapid > Broadcast, per epoch;
   * a product is used only if it was published by the cut-off;
   * broadcast messages are used only with Toe ≤ cut-off.
3. **Starting orbit.** Single-point positions from the code (every 300 s, PDOP and residual
   gates) give three positions for Orekit's Gibbs initial orbit determination. A quick Orekit
   batch fit to all the positions follows.
4. **Orbit determination (carrier phase).** An Orekit `BatchLSEstimator` fits the orbit (and
   optionally Cd/Cr) to every GPS satellite in view, every 30 s, using both measurement types:
   * `OneWayGNSSRange` on the ionosphere-free code P_IF;
   * `OneWayGNSSPhase` on the ionosphere-free phase L_IF (in metres, wavelength 1 m).

   The other estimated parameters are:
   * one **receiver clock** per epoch;
   * one **float ambiguity** per continuous phase arc.

   Orekit models the light time in the inertial frame (which includes the Sagnac effect), the
   GPS clock with its relativistic term, the Shapiro delay, and the antenna offset rotated by
   the attitude.
5. **Forecast.** The estimated state is propagated by the Orekit numerical propagator to the
   cut-off and over the forecast span. The formal covariance is propagated through Orekit's
   state-transition matrix: P(t) = [Φ Ψ] P₀ [Φ Ψ]ᵀ.

## Data editing

* **Arc breaks** happen at:
  * loss-of-lock flags;
  * gaps longer than 60 s;
  * Melbourne–Wübbena jumps larger than 2.5 wide-lane cycles (on the 10 s data);
  * a change of GPS product;
  * geometry-free jumps larger than 1 m.

  At LEO speed the ionosphere alone moves the geometry-free combination by 10–30 cm in 10 s. A
  tighter geometry-free test flagged 460 false slips in 6 h, so it is only a guard against gross
  jumps.
* **Residual-step slips.** After each Orekit solve, a step in an arc's phase residuals splits
  that arc (this catches equal L1/L2 slips, which no combination shows). Observations beyond 4
  a-posteriori sigmas are removed.
* **Re-weighting.** A final solve re-weights code and phase with their post-fit scatter, so the
  Orekit covariance and the variance factor (≈ 1) are realistic.

## Two implementation details worth knowing

* **Spans.** Orekit 13.1.2's one-way GNSS measurements attribute their partial derivatives to
  the *first* time span of a spanned parameter. Per-epoch clocks and per-arc ambiguities built
  as spans of one driver therefore give a singular problem. The estimator instead uses:
  * one Orekit `ObservableSatellite` per epoch (same propagator, its own clock driver);
  * one `AmbiguityDriver` per arc, through a cache keyed by arc.
* **Optimizer.** Every row of the Jacobian has about eight non-zeros (6 orbit, 1 clock, 1
  ambiguity), but there are about 800 parameters. The dense JᵀJ product of the generic Gauss–Newton
  optimizer dominated the run time. `SparseGaussNewtonOptimizer` (a Hipparchus
  `LeastSquaresOptimizer` plugged into Orekit's `BatchLSEstimator`) forms the same normal
  equations from the non-zeros only. The algorithm and convergence test are unchanged.

## Antenna and centre of mass

The GNSSRD header gives the antenna position in the spacecraft frame (`ANTENNA: DELTA`, for
Sentinel-3A: 2.881, −0.190, −0.794 m). This is measured from the frame origin, **not** from
the centre of mass. The antenna offset used is DELTA minus the centre of mass
(`spacecraft.comBodyX/Y/Z`). The Sentinel-3A profile sets (1.489, 0.217, 0.009) m, the launch
centre of gravity from the mission documentation. Without that correction the orbit is about
1.9 m off along-track.

## Settings

| Key | Default | Meaning |
|---|---|---|
| `od.arcHours` | 6 (Sentinel-3A profile: 12) | data arc ending at the cut-off |
| `od.phaseRateSeconds` | 30 (Sentinel-3A profile: 60) | carrier-phase sampling (slip detection uses 10 s) |
| `od.codeSigma`, `od.phaseSigma` | 1 m, 0.01 m | starting weights (re-estimated) |
| `od.antennaOffset` | true | apply the header antenna offset |
| `od.estimateCd`, `od.estimateCr` | false (Sentinel-3A profile: Cd **true**) | estimate drag / radiation coefficients |
| `od.gpsAntennaPhaseCentre` | true | GPS satellite antenna offsets/variations from ANTEX (precise orbits only) |
| `od.phaseWindUp` | true | carrier-phase wind-up |
| `window.parallelRuns` | 0 (automatic) | cut-offs of a window run computed at the same time (separate processes) |
| `od.samplingSeconds`, `od.maxPdop`, `od.maxSppRms`, `od.pseudorangeSigma` | 300 s, 6, 30 m, 5 m | code positions for the starting orbit only |
| `spacecraft.comBodyX/Y/Z` | 0 (Sentinel-3A profile: 1.489, 0.217, 0.009 m) | centre of mass in the frame of `ANTENNA: DELTA` |
| `integrator.positionToleranceM` | 1 mm | Orekit Dormand-Prince 8(5,3) tolerance |

## Validation

Validation consists of a closed-loop simulation and the two-month Sentinel-3A campaign below.

**Closed loop** (`OrekitCarrierPhaseOdTest`). The simulation uses:
* simulated dual-frequency code (0.3 m) and phase (2 mm);
* an independent light-time model;
* a receiver clock and random ambiguities;
* one wide-lane and one equal L1/L2 slip;
* a 1.66 m antenna offset;
* a start offset of 30 m and 3 cm/s.

The estimator recovers the orbit to 4.9 mm and 6 µm/s with a phase RMS of 1.9 mm, finds both
slips, and has a variance factor of 1.07. `PipelineTest` runs the full pipeline the same way:
4 mm at the cut-off and 7 mm after one day.

**Two months of Sentinel-3A** (3 Sep – 2 Nov 2024, 61 daily cut-offs at 05:59:42 UTC, the
showcase settings, broadcast products in cut-off-safe forecast mode, scored against POEORB):
see the [results](#two-month-results) below.

## Two-month results

| Run | Days | Successful | Median fit RMS | Median run time |
|---|---:|---:|---|---:|
| Before: SPP position fit (previous engine) | 61 | 61 | 2.702 m (positions) | 135 s |
| After: carrier-phase OD (in-house engine) | 61 | 61 | 0.069 m (carrier phase) | 191 s |
| Final: carrier-phase OD on the Orekit engine | 61 | 61 | 0.072 m (carrier phase) | 280 s |

**3-D position error against POEORB (m), median over the scored days** (95th percentile in brackets):

| Horizon | Before | After | Final | Change (final vs before) |
|---|---:|---:|---:|---:|
| At cut-off | 1.57 (3.21) | 0.78 (2.15) | 0.89 (2.22) | -44 % |
| +1 h | 1.93 (4.48) | 1.26 (4.52) | 1.43 (4.68) | -26 % |
| +6 h | 9.62 (712) | 8.80 (713) | 8.61 (714) | -10 % |
| +1 day | 94.91 (4,211) | 92.74 (4,212) | 93.06 (4,213) | -2 % |
| +3 days | 1,023 (16,355) | 1,020 (16,361) | 1,000 (16,404) | -2 % |
| +7 days | 8,208 (42,008) | 8,205 (42,014) | 8,131 (42,118) | -1 % |

Carrier phase roughly halves the orbit error at the cut-off and reduces it by a quarter one hour later. Beyond a few hours every run is limited by atmospheric drag (near solar maximum, with space weather persisted from the cut-off), so the 1-day and 7-day errors are unchanged. Daily values and the raw CSVs: [reports/two-month-carrier-phase-orekit](reports/two-month-carrier-phase-orekit/README.md).

## Improvements after the first two-month campaign

The five improvements below were measured on seven test days and then together in a second 61-day
campaign; see [reports/two-month-improvements](reports/two-month-improvements/README.md).

1. **Drag coefficient estimated on a 12 h arc** (Sentinel-3A profile). It is the largest gain for the
   forecast. A bug made the fit singular when Cd was estimated: Orekit's drag model also exposes a
   "global drag factor", perfectly correlated with Cd, and both were selected. It is fixed.
2. **Drag modelling.**
   * JB2008 and the box-wing model with measured attitude were tested.
   * Neither is the default. The box-wing panel areas are not Sentinel-3A's (they need the mission
     macro-model); see the results.
3. **GPS antenna phase centres and phase wind-up.**
   * The IGS ANTEX offsets and variations are applied in each GPS satellite's yaw attitude, for
     centre-of-mass (precise and ultra-rapid) orbits.
   * Phase wind-up is applied.
   * Together they reduce the phase residuals from about 6 cm to about 5 cm.
4. **Closer to Orekit's design.**
   * The nominal attitude is Orekit's yaw-compensated geodetic nadir pointing. It matches the
     measured PROQUA quaternions to 0.005°, where the previous approximation was 2.5° off.
   * OEM output frames are computed with Orekit during the run.
   * A headless `run` command runs saved configurations.
   * The time-span issue is written up for the Orekit project (`docs/orekit/`).
5. **Parallel window runs.** Each cut-off runs in its own worker process, because Orekit's data
   context is process-wide. By default there is one worker per spare core, limited by memory.

## Known limitations

These are the remaining sources of error:

* **LEO antenna phase-centre variations.**
* **Strictly dynamic arc.** There are no empirical accelerations, so drag mismodelling over
  the 6 h arc shows in the residuals.
* **Float ambiguities.** Ambiguities are not resolved to integers.
* **Ocean tides.** These use the 8 main FES2004 constituents to degree 4.
* **Forecasts beyond a few hours.** These are dominated by drag (space-weather) uncertainty,
  not by the orbit-determination method.
