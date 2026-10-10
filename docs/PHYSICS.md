# Physics & validation

What each model computes, where it comes from, and how it is verified. All
accelerations are in SI and in the GCRF (pseudo-inertial) frame. References:
Montenbruck & Gill, *Satellite Orbits*; Vallado, *Fundamentals of Astrodynamics*;
IERS Conventions (2010); SOFA.

## Time
TAI, UTC (full 1972–2017 leap-second table), TT (= TAI + 32.184 s), GPS
(= TAI − 19 s) and UT1 (≈ UTC unless an EOP file is supplied). `AbsoluteDate`
stores a uniform atomic timeline, so durations are leap-second-free; calendar
readings are produced for any scale on demand.

## Frames
`FramesFactory` provides GCRF (inertial) and ITRF (Earth-fixed). The default
`iau2006` transform is the full chain:

1. **Precession** — IAU-2006 Fukushima-Williams angles.
2. **Nutation** — IAU-2000B 77-term luni-solar series.
3. **Earth rotation** — Earth Rotation Angle → GMST(2006) + equation of the
   equinoxes = GAST.
4. **Polar motion** — small-angle `R1(−yp)·R2(−xp)` (identity without EOP).

A `simple` mode (GMST rotation only) is available for speed/comparison.
Earth-orientation parameters come from an `EopProvider` (constant, or an IERS EOP
C04 file via `IersEopProvider`).

## Bodies
Earth (central), plus **low-precision analytical Sun & Moon** ephemerides
(Montenbruck & Gill §3.3.2), accurate to ≈0.1° / a few hundred km — ample for
perturbation forcing. `OneAxisEllipsoid` gives geodetic latitude/longitude/altitude
via Bowring's method.

## Orbits
Cartesian, Keplerian and Equinoctial representations with loss-free conversions;
mean/eccentric/true anomaly handled via Newton's solution of Kepler's equation.

## Force models

| Model | Summary | Reference |
|-------|---------|-----------|
| Central attraction | `−μ r / r³` | — |
| Spherical-harmonic gravity | Cunningham/Gottlieb recursion in ITRF; selectable degree/order; EGM96 to d/o 70 | M&G §3.2.4 |
| Third body | `GM_b[(s−r)/\|s−r\|³ − s/\|s\|³]`, Sun/Moon | M&G §3.3 |
| Atmospheric drag | `−½ (Cd A/m) ρ \|v_rel\| v_rel`, co-rotating atmosphere | Vallado §8 |
| &nbsp;• ExponentialAtmosphere | piecewise-exponential (Vallado Table 8-4) | Vallado |
| &nbsp;• HarrisPriesterAtmosphere | diurnal-bulge density | M&G §3.5.1 |
| &nbsp;• NRLMSISE00Atmosphere | full empirical thermosphere, F10.7/Ap-driven | Picone et al. 2002 |
| Solar radiation pressure | `ν Cr (A/m) P₀ (AU/d)² û`, conical umbra/penumbra | Vallado §8 |
| Solid Earth tides | IERS-2010 ΔC̄/ΔS̄ for n=2,3 + k⁺ degree-4, Sun+Moon | IERS 2010 §6.2 |
| Ocean tides | IERS-2010 eq 6.15, FES2004 8-constituent (Q1,O1,P1,K1,N2,M2,S2,K2) to d/o 4 | IERS 2010 §6.3 |
| Earth albedo + IR | Knocke-Ries 2nd-degree zonal, integrated over the visible cap | Knocke & Ries 1988 |
| Relativity | Schwarzschild (+ optional Lense-Thirring) | IERS 2010 §10 |
| Empirical | constant radial/transverse/normal | — |

## Propagators
- **Numerical (Cowell)** — adaptive Dormand-Prince 5(4) or fixed RK4 over the
  summed forces; dense-output sampling, g-stop events.
- **DSST (semi-analytical)** — numerically-averaged mean-element engine; see
  below.
- **Keplerian** — unperturbed two-body baseline.

### DSST (numerically-averaged, mean elements)
Propagates the six mean equinoctial elements. Each step orbit-averages the
variational equations: since the osculating elements are functions of state and a
perturbation enters only `dv/dt`, each element changes at `∂E/∂v · γ`, averaged
over one revolution (Gaussian quadrature in eccentric anomaly), with the mean
motion `n(a)` added to the mean longitude. Reuses every force model; large step
→ ~20× faster than Cowell at 30 days, ~200× at 1 year. Outputs **mean** elements.

## Validation (`mvn test`, 32 tests)

| Check | Result |
|-------|--------|
| Orbit conversions | round-trip Cartesian↔Keplerian↔Equinoctial loss-free |
| Two-body energy | specific energy conserved to 1e-9 over 10 orbits |
| Numerical vs analytical | sub-metre agreement over one hour |
| J2 nodal regression | matches `−1.5 n J2 (Re/p)² cos i` secular rate |
| Time scales | TT offset, modern TAI−UTC = 37 s, calendar round-trip |
| **NRLMSISE-00** | reproduces the **official reference test case to 7 sig figs** (all species, mass density, both temperatures) |
| EGM96 load | denormalised C̄20 → J2 = 1.082627e-3; C22/S22/C40 match |
| Solid / ocean tides | magnitudes ~2e-7 / ~3e-8 m/s² at 600 km; vanish when sources removed; ocean varies with the tidal argument |
| Earth radiation | ~11% of direct SRP; →0 as area→0 |
| Lense-Thirring | ~1e-10 m/s²; Schwarzschild unchanged when off |
| **IAU frames** | nutation Δψ/Δε match **SOFA to 1e-13 rad**, obliquity to 1e-15; transform round-trips to identity |
| EOP file | parsed and linearly interpolated; fallback outside range |
| **DSST** | two-body exact; J2 nodal rate to ~1%; drag secular SMA decay matches Cowell to ~0.1 m over 3 days |

## Accuracy caveats (honest scope)
- Sun/Moon are low-precision analytical series (perturbation-grade).
- Without an EOP file, UT1 = UTC and polar motion = 0 (sub-arcsecond level).
- NRLMSISE-00 uses a constant space-weather provider unless a CelesTrak file is
  supplied.
- Ocean tides are the FES2004 8-constituent truncation; frequency-dependent
  (Step-2) solid-tide terms, the pole tide and de Sitter precession are omitted
  (negligible for LEO).
- DSST emits mean elements (short-period averaged out) and does not special-case
  tesseral resonance (GEO).
