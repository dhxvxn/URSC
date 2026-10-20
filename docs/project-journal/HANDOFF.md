# URSC — handoff

Status of the URSC LEO trajectory project and how to pick it up. Branch:
`claude/leo-trajectory-prediction-java-ihupky`. Last updated: Oct 20 2026.

## What URSC is

A modular, **zero-runtime-dependency** Java library to determine and predict long-term LEO
satellite trajectories, with every force model and data source pluggable (Orekit is an
architectural reference only, used solely in `experiments/`). It has: a numerical (Cowell)
propagator and a DSST semi-analytical propagator; the full LEO force stack (EGM96/ICGEM
gravity, Sun/Moon third body, **NRLMSISE-00 and from-scratch JB2008** drag, SRP, Earth
albedo/IR, solid + ocean tides, relativity); a **CPOD 8-plate box-wing** attitude-area model
with a measured-quaternion attitude provider; a **DE440 JPL ephemeris** reader (optional,
analytic Sun/Moon by default); IAU-2006/2000 frames with real IERS EOP; batch least-squares
orbit determination; a space-weather **forecast layer** with scenario bands; and a terminal CLI.

## Build & test

```sh
mvn -q -DskipTests compile      # build
mvn -q test                     # 68 tests, all green
mvn -q -DskipTests package      # -> target/leo-trajectory-0.1.0.jar (runnable CLI)
```

## Commands (all support real data files)

```sh
leoprop predict   # NEW: single typed-in state -> prediction (+ optional 1-day/8-day error)
leoprop validate  <poeorb.eof> --sw-file SW.txt --eop-file finals2000A.all [--gravity-file m.gfc]
leoprop fit       <poeorb.eof> --estimate-cd --hours 6 --sw-file SW.txt --eop-file finals2000A.all
leoprop forecast  <seed.eof>   --days 7 --sw-file SW.txt --eop-file finals2000A.all [--sw-forecast --scenario stressed]
leoprop backtest  <train.eof> <truth.eof> --sw-file SW.txt --eop-file finals2000A.all
```

### `predict` — the single-state terminal tool (the main user-facing workflow)

Type in the UTC epoch and the six Cartesian elements (x,y,z,vx,vy,vz) directly — as flags,
or interactively with no `--utc` (it asks each force model as yes/no). Pick any force-model
combination and read off the predicted state; add `--truth` to score the **1-day and 8-day**
position error. This is how to test which model combination wins at which horizon without
re-running the analysis by hand.

```sh
# interactive (prompts for state + each force model)
java -jar target/leo-trajectory-0.1.0.jar predict

# one-shot: real S3A Oct-7 2024 state, full stack, scored against truth
java -jar target/leo-trajectory-0.1.0.jar predict \
  --utc 2024-10-07T21:59:42 \
  --pos 482304.989602,1077999.632437,-7091304.589548 \
  --vel 7497.731211,-93.447784,495.577989 --frame itrf --days 8 \
  --gravity-degree 70 --atmosphere nrlmsise00 \
  --eop-file docs/reports/perturbation-ladder-truestate-7day/inputs/finals2000A.all \
  --sw-file  docs/reports/perturbation-ladder-truestate-7day/inputs/SpaceWeather-All-v1.2.txt \
  --truth docs/reports/perturbation-ladder-truestate-7day/inputs/S3A_POEORB_truth_20241007-08.EOF,docs/reports/perturbation-ladder-truestate-7day/inputs/S3A_POEORB_truth_20241015-16.EOF
```

Force toggles: `--[no-]sun --[no-]moon --[no-]drag --[no-]srp --[no-]albedo --[no-]solid-tides
--[no-]ocean-tides --[no-]relativity`; `--atmosphere none|exponential|harris-priester|nrlmsise00|jb2008`;
`--gravity-degree N`; `--box-wing`; `--mass --cd --cr --area`; `--de440 lnxp1990.440`.

Data files (orekit-data, CelesTrak, POEORB, attitude, DE440) are **uploaded** (network-blocked
here). See `docs/OREKIT_DATA.md` for formats; `OREKIT-DATA-ASSESSMENT.md` for which matter.

## Where things are

| area | path |
|---|---|
| force models | `src/main/java/org/ursc/trajectory/forces/` (gravity, drag incl. `drag/jb2008/`, radiation, tides, box-wing) |
| attitude | `src/main/java/org/ursc/trajectory/attitude/` (`AttitudeProvider`, `NominalYawSteering`, `QuaternionFileAttitude`) |
| ephemeris | `src/main/java/org/ursc/trajectory/bodies/` (`CelestialBodyFactory`, `De440Ephemeris`, `EphemerisSource`) |
| frames + EOP | `src/main/java/org/ursc/trajectory/frames/` (`Finals2000AEopProvider`, `IersEopProvider`) |
| gravity loader | `forces/gravity/GravityFieldFactory.java` (`loadIcgem`, `getEgm96`) |
| OD | `estimation/BatchLeastSquares.java` |
| CLI + commands | `app/Cli.java`, `app/command/` (shared helpers in `OrbitFiles.java`) |
| reports & studies | `docs/reports/`, `docs/project-journal/` |
| Orekit cross-check | `experiments/orekit-compare/` (separate module; core stays zero-dependency) |

## Current accuracy (Sentinel-3A, real Oct-2024 data)

Two regimes, depending on whether orbit determination is allowed:

**With OD (an observation arc to fit Cd):**
- Orbit determination (6 h fit, real EOP): post-fit RMS **~2.8 m**.
- Short-arc propagation (seeded from truth): ~5 m @ 1 h, ~24 m @ 6 h, ~52 m @ 24 h.
- Prediction (fit 6 h, predict 20 h): held-out floor **~141 m**, along-track/drag dominated.

