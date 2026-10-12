# Prediction error analysis — Sentinel-3A, October 2024

This report quantifies how far a **long-horizon prediction** of the Sentinel-3A orbit,
started from a **single state vector**, drifts from the satellite's **actual** precise
orbit six to seven days later. It is the first end-to-end accuracy check of the URSC
propagator against real ESA precise-orbit truth.

Two propagators are compared on the *same* input and the *same* truth window:

| run | forces modelled |
|---|---|
| **two-body** (bare propagator) | central gravity only — **no perturbations** |
| **full-force** | EGM96 gravity 30×30, Sun + Moon third-body, NRLMSISE-00 drag (real space weather), solar radiation pressure, Earth albedo/IR, solid + ocean tides, relativity |

## Input (what the model was given)

- **One** Sentinel-3A state vector, `inputs/oct1_seed.eof`
  (ITRF / Earth-fixed), epoch **2024-10-01 21:59:42 UTC**:

  | X (m) | Y (m) | Z (m) | VX (m/s) | VY (m/s) | VZ (m/s) |
  |---:|---:|---:|---:|---:|---:|
  | −6 070 314.946759 | 552 274.315540 | −3 807 638.868815 | 4 022.099496 | 1 401.170432 | −6 216.770780 |

- `inputs/SpaceWeather-All-v1.2.txt` — real observed F10.7 / Ap (orekit-data CSSI file),
  used by the full-force run's drag model. Not used by the two-body run.

## Truth (what the prediction is compared against)

- `inputs/S3A_POEORB_truth_20241007-08.EOF` — the actual Sentinel-3A **POEORB** precise
  orbit (ITRF), 9361 state vectors at 10 s cadence, covering
  **2024-10-07 21:59:42 → 2024-10-08 23:59:42 UTC**. The window begins exactly **6 days**
  after the seed epoch.

## Method

1. Seed the propagator from the single Oct-1 ITRF state (converted to the GCRF inertial
   frame).
2. Propagate forward to each truth epoch (every 60 s), then convert the predicted state
   back to ITRF (the frame the truth data lives in).
3. At each epoch compute, in ITRF:
   - **position error** `√((x−x₁)² + (y−y₁)² + (z−z₁)²)` — (x,y,z) model, (x₁,y₁,z₁) actual
   - **velocity error** `√((vₓ−vₓ₁)² + (v_y−v_y₁)² + (v_z−v_z₁)²)`

Position-error *magnitude* is frame-independent; velocity-error magnitude is
frame-dependent, so ITRF (the data frame) is used throughout.

## Results

| metric | two-body | full-force |
|---|---:|---:|
| position RMS | **13 923.4 km** | **13 444.7 km** |
| position max | 13 968.5 km | 13 454.1 km |
| position final | 13 961.3 km | 13 449.4 km |
| velocity RMS | 14 623.8 m/s | 14 104.8 m/s |
| velocity max | 14 694.3 m/s | 14 137.5 m/s |
| velocity final | 14 641.8 m/s | 14 108.1 m/s |

Turning the full perturbation stack on improves the 6-day prediction by **≈ 480 km**
(~3.4%) over the bare two-body propagator — a real, measurable gain.

### Why both errors are large (and nearly constant)

The predicted orbit and the real orbit have the **same size, shape and plane** — they are
just **~130° apart in phase along the track**, so the separation stays near-constant
(~13–14 thousand km) all the way around. The osculating elements explain it:

| | semi-major axis `a` | inclination | RAAN |
|---|---:|---:|---:|
| seed (Oct-1) | **7181.7 km** | 98.571° | 340.95° |
| actual (Oct-7) | **7169.1 km** | 98.601° | 346.76° (precesses ~0.97°/day — correct) |

The dominant error source is the **~12.6 km semi-major-axis gap** between the user-supplied
Oct-1 seed and the true orbit. A small `a` offset changes the orbital period slightly; over
~85 revolutions in 6 days that compounds into the ~130° along-track lag. **No force model
can remove this** — only fitting the seed state to a short arc (orbit determination) can.
That is exactly what the `backtest` / `fit` commands in this project do, and is the natural
next experiment.

### Honest scope

This is a **6-to-7-day prediction from a single state with no orbit determination and a
fixed drag coefficient (Cd = 2.2)**. It measures raw open-loop propagation drift, not the
accuracy achievable with a fitted arc.

## Files

```
graphs/   twobody_* and fullforce_* : position-error graph, velocity-error graph, 6-element table
data/     error_oct7_twobody.csv, error_oct7.csv : full 60 s series (epoch, dPos, dVel, model 6, actual 6)
          *_comparison_table_hourly.csv / .md     : hourly model-vs-actual tables
inputs/   oct1_seed.eof (input), S3A_POEORB_truth_20241007-08.EOF (truth), SpaceWeather-All-v1.2.txt
src/      ErrorAnalysisKepler.java (two-body), ErrorAnalysis.java (full-force), make_*.py (graphs/tables)
```

### Reproduce

```sh
mvn -q -DskipTests compile
cd docs/reports/error-analysis-oct2024/src
# two-body (bare propagator)
javac -cp ../../../../target/classes -d . ErrorAnalysisKepler.java
java  -cp ../../../../target/classes:. ErrorAnalysisKepler \
      ../inputs/oct1_seed.eof ../inputs/S3A_POEORB_truth_20241007-08.EOF out_twobody.csv 60
# full-force
javac -cp ../../../../target/classes -d . ErrorAnalysis.java
java  -cp ../../../../target/classes:. ErrorAnalysis \
      ../inputs/oct1_seed.eof ../inputs/S3A_POEORB_truth_20241007-08.EOF \
      ../inputs/SpaceWeather-All-v1.2.txt out_fullforce.csv 60
# graphs + tables
python3 -I make_all.py out_twobody.csv twobody "two-body, no perturbations"
```

Data sources: Sentinel-3A POEORB — Copernicus / EUMETSAT (open data); space weather —
orekit-data (public). Redistributed here for reproducibility.
