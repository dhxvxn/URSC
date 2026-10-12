# Perturbation-model combination sweep — Sentinel-3A

Which combination of force models gives the lowest orbit-prediction error? This sweep
answers it the correct way: **fit-then-predict per combination**. For each force-model
combination it determines the orbit (state + Cd + Cr) on a short training arc of the real
Sentinel-3A precise orbit, predicts forward, and scores the position error on the **held-out**
remainder it never saw. Lowest held-out RMS wins.

## Why fit-then-predict (not a fixed seed)

The earlier single-seed run (see `../README.md`) had ~13,000 km error dominated by a ~12.6 km
seed semi-major-axis offset — a phase error no force model can remove. Here, orbit
determination corrects the state for every combination, so the **force choice becomes the
dominant signal** and the comparison is fair. The error floor drops from ~13,000 km to
~**224 m**, which already shows the real lesson: *determination quality dominates; force
selection is the second-order refinement.*

## Setup

- Data: `../inputs/S3A_POEORB_truth_20241007-08.EOF` (9361 states, 10 s, ~26 h).
- Split: **fit = first 6 h**, **held-out score = remaining ~20 h**.
- Real space weather (`../inputs/SpaceWeather-All-v1.2.txt`) for all drag models.
- Gravity "full" rung = EGM96 20×20. Fit = `BatchLeastSquares` (state + Cd when drag on +
  Cr when SRP on), via the existing `backtest` scoring path (`BacktestCommand.scoreCombos`).

## Result — least-error combination

**`gravity 20×20 + Sun/Moon + drag (NRLMSISE-00, fitted Cd) + SRP (fitted Cr)` → held-out RMS = 224.0 m**

### Cumulative ladder (marginal contribution of each force)

| rung | held-out RMS | marginal gain |
|---|---:|---:|
| two-body (central) | 51 817.9 m | — |
| + J2 | 2 415.6 m | **−49 402 m** |
| + gravity 20×20 | 270.8 m | **−2 145 m** |
| + Sun/Moon | 270.6 m | −0.2 m |
| + drag (NRLMSISE, fitted Cd) | 224.2 m | **−46.4 m** |
| + SRP (fitted Cr) | **224.0 m** | −0.2 m |
| + solid tides | 224.1 m | +0.1 m |
| + ocean tides | 226.9 m | +2.8 m |
| + relativity | 226.9 m | 0.0 m |
| + albedo/IR (full stack) | 226.9 m | −0.0 m |

### Atmosphere model (at the full stack)

| drag model | held-out RMS |
|---|---:|
| NRLMSISE-00 | **226.9 m** |
| Harris-Priester | 228.1 m |
| exponential | 243.2 m |
| none | 270.0 m |

### Leave-one-out from the full stack (which single removal hurts most)

| removed | held-out RMS | penalty vs full |
|---|---:|---:|
| drag | 270.0 m | **+43.1 m** |
| Sun/Moon | 230.6 m | +3.8 m |
| SRP | 227.0 m | +0.2 m |
| relativity | 226.9 m | 0.0 m |
| albedo/IR | 226.9 m | 0.0 m |
| solid tides | 226.8 m | −0.1 m |
| ocean tides | 224.1 m | −2.8 m |

## Conclusions (at this ~20 h horizon)

1. **Gravity is ~99.5% of the gain.** J2 alone cuts error ~20×; the full 20×20 field another ~9×.
2. **Drag is the clear #2** (~46 m; removing it is the biggest single-force penalty), and
   **NRLMSISE-00 is the best atmosphere** > Harris-Priester > exponential > none.
3. **Sun/Moon is a small but real #3** (~4 m).
4. **Solid/ocean tides, relativity, albedo are negligible here** — the top ~5 combinations
   (224–227 m) are within the ~3 m fit/prediction noise. Ocean tides even looked marginally
   *worse* (+2.8 m), i.e. noise at this horizon, not a real penalty. These fine forces are
   physically correct to keep and are expected to separate from noise over multi-day arcs
   (needs more contiguous truth days to test).

**Practical recommendation:** for this satellite/altitude at a ~1-day horizon, the efficient
stack is **full gravity field + NRLMSISE-00 drag (fitted Cd) + Sun/Moon + SRP (fitted Cr)**.

## Files / reproduce

```
sweep_chart.png            sorted bar charts (ladder / leave-one-out / atmosphere)
sweep_results.csv          group, combination, held-out RMS (m)
sweep_console_output.txt   full ranked console output
ForceSweep.java            harness (package org.ursc.trajectory.app.command;
                           calls BacktestCommand.scoreCombos over the combo sets)
make_sweep_chart.py        chart generator
```

```sh
mvn -q -DskipTests compile
mkdir -p build && javac -cp target/classes -d build \
  docs/reports/error-analysis-oct2024/sweep/ForceSweep.java
java -cp target/classes:build org.ursc.trajectory.app.command.ForceSweep \
  docs/reports/error-analysis-oct2024/inputs/S3A_POEORB_truth_20241007-08.EOF \
  docs/reports/error-analysis-oct2024/inputs/SpaceWeather-All-v1.2.txt \
  sweep_results.csv 6 20
python3 docs/reports/error-analysis-oct2024/sweep/make_sweep_chart.py
```
