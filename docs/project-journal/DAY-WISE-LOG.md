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
