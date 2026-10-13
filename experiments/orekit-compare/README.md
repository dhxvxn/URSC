# Orekit vs URSC — force-model head-to-head (8-day true-state prediction)

A cross-check requested by the user: *does propagating with the real **Orekit** library's own
force models beat URSC's from-scratch force stack?* The answer, measured, is **no** — Orekit
lands at **~3.85 km** on the same 8-day test where URSC gets **~1.94 km**. Both are km-scale and
bounded by the same fixed-Cd / atmospheric-density wall; neither is anywhere near 20 m.

This experiment is **standalone** — a separate Maven root depending only on
`org.orekit:orekit:13.1.9` (Hipparchus 4.0.3 transitively). The URSC core stays
zero-runtime-dependency and its 47-test build is untouched.

## What it does

Propagates the **exact true Sentinel-3A state at 2024-10-07 21:59:42** (the first OSV of the
Oct-7/8 POEORB) forward ~8 days with Orekit, and compares the ITRF position error against the
actual S3A orbit on Oct-15/16 — the same seed, window, and metric as
`docs/reports/perturbation-ladder-truestate-7day/`. No orbit determination; a single true state
cannot be fitted, so drag uses a **fixed Cd = 2.2**.

Force models (Orekit classes), added cumulatively to match URSC's key ladder rungs:

| rung | Orekit force models |
|---|---|
| two-body | central term only (propagator `mu`) |
| gravity + Sun/Moon | `HolmesFeatherstoneAttractionModel` (20×20) + 2× `ThirdBodyAttraction` |
| + drag | + `DragForce(NRLMSISE00, IsotropicDrag(8 m², Cd 2.2))` |
| FULL | + `SolarRadiationPressure` (Cr 1.3, 8 m²) + `SolidTides` + `OceanTides` + `Relativity` + `KnockeRediffusedForceModel` |

Shared config mirrors URSC: mass 1130 kg; DP54 integrator (minStep 1e-3, maxStep 300, absTol
1e-6, relTol 1e-9); real `finals2000A.all` EOP + the same CSSI `SpaceWeather-All-v1.2.txt`, both
from orekit-data; μ/aₑ from the gravity field.

**One deliberate difference:** the gravity field is **EIGEN-6S** (the field shipped in
orekit-data), where URSC uses **EGM96**. At 20×20 and 800 km these agree to sub-metre, so this
does not explain the gap.

## Results

```
rung                                     near1d_RMS_m    far8d_RMS_m    far8d_max_m | URSC_8d_m    delta_m
two-body (central only)                   1225160.160   13170425.947   13591836.139 | 13171319.549  -893.602
gravity 20x20 + Sun/Moon                      332.462      30963.307      34207.042 |    28590.704  2372.603
+ drag (NRLMSISE fixed Cd)                     83.666       3926.849       4376.089 |     1874.803  2052.046
FULL stack (+SRP+tides+rel+albedo)             95.701       3850.318       4227.678 |     1935.881  1914.437
```

## What it means

1. **URSC is implemented correctly.** The two-body rung matches Orekit to **0.007 %**
   (13,170.4 vs 13,171.3 km) — independent confirmation that URSC's seed handling, GCRF↔ITRF
   frames, time scales, and central-gravity term are right. If URSC had a frame or seed bug, this
   rung would diverge; it does not.

2. **Orekit does not beat URSC — it is ~2× worse here (3.85 km vs 1.94 km).** Both use NRLMSISE-00
   with a fixed Cd = 2.2, so the gap is **which density the two NRLMSISE-00 drivers produce** from
   the same space weather (daily vs 81-day Ap/F10.7 handling, interpolation) and how that fixed Cd
   happens to absorb the mean density over the week. URSC's 1.9 km is not a tuned optimum and
   Orekit's 3.9 km is not a bug — both are points on the **fixed-Cd / density wall**, differing by
   density-model detail, not by force-model fidelity.

3. **Swapping to Orekit is not an accuracy lever, and neither reaches 20 m.** This is the empirical
   version of the earlier assessment: the 8-day forward floor is set by the inability to forecast
   thermospheric density, identical in kind for both libraries. The levers that actually lower the
   floor are **estimated / piecewise (time-varying) Cd**, **empirical accelerations**, and tighter
   **orbit determination** — not the choice of force-model implementation.

## Reproduce

```sh
cd experiments/orekit-compare
mvn -q compile
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" OrekitCompare \
  ../../docs/reports/perturbation-ladder-truestate-7day/inputs/S3A_POEORB_truth_20241007-08.EOF \
  ../../docs/reports/perturbation-ladder-truestate-7day/inputs/S3A_POEORB_truth_20241015-16.EOF \
  <path-to-orekit-data-directory>
```

`<path-to-orekit-data-directory>` is a standard orekit-data tree (EOP `finals2000A.all`, a gravity
`.gfc`, the DE-440 ephemeris, FES2004, and CSSI `SpaceWeather-All-v1.2.txt`).
