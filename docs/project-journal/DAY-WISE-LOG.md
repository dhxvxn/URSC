# URSC — day-wise development log

A chronological record of what was built and found each day, with the physics takeaways and
commit references. All work is on branch `claude/leo-trajectory-prediction-java-ihupky`.

---

## Day 1 — Oct 9: foundation (zero-dependency force models + CLI + frames)

Built the modular, zero-runtime-dependency core (Orekit as architectural reference).

- Modular LEO trajectory library skeleton; every force model / variable pluggable. `c2ac8cd`
- NRLMSISE-00 atmosphere as a pluggable drag model. `4199436`
- Bundled EGM96 gravity field (degree/order 70), made the default. `0fca02d`
- Terminal-first subcommand CLI: `propagate`, `decay`, `compare`, `tle`. `2e984c1`
- Conservative/non-conservative forces: solid Earth tides, Earth albedo/IR, Lense-Thirring
  relativity. `dc9484b`
- Ocean tides (bundled FES2004, 8 constituents). `3bab63a`
- File-backed space weather (CelesTrak CSSI) for NRLMSISE-00. `020863a`
- High-fidelity IAU-2006/2000B frames with IERS EOP loading (`IersEopProvider`, C04). `b0e8d71`

**Takeaway:** a complete numerical (Cowell) propagator with the full LEO force stack, no
external dependencies.

---

## Day 2 — Oct 10: semi-analytical propagation + orbit determination

- DSST semi-analytical mean-element propagator (fast long-term propagation). `0c460ae`
- Documentation set: `docs/USAGE.md`, `docs/ARCHITECTURE.md`, `docs/PHYSICS.md`. `dbd00ff`
- POEORB precise-orbit validation + batch least-squares orbit determination
  (`PoeOrbReader`, `BatchLeastSquares`, `validate`, `fit`). `d744f72`

**Takeaway:** URSC can now be validated against real precise orbits and can *determine* an
orbit (state + Cd) from tracking data, not just propagate.

---

## Day 3 — Oct 12: accuracy work, reports, and the force-selection experiment

- Real space weather wired into `validate`/`fit`; **fixed DSST to be osculating-accurate**
  (a near-circular-orbit element bug gave a 30-day DSST error of 6234 km → 2.76 km, a
  ~3000× improvement, ~10× faster than Cowell). `d0d805e`
- Technical + physics reports (PDF + reproducible HTML sources). `2a7aafe`, `49b0740`
- **`backtest`** command: fit-then-predict force-model selection — for each force
  combination, determine the orbit on a training arc and score held-out prediction error. `bf4431f`
- **`forecast`** command (predict forward from one seed) + an orekit CSSI-txt space-weather
  parser (`CssiTxtSpaceWeatherProvider`). `617eb62`
- **Oct-2024 error analysis** (single Oct-1 seed vs. actual Oct-7/8 POEORB): position- and
  velocity-error graphs + 6-element tables, two-body vs full-force. Then-believed cause of the
  ~13,000 km error: a seed phase offset — **later found (Day 5) to be an S3A/S3B satellite
  mismatch**: the Oct-1 seed was Sentinel-3B, the truth Sentinel-3A (~140° apart). `a4b8a15`
  (this report was later consolidated into `docs/reports/perturbation-ladder-truestate-7day/`).
- **Perturbation-model sweep**: with orbit determination the error floor drops from
  ~13,000 km to **224 m**. Ranking: gravity ≫ drag > Sun/Moon ≫ tides/relativity/albedo
  (the last group within noise at a ~1-day horizon). NRLMSISE-00 is the best atmosphere. `4f0763c`
- **orekit-data integration**: `Finals2000AEopProvider` (finals2000A.all EOP),
  header-aware ICGEM `.gfc` loader, `--eop-file` / `--gravity-file` on
  validate/fit/forecast/backtest, `docs/OREKIT_DATA.md`. `85cadd3`, `9cab0e0`

**Takeaway:** determination quality dominates; force selection is the second-order
refinement. The efficient stack is gravity + NRLMSISE drag (fitted Cd) + Sun/Moon + SRP.

---

## Day 4 — Oct 13: real orekit-data, and what actually helps

Supplied the real orekit-data files (`finals2000A.all`, `finals.all`, `eigen-6s.gfc`,
a DE440 excerpt, and the full zip). Assessed each (see `OREKIT-DATA-ASSESSMENT.md`).

- Verified `Finals2000AEopProvider` parses the **real** finals2000A.all (Oct 7 2024:
  PM-x 0.2258″, PM-y 0.4003″, UT1-UTC 0.0589 s — matching the POEORB's UT1 to sub-ms).
- **Fixed the ICGEM loader** to handle EIGEN-6S's time-variable format (read static
  `gfc`/`gfct`, ignore `trnd`/`acos`/`asin`); added an EOP-aware `scoreCombos` overload.
- **Measured what helps** with real EOP:
  - OD (6 h fit): post-fit RMS **4.87 → 2.78 m (−43 %)**; Cd **5.17 → 4.02** (more physical).
  - Prediction (sweep, fit 6 h / predict 20 h): floor **224 → 141 m (−37 %)**.
  - Arc-length sweep: real EOP cuts error **~25 % up to ~12 h**, washing into noise by 24 h.
  - EIGEN-6S ≈ EGM96 at 20×20; DE440 not worth it for LEO; finals.all redundant.
