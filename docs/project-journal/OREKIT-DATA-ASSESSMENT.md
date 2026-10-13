# orekit-data assessment — what helps, with evidence

Four files were supplied (plus the full orekit-data zip). This is a file-by-file verdict
on what each one does for URSC, backed by measurements on the real Sentinel-3A Oct-7/8
precise orbit. The orekit-data git host is network-blocked in this environment, so the
files were uploaded.

## Verdict table

| file | what it is | verdict | evidence |
|---|---|---|---|
| **`finals2000A.all`** | IERS EOP, IAU-2000A (UT1-UTC + polar motion) | **Use it — biggest single win** | OD residual −43 %, 20 h prediction floor −37 % (below) |
| `finals.all` | IERS EOP, IAU-1980 | redundant | identical PM + UT1 columns to finals2000A.all; only the nutation convention differs (dψ/dε vs dX/dY), which URSC does not use |
| `eigen-6s.gfc` | EIGEN-6S gravity model (GFZ/GRGS), d/o 240 | optional | ≈ EGM96 at 20×20; useful only if you go to high degree |
| `lnxp1990.440` | truncated DE440 planetary/lunar ephemeris (excerpt) | **not worth it for LEO** | analytic Sun/Moon already accurate to far below the error floor; see reasoning below |
| (in zip) `SpaceWeather-All-v1.2.txt` | CSSI space weather (F10.7/Ap) | already in use | drives NRLMSISE-00 drag via `--sw-file` |
| (in zip) `MSAFE/` | Marshall Solar Activity *forecasts* | future | belongs to the parked space-weather-forecasting roadmap, not current accuracy |
| (in zip) `tai-utc.dat` | leap seconds | not needed | URSC has leap seconds built in |
| (in zip) `fes2004_Cnm-Snm.dat` | FES2004 ocean-tide coefficients | not needed | URSC bundles an 8-constituent FES2004 ocean-tide model |

## The headline: `finals2000A.all` (real Earth orientation)

Real UT1-UTC **and polar motion** in the GCRF↔ITRF transform. Previously URSC used the
POEORB's own UT1 with **zero polar motion**. For Oct 7 2024 the real IERS values are
PM-x = 0.2258″, PM-y = 0.4003″, UT1-UTC = 0.0589 s.

### Effect 1 — orbit determination gets markedly better

`fit` on the first 6 h of the Oct-7 orbit (estimate state + Cd):

| | post-fit RMS | estimated Cd |
|---|---:|---:|
| no EOP (zero polar motion) | 4.87 m | 5.17 (inflated) |
| **real EOP** | **2.78 m** | **4.02** |

Real EOP nearly **halves the OD residual (−43 %)** and yields a **more physical drag
coefficient** — without it, the fit was absorbing the ~tens-of-metres polar-motion error
into an unphysically large Cd.

### Effect 2 — the prediction floor drops too

Force-model sweep (fit 6 h, predict the held-out ~20 h), least-error combination:

| | held-out prediction RMS |
|---|---:|
| no EOP | 224 m |
| **real EOP** | **141 m (−37 %)** |

See `results/eop_sweep_compare.png`. The drag rung's marginal gain triples with real EOP
(−46 m → −100 m): once the fit stops hiding frame error in Cd, the drag model extrapolates
far better.

### Effect 3 — the benefit is arc-length dependent

`validate` RMS vs. arc length (`results/validate_vs_hours.csv`):

| arc | no EOP | real EOP | change |
|---|---:|---:|---:|
| 1 h | 7.1 m | 5.2 m | −27 % |
| 3 h | 21.4 m | 16.3 m | −24 % |
| 6 h | 32.1 m | 24.3 m | −24 % |
| 12 h | 39.4 m | 35.1 m | −11 % |
| 24 h | 52.4 m | 57.5 m | +5 m (noise) |

Real EOP cuts error **~25 % on arcs up to ~12 h**, where the frame error is a real fraction
of the total. Beyond ~half a day, dynamic mismodelling (drag + gravity truncation)
dominates and the EOP term sinks into the noise. This is exactly why it helps OD most (fit
windows are short) and helps long-horizon prediction indirectly (through a cleaner fit).

**Recommendation:** pass `--eop-file finals2000A.all` on every run.

## `eigen-6s.gfc` (EIGEN-6S gravity model) — optional

Loads correctly via `--gravity-file` (the ICGEM loader was fixed to handle EIGEN-6S's
time-variable `gfct`/`trnd`/`acos`/`asin` lines — it now reads the static `gfc`/`gfct`
base coefficients and ignores the rate terms). At 20×20 it is **indistinguishable from the
bundled EGM96** (24 h validate RMS 52.4 m either way; EIGEN marginally better on peak
error). It only matters if you push to high degree (EIGEN-6S goes to 240), which buys
little at Sentinel-3's 800 km altitude. Keep EGM96 as default; use EIGEN-6S for high-degree
experiments.

## `lnxp1990.440` (DE440 excerpt) — not worth implementing for LEO

A truncated JPL DE440 binary (the file self-identifies as an excerpt). It would replace
URSC's analytic Sun/Moon ephemeris. **Why it doesn't help here:** the third-body
perturbation at 800 km is ~5×10⁻⁶ of the central term, and the analytic ephemeris places
the Sun/Moon to well under an arc-minute — the resulting third-body acceleration error is
far below a millimetre of orbit effect over a day, i.e. orders of magnitude under the
~140 m prediction floor. Parsing the DE440 binary (Chebyshev-coefficient records) is a
sizeable, error-prone effort with no measurable payoff at this accuracy. Deferred; revisit
only if URSC ever targets sub-metre LEO accuracy or high-altitude/cislunar regimes.

## `finals.all` (IAU-1980 EOP) — redundant

Byte-for-byte identical PM-x, PM-y and UT1-UTC columns to `finals2000A.all`; the two differ
only in the celestial-pole-offset convention at the end of each line (dψ/dε for IAU-1980 vs
dX/dY for IAU-2000). URSC uses the IAU-2006/2000 frame and reads only PM + UT1, so
`finals2000A.all` is the correct file and `finals.all` adds nothing. Kept here for
completeness.
