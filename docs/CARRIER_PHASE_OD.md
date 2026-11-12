# Carrier-phase orbit determination

The default orbit determination fits **single-point-positioning (SPP) position fixes**: each
GNSSRD epoch is first turned into one 3-D point (metre-level noise), and the batch least-squares
fit then finds the orbit through those points. Setting **`od.method = CARRIER_PHASE`** (Run tab →
*Orbit determination* → *OD measurements* → *Code + carrier phase*) adds a second stage that fits
the orbit **directly to the GPS measurements**, using the carrier phase, which is about a hundred
times less noisy than the code.

Implementation: `estimation/CarrierPhaseOd.java`; wiring in `pipeline/OdForecastPipeline.java`;
test `CarrierPhaseOdTest`.

## What the GNSSRD file provides

The Sentinel-3 GNSSRD archive holds a RINEX 3.03 GPS file at 1 Hz with, per satellite,
`C1C L1C S1C C1W L1W S1W C2W L2W S2W`: code (C) and carrier phase (L) on L1 and L2, plus the
loss-of-lock indicator (LLI) of every phase. The header gives the antenna position in the
spacecraft frame (`ANTENNA: DELTA`, 2.881 / −0.190 / −0.794 m for Sentinel-3A). The reader now keeps the LLI
flags and the antenna delta.

## Measurement model

Per GPS satellite *s* and epoch *t* (GPS time), with the ionosphere removed by the
dual-frequency (ionosphere-free) combination:

```
P_IF = ρ + c·δt_r(t) − c·δt_s + Δ_Shapiro + ε_P          (code, ~1 m noise)
L_IF = ρ + c·δt_r(t) − c·δt_s + Δ_Shapiro + B_arc + ε_L  (phase, ~mm noise)
```

* **ρ**: geometric range from the LEO **antenna** to the GPS satellite at transmit time. It uses
  light-time iteration and the Sagnac (Earth-rotation) correction. The antenna position is the
  centre of mass plus *(ANTENNA: DELTA − centre of mass)* in the body frame, rotated by the
  attitude: nominal yaw steering, or the measured PROQUA attitude with the box-wing model.
* **δt_s**: the GPS clock from the selected product. The tier order is the same as for SPP
  (Final > Rapid > Ultra-rapid > Broadcast), cut-off-safe in Forecast mode.
* **c·δt_r**: one receiver-clock parameter per epoch.
* **B_arc**: one float ambiguity per continuous phase arc.
* **Δ_Shapiro**: the relativistic path delay. There is no troposphere at LEO altitude.

## Estimation

The fit runs as follows:

1. **Starting point.** The SPP-based batch solution seeds the fit.
2. **Joint solve.** One Gauss–Newton solve estimates the state together with every clock and
   ambiguity. Over a 6 h arc that is about 700 clocks and about 100 ambiguities. The orbit
   partials come from finite-difference propagation through the full force model.
3. **Cycle slips.** Arcs are split at:
   * LLI flags;
   * data gaps longer than 60 s;
   * Melbourne–Wübbena jumps larger than 2.5 wide-lane cycles, checked on 10 s data;
   * a change of GPS product;
   * gross geometry-free jumps larger than 1 m.

   Equal L1/L2 slips are invisible to both combinations. They are found after each solve as
   steps in the phase residuals.

   *Lesson from the real data:* at LEO speed the ionosphere alone changes the geometry-free
   combination by 10–30 cm in 10 s. A classic 8 cm geometry-free threshold therefore flagged 460
   false slips in 6 h; the current settings flag 3–4.
4. **Outliers** beyond 4 a-posteriori sigmas are rejected.
5. **Re-weighting.** A final variance-component step re-weights code and phase by their post-fit
   scatter, so the formal covariance is realistic.

The result replaces the SPP-based state at the arc epoch, and the forecast and covariance
propagation proceed unchanged. If the carrier-phase fit fails or looks implausible, the run keeps
the SPP solution and says so in the warnings. A failed fit means not converged or fewer than 100
phase observations. An implausible one has a phase RMS above 1 m or moves the state more than
200 m from the SPP solution.