**Single state only (the governing product constraint — UTC + 6 elements, no arc, fixed Cd):**
- 1-day true-state RMS **~65–100 m**, 8-day **~1.6 km** (full stack, gravity 70, NRLMSISE, Cd 2.2).
- No OD is possible (one state has no arc), so **Cd cannot be fitted** — this is the fixed-Cd
  drag floor. Geometry and ephemeris fidelity do not break it (see caveats).

## Capabilities added since Day 4 (storm density, real geometry, the terminal tool)

- **JB2008 atmosphere** ported from scratch into the core (storm-aware via SOLFSMY/DTCFILE),
  validated vs an Orekit oracle to <1e-4. Selectable with `--atmosphere jb2008`.
- **CPOD 8-plate box-wing** (GMV-CPOD-TN-0027): real bus faces 2.84/5.41/6.10 m², solar array
  10.50 m² canted 24°, mass 1129.648 kg; projected-area validated vs the CPOD table
  (deployed 3.244/6.058/16.576 m²). Oriented by an `AttitudeProvider`: the nominal yaw-steering
  law, or **measured AUX_PROQUA quaternions** (`QuaternionFileAttitude`).
- **DE440 JPL ephemeris** reader for high-precision Sun/Moon (`--de440`), analytic series the
  default and fallback.
- **Space-weather forecast layer** with nominal/stressed scenario bands for forward prediction
  (`forecast --sw-forecast --scenario`).
- **`predict`** terminal command (above) — the single-state workflow as a self-contained tool.

## Honest caveats (important — read before promising accuracy)

- **The single-state error is a fixed-Cd drag floor.** Measured three independent ways, the
  physical upgrades do **not** reduce it: the real CPOD box-wing + measured attitude left the
  1-day RMS essentially unchanged (72 m cannonball vs 85 m box-wing — the geometry just re-sets
  the ballistic coefficient); DE440 Sun/Moon left it unchanged (99.79 → 99.92 m; the analytic
  series was already accurate to 0.08°); and an earlier Orekit head-to-head with the identical
  force stack landed at the same ~1.9 km at 8 days. The one lever that breaks the floor is
  **fitting Cd** (or an along-track empirical acceleration), which **requires an observation
  arc** — impossible from a single state.
- Which density model or force subset "wins" under a fixed Cd is partly an epoch-specific
  **cancellation** (NRLMSISE beat JB2008 here; low gravity degree can beat high) — validate any
  "winner" on a second epoch before trusting it.
- Gravity **saturates at degree ~80** for 800 km (bit-identical 80→140); DE440 adds nothing for LEO.
- Beyond ~1 day, prediction is bounded by **space-weather (F10.7/Ap) forecast** uncertainty —
  the Oct 10–11 2024 storm is the dominant 8-day error and is not forecastable from before it.
  "Reconstruction" (scoring with the historical space weather) is not forecasting skill.
- The S3A box-wing's measured attitude only covers ~Oct 16; the nominal yaw-steering law
  (verified to match the telemetry to 99.8%) is the faithful choice for other epochs.

## What's next (parked roadmap, in priority order)

1. **Estimate Cd / an along-track empirical acceleration from an arc** — the only lever that
   breaks the single-state floor. Needs contiguous daily POEORB (e.g. Oct 7–16). Machinery
   (`BatchLeastSquares`, `EmpiricalAcceleration`) is already in place; see the plan file.
2. **Uncertainty quantification** — OD covariance → covariance propagation → Monte-Carlo over
   solar scenarios → RTN error bands + a lifetime distribution.
3. **Space-weather forecasting** — analytical solar-cycle F10.7 + the orekit-data MSAFE forecasts
   → percentile drag scenarios (the forecast layer already supports the band plumbing).
4. **Higher-order DSST**; **raw GNSS OD** (the uploaded GNSSRD) for cm-level POD.
5. **Density assimilation** (HASDM-style / ML correction) — the only route to *greater* accuracy
   than operational tools, still bounded by the space-weather wall.

## Immediate follow-ups (small, high-value)

- `predict --csv` to dump the full predicted ephemeris; a `predict`-based **sweep mode** that
  runs a batch of force combinations and prints a ranked 1-day/8-day table automatically.
- Always pass `--eop-file finals2000A.all` (it both tightens OD and lowers the prediction floor);
  consider bundling a recent EOP snapshot.
- Longer-horizon force selection needs more **contiguous** POEORB days (current truth is two
  ~26 h arcs, Oct 7–8 and Oct 15–16).

## Reproduce the headline single-state numbers

```sh
# full stack, gravity 70, NRLMSISE, fixed Cd -> 1-day ~99.8 m, 8-day ~1602 m
java -jar target/leo-trajectory-0.1.0.jar predict \
  --utc 2024-10-07T21:59:42 \
  --pos 482304.989602,1077999.632437,-7091304.589548 \
  --vel 7497.731211,-93.447784,495.577989 --frame itrf --days 8 \
  --gravity-degree 70 --atmosphere nrlmsise00 \
  --eop-file docs/reports/perturbation-ladder-truestate-7day/inputs/finals2000A.all \
  --sw-file  docs/reports/perturbation-ladder-truestate-7day/inputs/SpaceWeather-All-v1.2.txt \
  --truth docs/reports/perturbation-ladder-truestate-7day/inputs/S3A_POEORB_truth_20241007-08.EOF,docs/reports/perturbation-ladder-truestate-7day/inputs/S3A_POEORB_truth_20241015-16.EOF
```
