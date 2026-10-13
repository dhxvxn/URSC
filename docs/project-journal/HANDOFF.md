# URSC — handoff

Status of the URSC LEO trajectory project and how to pick it up. Branch:
`claude/leo-trajectory-prediction-java-ihupky`.

## What URSC is

A modular, **zero-runtime-dependency** Java library to determine and predict long-term LEO
satellite trajectories, with every force model and data source pluggable. It has: a
numerical (Cowell) propagator and a DSST semi-analytical propagator; the full LEO force
stack (EGM96/ICGEM gravity, Sun/Moon third body, NRLMSISE-00 drag, SRP, Earth albedo/IR,
solid + ocean tides, relativity); IAU-2006/2000 frames with real IERS EOP; batch
least-squares orbit determination; and a terminal CLI.

## Build & test

```sh
mvn -q -DskipTests compile      # build
mvn -q test                     # 47 tests, all green
```

## Commands (all support real data files)

```sh
leoprop validate <poeorb.eof> --sw-file SW.txt --eop-file finals2000A.all [--gravity-file m.gfc]
leoprop fit      <poeorb.eof> --estimate-cd --hours 6 --sw-file SW.txt --eop-file finals2000A.all
leoprop forecast <seed.eof>   --days 7 --sw-file SW.txt --eop-file finals2000A.all
leoprop backtest <train.eof> <truth.eof> --sw-file SW.txt --eop-file finals2000A.all
```

Data files are **uploaded** (orekit-data and CelesTrak are network-blocked here). See
`docs/OREKIT_DATA.md` for formats; `docs/project-journal/OREKIT-DATA-ASSESSMENT.md` for
which files matter.

## Where things are

| area | path |
|---|---|
| force models | `src/main/java/org/ursc/trajectory/forces/` |
| frames + EOP | `src/main/java/org/ursc/trajectory/frames/` (`Finals2000AEopProvider`, `IersEopProvider`) |
| gravity loader | `forces/gravity/GravityFieldFactory.java` (`loadIcgem`, `loadNormalized`) |
| OD | `estimation/BatchLeastSquares.java` |
| commands | `app/command/` (shared helpers in `OrbitFiles.java`) |
| reports & studies | `docs/reports/`, `docs/project-journal/` |

## Current accuracy (Sentinel-3A, real Oct-2024 data)

- **Orbit determination** (6 h fit, with real EOP): post-fit RMS **~2.8 m**.
- **Short-arc propagation** (seeded from truth): ~5 m at 1 h, ~24 m at 6 h, ~52 m at 24 h.
- **Prediction** (fit 6 h, predict 20 h, with real EOP): held-out floor **~141 m**,
  along-track/drag dominated.
- **Open-loop from a single state, no OD**: km-scale (phase runaway from the seed), as
  expected — orbit determination is essential.

Best force stack for this regime: **full gravity + NRLMSISE-00 drag (fitted Cd) + Sun/Moon
+ SRP (fitted Cr)**, with **real EOP** (`--eop-file finals2000A.all`). Fine forces (tides,
relativity, albedo) are physically correct but below the noise at a ~1-day horizon.

## Honest caveats

- Prediction accuracy is **drag-limited**; beyond a day it is bounded by space-weather
  (F10.7/Ap) uncertainty — the same wall every operational tool hits.
- EOP helps OD and short/medium arcs (~25 % up to 12 h); it is in the noise by 24 h.
- EIGEN-6S ≈ EGM96 at the degrees that matter for 800 km; DE440 adds nothing for LEO.
- "Reconstruction" (scoring with the historical space weather of those days) is not the
  same as forecasting skill.

## What's next (parked roadmap, in priority order)

1. **Uncertainty quantification** — OD covariance → covariance propagation → Monte-Carlo
   over solar-activity scenarios → a `predict` command emitting RTN error bands + a lifetime
   distribution. (Detailed design in the plan file / git history.)
2. **Space-weather forecasting** — analytical solar-cycle F10.7 model + the orekit-data
   **MSAFE** forecasts (already available in the uploaded zip) → percentile drag scenarios.
3. **Higher-order DSST** — 2nd-order short-period + tesseral resonance + DP853 integrator.
4. **Raw GNSS OD** — ingest the uploaded GNSSRD raw observations for cm-level POD.
5. **Way 1 — atmosphere density assimilation** (the only real lever for *greater* accuracy):
   correct NRLMSISE density from observed decay (HASDM-style) / an ML density layer.

## Immediate follow-ups (small, high-value)

- Make `--eop-file` the documented default in every example; consider auto-loading a
  bundled recent EOP file.
- Re-run the Oct-2024 reports/sweep with `--eop-file` applied and refresh their numbers
  (this journal already carries the improved figures).
- Longer-horizon force selection needs more **contiguous** POEORB days (current truth is a
  single ~26 h arc); uploading several consecutive days would let the fine forces separate
  from noise.

## Reproduce the Day-4 results

```sh
# OD improvement from real EOP (inputs now live in the kept example report's inputs/ folder)
leoprop fit docs/reports/perturbation-ladder-truestate-7day/inputs/S3A_POEORB_truth_20241007-08.EOF \
  --estimate-cd --hours 6 --gravity-degree 20 \
  --sw-file docs/reports/perturbation-ladder-truestate-7day/inputs/SpaceWeather-All-v1.2.txt \
  --eop-file docs/project-journal/inputs/finals2000A.all
```

(The earlier per-report sweep/ladder folders were consolidated into the single example report
`docs/reports/perturbation-ladder-truestate-7day/`.)