- Wrote this journal, the assessment, and the handoff; committed the real data + results.

**Takeaway:** `finals2000A.all` is the single highest-value orekit-data file — always pass
`--eop-file`. It both tightens OD and, through a cleaner fit, lowers the prediction floor.

---

## Day 5 — Oct 13: reports, Orekit cross-check, and storm-capable density + box-wing + forecast

- **Perturbation reports** built and consolidated to one example: per-combination 6-element
  tables + held-out error (`731428b`), a 7-day OD-then-predict ladder (`f506380`), a true-state
  (no-fit) ladder (`c3f50a2`), then the old iterations removed leaving one example report
  (`a2df93d`).
- **Discovered the real cause of the earlier ~13,000 km "error":** an **S3A/S3B satellite
  mismatch** (the Oct-1 seed was Sentinel-3B, ~140° apart in-track from the S3A truth), not a
  force-model defect — the two-body-from-true-state baseline confirmed it.
- **Orekit head-to-head** (`0f0ef16`, separate `experiments/orekit-compare/` module): fed the
  identical force stack, Orekit lands at the **same ~1.9 km** 8-day floor as URSC → the floor is
  physics (fixed-Cd vs real density), not a URSC bug.
- **Storm-density A/B** (`c61eff3`): over the Oct 10–11 storm week, **JB2008 cut the 8-day RMS
  ~3× vs NRLMSISE** (863 → 271 m at best B) → motivated porting JB2008 into the core.
- Built three capabilities into the zero-dependency core:
  - **Phase 1 — JB2008 atmosphere** from scratch (SOLFSMY/DTCFILE drivers), validated vs an
    Orekit oracle to <1e-4. `43b6a0c`
  - **Phase 2 — attitude box-wing** drag/SRP area model (non-breaking seam; cannonball default). `7132db2`
  - **Phase 3 — space-weather forecast layer** with nominal/stressed scenario bands (storms are
    not deterministically forecastable, so forward prediction is a band, not a point). `b9c6737`

**Scratch-only investigations (findings kept; harnesses not committed, under `scratchpad/`):**
gravity **saturates at degree ~80** for 800 km (bit-identical 80→140); the **force-model
combination is not a robust lever** (128 subsets cluster within ~1–3 % and the "winner" flips
between epochs — a cancellation artifact); which density model wins under fixed Cd is likewise a
coin-flip. The 1-day error is drag-limited, not gravity-limited.

**Takeaway:** storm-aware density (JB2008) is the real model-side lever *when you can fit Cd*;
under a fixed Cd the floor is the drag/density mismatch, confirmed independently by Orekit.

---

## Day 6 — Oct 18–20: real CPOD geometry, DE440, and the `predict` terminal tool

The user's guide supplied the real Sentinel-3A CPOD macro-model (GMV-CPOD-TN-0027) and the
measured attitude (AUX_PROQUA quaternions, 1 Hz, Oct 16). Governing constraint confirmed: the
only per-satellite input is a **single state** (UTC + 6 elements), so **no OD / no Cd fit**.

- **CPOD 8-plate box-wing + measured attitude** (`69851ea`): real bus faces 2.84/5.41/6.10 m²,
  solar array 10.50 m² canted 24°, mass 1129.648 kg; new `attitude` package (`AttitudeProvider`,
  `NominalYawSteering`, `QuaternionFileAttitude`). Projected area validated vs the CPOD table
  (3.244/6.058/16.576 m²). Measured attitude matches the nominal yaw-steering law to **99.8 %**
  (−X ram, +Z nadir), so the nominal law is justified for epochs without telemetry.
- **DE440 JPL ephemeris reader** (`b258e72`): high-precision Sun/Moon behind the `CelestialBody`
  seam, analytic series the default/fallback; validated vs the real DE440 file.
- **`predict` terminal command** (`9108a0b`): type in the UTC + 6 Cartesian elements directly
  (flags or interactive yes/no per force model), pick any combination, get the predicted state,
  and — with `--truth` — the 1-day/8-day position error. The single-state workflow as a
  self-contained tool. 68 tests green; runnable jar.
- Housekeeping: ignore `scratchpad/` (`a7cd16a`).

**Measured, honest result (all confirm the same wall):** the real geometry and DE440 do **not**
reduce the single-state error — box-wing 85 m vs cannonball 72 m at 1 day (geometry just re-sets
the ballistic coefficient), DE440 99.92 vs analytic 99.79 m (the analytic Sun/Moon was already
good to 0.08°). Full stack / NRLMSISE / gravity 70 → **1-day 99.8 m, 8-day 1602 m**; swapping to
JB2008 / gravity 40 / no fine forces → 164 m / 4785 m.

**Takeaway:** the model is now physically faithful to the real spacecraft and fully tunable from
the terminal, but the single-state fixed-Cd floor is structural — the only lever that breaks it
is fitting Cd (or an along-track empirical acceleration), which needs an observation arc.