## Settings

| Key | Default | Meaning |
|---|---|---|
| `od.method` | `SPP_POSITIONS` | `CARRIER_PHASE` enables the refinement |
| `od.phaseRateSeconds` | 30 | estimator sampling (slip detection uses 10 s data) |
| `od.codeSigma`, `od.phaseSigma` | 1 m, 0.01 m | starting weights (re-estimated) |
| `od.antennaOffset` | true | apply the header antenna offset |
| `spacecraft.comBodyX/Y/Z` | 0 (Sentinel-3A profile: 1.489, 0.217, 0.009 m) | centre of mass in the frame of `ANTENNA: DELTA` |

## Validation (Sentinel-3A, cut-off 2024-10-15 05:59:42 UTC, 6 h arc)

Errors are 3-D position errors against the POEORB precise orbit, which is used for scoring only.
*Broadcast* runs are in Forecast mode; *Final* runs use IGS Final (CODE) products in
Reconstruction mode. Cd is fixed at 2.2 unless stated.

| Run | Post-fit RMS | at cut-off | +1 h | +6 h | +24 h |
|---|---|---|---|---|---|
| SPP positions, broadcast | 2.07 m (positions) | 0.71 m | 1.35 m | 4.11 m | 28.7 m |
| **Carrier phase, broadcast** | 13.0 cm (phase) | 0.73 m | **0.72 m** | **3.62 m** | 29.6 m |
| SPP positions, Final | 2.45 m | 0.65 m | 1.55 m | 4.46 m | 29.6 m |
| **Carrier phase, Final** | 7.3 cm | 0.69 m | **0.77 m** | **3.79 m** | 30.4 m |
| Carrier phase, Final, no antenna offset | 18.8 cm | 0.99 m | 1.69 m | 4.80 m | 31.1 m |
| SPP positions, Final, Cd estimated | 2.44 m | 0.68 m | 1.15 m | 2.35 m | **3.44 m** |
| Carrier phase, Final, Cd estimated | 7.3 cm | 0.80 m | 0.72 m | 2.62 m | 16.1 m |

What this shows:

* **The fit is working.**
  * The phase fits at the centimetre-to-decimetre level.
  * The formal position sigma drops from metres to 1–3 cm.
  * The short-term orbit (+1 h) is about twice as accurate as with SPP.
* **The antenna offset matters.**
  * Applying it more than halves the phase residuals (18.8 → 7.3 cm).
  * It needs the centre of mass: the header's DELTA is measured from the spacecraft-frame origin,
    not from the centre of mass. Without that correction the orbit was 1.9 m off along-track.
* **It does not yet improve multi-day forecasts.**
  * Beyond a few hours the forecast error is set by drag modelling, not by the OD measurements.
  * With Cd estimated, the SPP fit forecasts better than the carrier-phase fit (3.4 m vs 16 m at
    24 h). A post-fit phase RMS of 7 cm with Final products is several times the expected
    1–2 cm, so a remaining measurement-model error is being absorbed into the orbit and Cd.

**Recommendation.** Keep `SPP_POSITIONS` as the default for forecasting. Use `CARRIER_PHASE` for
precise reconstruction of the arc and for short-term prediction, preferably with IGS Final or
Rapid products. This is one cut-off; a full 61-day campaign comparison is the next validation step.

## Known limitations / next steps

These are the remaining sources of systematic error:

* **GPS satellite antenna phase-centre offsets and variations.** IGS SP3 orbits refer to the
  satellite centre of mass. Correcting this needs the IGS ANTEX file. It is the most likely cause
  of the remaining 7 cm residual.
* **Phase wind-up.** Only its part common to all satellites is absorbed by the clock.
* **LEO antenna phase-centre variations.**
* **Dynamics over the arc.** The orbit is fitted with a strictly dynamic model. Empirical
  (reduced-dynamic) accelerations would absorb mismodelled forces over the arc.
* **Float ambiguities.** Ambiguity resolution to integers is not attempted.
