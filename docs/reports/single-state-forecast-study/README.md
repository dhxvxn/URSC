# Single-state forecast study — Sentinel-3A, Aug & Oct 2024

How accurately can URSC predict a Sentinel-3A orbit from a **single state** (UTC + 6 Cartesian
elements + one attitude sample + the space weather at that instant), with **everything after the
seed propagated, never read from future observations**? This report records every input, model
configuration (with variable values), and result from that investigation.

## The input contract

Per-satellite inputs are all at **one epoch**: UTC, the six elements (x,y,z,vx,vy,vz), one attitude
quaternion, one space-weather sample (F10.7, F10.7a, Ap). From there URSC is the propagator — it
forecasts the trajectory, attitude and space weather forward from physics/models. Environment models
(gravity field, EOP, the space-weather driver file for reading the *seed-instant* value, DE440) are
bundled/uploaded, not per-satellite inputs. **No observed future data is used.**

## Inputs

Seed states (ITRF, from Sentinel-3A POEORB), and the truth states used to score:

| role | UTC | x, y, z (m) | vx, vy, vz (m/s) |
|---|---|---|---|
| Aug-1 seed | 2024-08-01T21:59:42 | -5539309.948842, -697403.926822, 4510135.451876 | -4797.750487, 1254.166536, -5683.340209 |
| Aug-2 truth (+1d) | 2024-08-02T21:59:42 | -4241010.467952, 861128.531538, -5739746.010449 | 6068.430474, 980.961472, -4339.346932 |
| Aug-7 seed | 2024-08-07T21:59:42 | 6767847.481359, 377682.222921, -2386291.285487 | 2525.539473, -1538.926322, 6937.240212 |
| Aug-8 seed/truth | 2024-08-08T21:59:42 | 2003659.741832, -1028630.224384, 6810347.688489 | -7232.496965, -473.488964, 2051.982928 |
| Aug-14 truth | 2024-08-14T21:59:42 | 491031.668121, 1076943.289996, -7090786.959859 | 7497.101178, -95.788196, 504.658822 |
| Oct-7 seed | 2024-10-07T21:59:42 | 482304.989602, 1077999.632437, -7091304.589548 | 7497.731211, -93.447784, 495.577989 |

Seed-instant space weather (read at the seed epoch only, from `SpaceWeather-All-v1.2.txt`):

| seed | F10.7 | F10.7a (81-day) | Ap |
|---|---|---|---|
| Aug-1 | 235.1 | 219.7 | 19 |
| Aug-7 | 270.0 | 219.2 | 6 |
| Aug-8 | 303.2 | 219.0 | 6 |
| Oct-7 | 264.5 | 209.8 | 54 |

Environment files (in `docs/reports/perturbation-ladder-truestate-7day/inputs/`): `finals2000A.all`
(EOP), `SpaceWeather-All-v1.2.txt` (CSSI). DE440 (`lnxp1990.440`) and EIGEN-6S (`eigen-6s.gfc`) from
the uploaded orekit-data; read by path.

## Configurations (variable values)

| knob | values tried |
|---|---|
| density model | NRLMSISE-00, JB2008 |
| drag coefficient Cd | 2.2 (fixed — no fit possible from one state) |
| cross-section area A | 8 m² (sweep 2–8 showed JB2008 is area-insensitive) |
| mass | 1130 kg |
| gravity field / degree×order | EGM96 and EIGEN-6S; 20–120 (order=degree, + 70×40, 120×70) |
| Sun/Moon | analytic series vs DE440 (no measurable difference) |
| integrator (DP54) | loose (absTol 1e-7, relTol 1e-9, maxStep 120 s) vs tight (1e-10/1e-12, 30 s) |
| weather forward | reconstruction (observed), constant (seed frozen), forecast-trend (F10.7→F10.7a, Ap persisted) |

## Results

### 1. The best density model flips arc-to-arc — no universal choice

7/8-day forward error, forecast weather, tight integrator, A=8, gravity 70:

| arc (seed F10.7) | NRLMSISE | JB2008 | winner |
|---|---|---|---|
| Aug-1 (235) | 9,170 m | **1,326 m** | JB2008 |
| Oct-7 (264), 8d | **1,595 m** | 4,171 m | NRLMSISE |
| Aug-8→14 (303), 6d | **1,733 m** | 10,022 m | NRLMSISE |
| Aug-7→14 (270), 7d | **2,580 m** | 12,562 m | NRLMSISE |

