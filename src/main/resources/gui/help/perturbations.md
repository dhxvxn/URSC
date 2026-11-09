# Perturbations and models

Every force can be switched on or off independently and most have a choice of model, so you can measure the effect of each one yourself (run the same case twice and compare in History). Typical size of each effect on an ~800 km orbit over one day:

| Force | Typical effect / day | Models |
|---|---|---|
| Earth gravity field | kilometres (J2 alone: hundreds of km) | EGM96 to degree 70 (bundled), any ICGEM .gfc |
| Atmospheric drag | tens to hundreds of metres; largest uncertainty | NRLMSISE-00, JB2008, Harris-Priester, exponential |
| Sun and Moon gravity | tens of metres | analytic series or JPL DE440 |
| Solar radiation pressure | metres | cannonball or box-wing, conical shadow |
| Earth albedo / infrared | decimetres | Knocke-type model |
| Solid Earth tides | decimetres | IERS model |
| Ocean tides | centimetres-decimetres | FES2004 (bundled) |
| Relativity | centimetres | Schwarzschild, optional Lense-Thirring |

## Choosing a density model
NRLMSISE-00 is the default and gave the lowest 1-day error in the two-month Sentinel-3A study. JB2008 reacts to geomagnetic storms and uses its bundled solar indices. Harris-Priester and exponential are quick approximations. Beyond a few days the error is dominated by unpredictable solar activity and by manoeuvres, not by the model choice.

## Drag and radiation coefficients
Only Cd*A/m and Cr*A/m matter. With a fixed Cd the drag error sets the forecast error floor; estimating Cd in the OD (arcs of 6 h or more) usually lowers it.

## Gravity degree
The effect of high-degree terms fades with altitude; at 800 km the field saturates around degree 70-80. Degree 20-40 runs faster with little loss for multi-day forecasts.

## Integrator
Dormand-Prince 5(4) with absolute tolerance 1e-10 m and relative 1e-12 is converged for metre-level work; loosen to 1e-8 / 1e-10 for quick looks.