JB2008 won only one of four arcs; the ranking does not track F10.7 (JB2008 is *worst* on the highest-flux
Aug-8 arc). **Which density model is best is arc-specific and not predictable from the seed.** So a
single "best config" cannot be locked in, and a single constant error cannot be subtracted across days.

### 2. Fidelity ladder on the Aug-1 arc (how 9.2 km became 1.3 km)

| step (single state, no fit, no future data) | 7-day error |
|---|---|
| NRLMSISE, A=8, loose integrator (start) | 9,170 m |
| → JB2008 density model | 1,791 m |
| → + tight integrator | 1,326 m |
| → + DE440 Sun/Moon | ~0 change (1,327 m) |
| → + EIGEN-6S gravity / degree 120 / even-tighter integrator | ~0 change (~1,326 m) |

Density model is the dominant lever (−7.4 km); integrator convergence is worth −465 m; ephemeris and
gravity-field upgrades do nothing at this altitude. **1,326 m is the converged single-state floor for
this arc.** The residual is along-track and area-insensitive — a drag/density mismatch a fixed Cd can't
remove without an observation arc.

### 3. Gravity degree/order — saturates, and low degree is a cancellation trap

Aug-1, JB2008 + tight + forecast, EIGEN-6S:

| degree×order | 7-day error | 1-day error |
|---|---|---|
| 20×20 | 785.7 m | 273.5 m |
| 40×40 | 1,367.7 m | — |
| 80×80 | 1,327.0 m | 172.7 m |
| 100/120 | 1,326.8 m | — |
| 70×40 / 120×70 | 1,358.8 / 1,326.8 m | — |

Degree 20 gives a *lower* 7-day number (785 m, under 1 km) but a *higher* 1-day number (273 vs 173 m):
it is **not** more accurate — its gravity error coincidentally cancels the drag error at 7 days for this
arc only (on the October arc, degree 20 was 9,715 m, worse than degree 70). The physically-correct field
saturates at ~1,327 m past degree 80. **Low gravity degree is not a robust lever.**

### 4. Weather forward model (Aug-1)

Divergence of the single-instance assumption from using real observed weather: at 1 day constant ≈
forecast (~27 m); at 7 days forecast-trend (120 m) beats holding constant (330 m) because the seed flux
sat above its 81-day average and decayed toward it. But the weather-model error is small next to the
fixed-Cd floor, and for October forecast ≈ reconstruction (1,595 vs 1,602 m) — the weather forecast is
not the limiter.

## Conclusions (honest)

- **The single-state 7-day error is arc-dependent and not reducible below ~1–2.6 km by modeling alone**
  for these 2024 solar-max arcs. The best per-arc model gives 1.3 km (Aug-1) to 2.6 km (Aug-7).
- **No universal recipe:** the best density model, and whether a given gravity degree helps, both flip
  between arcs. A constant error offset cannot be subtracted.
- **Under 1 km is reachable** only by (a) a shorter horizon (JB2008 Aug-1 is under 1 km to ~4–5 days;
  1-day errors are ~170–390 m), (b) fitting Cd / an along-track empirical term on an observation arc
  (removes the along-track floor — needs more than one state), or (c) re-anchoring (re-observe, keep the
  horizon short). Degree-20 "sub-1 km" is a cancellation artifact, not accuracy.
- **DE440 and EIGEN-6S are not levers** for LEO single-state accuracy; the analytic Sun/Moon and EGM96-70
  are already adequate. The integrator must be tight (absTol 1e-10, maxStep ≤30 s).

## Reproduce

Scratch harnesses are in `src/` (package `org.ursc.trajectory.app.command`, compile against
`target/classes`). Example (October cross-check):

```sh
javac -cp target/classes -d /tmp/out docs/reports/single-state-forecast-study/src/TwoModel.java
java -cp target/classes:/tmp/out org.ursc.trajectory.app.command.TwoModel \
  <SpaceWeather-All-v1.2.txt> <finals2000A.all> \
  2024-08-07T21:59:42 6767847.481359 377682.222921 -2386291.285487 2525.539473 -1538.926322 6937.240212 \
  7 491031.668121 1076943.289996 -7090786.959859
```

All numbers are collected in `data/results.csv`.
