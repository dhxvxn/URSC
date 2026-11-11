# GNSS Based Orbit Determination and Trajectory Prediction
### A complete, self-contained project report — LEO orbit determination and forecasting from a satellite's own GPS data

*An internship project. Test satellite: ESA **Sentinel-3A**.*

> **How to read this report.** It assumes **no background in physics, orbital mechanics, or
> satellites** — only ordinary school maths. Every physics term and every mathematical term is
> explained the first time it appears, and again in the **[Glossary](#glossary)** at the end. A
> computer-science student should be able to read this top to bottom and understand the whole
> project without looking anything up elsewhere. Part I builds the foundations; later parts use
> them.

---

## Contents

**Part I — Foundations (start here)**
1. [What is an orbit, and why doesn't a satellite fall?](#1-what-is-an-orbit)
2. [Describing an orbit: state vectors and orbital elements](#2-describing-an-orbit)
3. [Time in space](#3-time-in-space)
4. [Where is "where"? Coordinate frames](#4-coordinate-frames)

**Part II — The problem**
5. [The problem and why it matters](#5-the-problem-and-why-it-matters)

**Part III — The measurements**
6. [How a satellite measures its own position with GPS](#6-how-a-satellite-measures-its-own-position)

**Part IV — The physics of the forecast**
7. [The forces that move a satellite](#7-the-forces-that-move-a-satellite)
8. [Atmospheric drag and the density models (in depth)](#8-atmospheric-drag-and-the-density-models)

**Part V — The mathematics**
9. [The algorithms: positioning, integration, orbit determination, covariance](#9-the-algorithms)

**Part VI — The system**
10. [What was built](#10-what-was-built)
11. [The pipeline, step by step](#11-the-pipeline-step-by-step)
12. [The features (with screenshots)](#12-the-features-with-screenshots)

**Part VII — Evidence and judgement**
13. [Results and validation (the two-month campaign)](#13-results-and-validation)
14. [Problems met and solved](#14-problems-met-and-solved)
15. [Advantages — why this approach](#15-advantages--why-this-approach)
16. [Limitations and honesty](#16-limitations-and-honesty)
17. [How to run it](#17-how-to-run-it)
18. [**Glossary**](#glossary)

---

# Part I — Foundations

## 1. What is an orbit?

A **satellite** is an object that circles the Earth. An **orbit** is the path it follows. The
reason it does not fall straight down is one of the oldest ideas in physics, due to Newton:

> Imagine firing a cannonball horizontally from a very tall mountain. Fire it gently and it falls
> to the ground a short distance away. Fire it harder and it lands further away. Fire it hard
> enough and the ground curves away beneath it *as fast as it falls* — so it keeps missing the
> ground and circles the planet. That is an orbit: **continuous free-fall that keeps missing the
> Earth.**

The force pulling it down is **gravity**. **Newton's law of gravitation** says every mass pulls
every other mass with a force

$$ F = \frac{G\,M\,m}{r^2} $$

where *M* is the Earth's mass, *m* the satellite's mass, *r* the distance between their centres,
and *G* a universal constant. The force gets weaker with the **square** of the distance (twice as
far → one-quarter the force). Combined with **Newton's second law** (force = mass × acceleration,
$F = ma$), this tells us the satellite's acceleration at any instant, and therefore — if we know
where it is and how fast it is going *now* — where it will be a moment later. Repeating that
calculation millions of times is exactly what "predicting the trajectory" means (see
[§9](#9-the-algorithms)).

**Low Earth Orbit (LEO)** is an orbit a few hundred kilometres up. Our test satellite,
**Sentinel-3A**, orbits at about **815 km** and circles the Earth roughly every **100 minutes** —
about 15 times a day. (For comparison, a passenger jet flies at ~0.01 km; the GPS satellites sit
much higher, at ~20,000 km.)

**Why so many terms and forces later?** If the Earth were a perfect, smooth sphere in empty space,
the orbit would be a simple, unchanging ellipse (Kepler's result, 1609). The real Earth is lumpy,
has an atmosphere, and shares space with the Sun and Moon — so the orbit slowly changes. Modelling
those changes accurately is the whole challenge, and is what Parts IV–V are about.

## 2. Describing an orbit

There are two equivalent ways to say where a satellite is and where it's going.

**(a) State vector.** Six numbers: three for **position** (x, y, z) and three for **velocity**
(vx, vy, vz), in some agreed coordinate system (see [§4](#4-coordinate-frames)). This is the most
direct description and the one the computer integrates forward in time.

**(b) Orbital (Keplerian) elements.** Six numbers that describe the *shape and orientation* of the
ellipse instead of an instantaneous point. They are more intuitive for humans:

| Element | Symbol | Plain meaning |
|---|---|---|
| Semi-major axis | *a* | the **size** of the orbit (average radius) |
| Eccentricity | *e* | how **oval** it is: 0 = circle, closer to 1 = more stretched |
| Inclination | *i* | the **tilt** of the orbit plane relative to the equator (90° = over the poles) |
| Right ascension of ascending node | *Ω* (RAAN) | the **swivel** of the orbit plane around the Earth's axis |
| Argument of perigee | *ω* | the **rotation of the ellipse within its plane** (where the low point is) |
| True anomaly | *ν* | **where the satellite is** along the orbit right now |

The two descriptions carry the same information and the program converts losslessly between them.
Sentinel-3A is in a nearly circular (*e* ≈ 0.001), highly inclined (*i* ≈ 98.6°) orbit — a
**Sun-synchronous** orbit, meaning the plane turns about 1°/day so the satellite always crosses a
given latitude at the same local solar time (good for consistent lighting in Earth imaging).

**Perigee / apogee** are the lowest and highest points of the orbit. **Epoch** is the exact
instant a state is given for.

## 3. Time in space

Timing matters enormously: light and radio travel ~300,000 km/s, so a **one-microsecond** clock
error is a **300-metre** position error. Several different "times" are used, and converting between
them correctly is part of the engine:

- **TAI (International Atomic Time)** — a perfectly uniform count of seconds from atomic clocks.
- **UTC (Coordinated Universal Time)** — civil time. It is TAI minus a whole number of **leap
  seconds**. Leap seconds are occasionally inserted to keep UTC in step with the Earth's slightly
  irregular rotation. (The program carries the full 1972–2017 leap-second table.)
- **GPS time** — what the GPS system runs on: TAI minus 19 seconds, with **no** leap seconds (so
  it drifts away from UTC by whole seconds over the years). A **cut-off at 05:59:42 UTC** equals
  **06:00:00 GPS** in the tested period — this is why that odd-looking time is used.
- **UT1** — time tied to the actual rotation angle of the Earth; differs from UTC by less than a
  second (the difference, *UT1−UTC*, is a measured quantity — see [EOP](#4-coordinate-frames)).

## 4. Coordinate frames

To write down a position you need a coordinate system, called a **reference frame**. Two matter:

- **Inertial frame (GCRF / EME2000, also called J2000)** — fixed relative to the distant stars. It
  does **not** rotate with the Earth. The laws of motion are simplest here, so the physics
  simulation is done in this frame.
- **Earth-fixed frame (ITRF)** — rotates **with** the Earth, so continents stay put. Latitude,
  longitude and ground tracks live here, and GPS measurements are naturally in this frame.

Converting between the two is surprisingly involved, because the Earth's orientation in space is
not simple. Four effects must be modelled:

1. **Rotation** — the daily spin (~15°/hour).
2. **Precession** — the Earth's axis slowly sweeps a cone over ~26,000 years (like a wobbling
   top).
3. **Nutation** — smaller, faster nodding of the axis caused by the Moon's pull.
4. **Polar motion** — the rotation pole itself wanders by a few metres on the surface, irregularly.

The program implements the full international standard (IAU-2006 precession + IAU-2000B nutation +
Earth-rotation angle + polar motion), validated against the reference SOFA library. The small,
*measured* corrections for the Earth's irregular rotation and pole wander are called **Earth
Orientation Parameters (EOP)** and come from a file published by the **IERS** (International Earth
Rotation Service). Without an EOP file the program assumes UT1 = UTC and zero polar motion — fine
to metre level, which is adequate here.

Errors are often expressed in a satellite-centred frame called **RTN** (or radial / along-track /
cross-track): **radial** = up–down, **along-track** = forward–back along the velocity (this is
where drag error accumulates), **cross-track** = sideways. This separates "the orbit is the right
shape but the satellite is slightly ahead/behind" (along-track) from other error types.

---

# Part II — The problem

## 5. The problem and why it matters

Once a satellite is up, operators constantly need to answer two questions:

1. **Where is it now?** — **orbit determination (OD)**: reconstruct the true orbit from
   measurements.
2. **Where will it be soon?** — **trajectory prediction / propagation**: simulate the physics
   forward.

These underpin almost everything: pointing an imaging camera at the right spot, scheduling when a
satellite is visible to a ground station (so you can talk to it), predicting re-entry, and —
increasingly urgent — **collision avoidance** in a crowded orbital environment.

Modern LEO satellites carry a **GPS receiver** and record the navigation signals they hear, just
like a phone's GPS but in orbit. Those recordings are the raw material. The task of this project
was to build **one program** that turns them into a trustworthy orbit and forecast, is fully
configurable, writes the international standard output format, and runs on a **single computer with
no internet** (many space-operations facilities are "air-gapped" for security).

The concrete goals:

- Read the satellite's own GPS measurements and the public GPS correction products.
- Determine the orbit and forecast it with a full, switchable physics model.
- Write results in the international standard format (**CCSDS OEM**, [§6](#6-how-a-satellite-measures-its-own-position)).
- Provide a visual, interactive interface.
- Validate on a real satellite over a real period: **Sentinel-3A, 2 September – 2 November 2024**.

---

# Part III — The measurements

## 6. How a satellite measures its own position

**GNSS and GPS.** *GNSS* (Global Navigation Satellite System) is the general name for
satellite-positioning systems; **GPS** (Global Positioning System) is the American one, with ~30
satellites at ~20,000 km. Each GPS satellite continuously broadcasts a timing signal that says, in
effect, "I am satellite N, here is my position, and the time is exactly T."

**Pseudorange — measuring distance with a clock.** A receiver notes when a signal arrives and
compares it to when it was sent; the time difference × the speed of light gives the distance. This
measured distance is called a **pseudorange** — "pseudo" because the receiver's own clock is not
perfect, so a common clock error is baked into every measurement.

**Single-point positioning (SPP).** With pseudoranges to **four or more** GPS satellites at one
instant, you can solve for **four unknowns**: the receiver's three position coordinates *and* its
clock error. Geometrically, each pseudorange says "I am somewhere on a sphere of radius *d* around
GPS satellite N"; the spheres intersect at the receiver. More satellites, and better-spread
satellites, give a better fix (this spread is measured by **PDOP** — Position Dilution of
Precision; lower is better). Doing SPP at every epoch of the arc produces a cloud of position
**fixes** — the input to orbit determination.

**But you must know where the GPS satellites were.** A pseudorange is only useful if you know the
*transmitter's* position and clock at the moment of transmission. That information comes as **GPS
products**, available at different accuracies and delays:

| Product | Source | Accuracy | Available | Used when |
|---|---|---|---|---|
| **Broadcast** | sent by GPS itself | ~1–2 m | immediately (real time) | live operations |
| **Ultra-rapid** | IGS, predicted + observed halves | ~5 cm | within hours | near-real-time |
| **Rapid** | IGS | ~2.5 cm | ~1 day later | next-day analysis |
| **Final** | IGS | ~2 cm | ~1–2 weeks later | best retrospective orbit |

**IGS** (International GNSS Service) is a global science collaboration; its analysis centres —
**CODE** (Switzerland), **ESA**, **EMR** (Canada), **GFZ** (Germany) — each publish products. The
precise products come as two files: **SP3** (the GPS satellites' precise positions) and **CLK**
(their precise clocks). The program prefers the most accurate product that was actually available
at the chosen time, falling back automatically per-satellite and per-epoch.

**The file types in this project:**
- **GNSSRD** — Sentinel-3's file of the satellite's **raw GPS observations** (the pseudoranges).
  The *mandatory* measurement input.
- **RINEX** — the standard text format for GNSS data (the program reads RINEX 3 and 4).
- **POEORB** (Precise Orbit Ephemeris) — ESA's very accurate, after-the-fact reconstructed orbit
  for Sentinel-3. Used **only to score** our result (to measure error), **never** as an input, so
  the test stays honest.
- **PROQUA** — the satellite's measured **attitude** (which way it points); used only for advanced
  "box-wing" shape modelling.

**The output format.** Every result is written as a **CCSDS OEM** — the Consultative Committee for
Space Data Systems' *Orbit Ephemeris Message*, the international standard text file listing
position and velocity over time (optionally with uncertainty). This lets any other space tool read
our results. The program also exports **KML** (for Google Earth) and **CZML** (for the Cesium 3-D
viewer).

**Air-gapped / offline** means the computer has no internet connection; the entire program — 3-D
globe, maps, help, search — is bundled inside it, so nothing is ever fetched online. New data
enters on approved media copied into a watched folder.

---

# Part IV — The physics of the forecast

## 7. The forces that move a satellite

Predicting the orbit means solving the **equation of motion** — Newton's second law written for the
satellite:

$$ \ddot{\mathbf r} \;=\; \mathbf a_{\text{gravity}} + \mathbf a_{\text{drag}} + \mathbf a_{\text{SRP}} + \mathbf a_{\text{Sun,Moon}} + \mathbf a_{\text{tides}} + \mathbf a_{\text{relativity}} + \dots $$

Here $\mathbf r$ is the position, $\ddot{\mathbf r}$ its acceleration (the two dots mean "second
derivative with respect to time"), and the right-hand side is the **sum of all accelerations** from
every force. Each force is a separate, switchable module. Below, each is explained; the one that
dominates forecast error — drag — gets its own section ([§8](#8-atmospheric-drag-and-the-density-models)).

**7.1 Central gravity (the main term).** To first approximation the Earth is a point mass and the
acceleration is $-\mu\,\mathbf r / r^3$, where $\mu = GM$ is the Earth's gravitational parameter.
This alone gives Kepler's unchanging ellipse.

**7.2 Non-spherical gravity (spherical harmonics).** The real Earth is not a perfect sphere: it
bulges at the equator and has density variations. Its gravity is described by a **spherical-harmonic
expansion** — think of it like building a complicated 3-D shape out of a sum of simpler standard
"bumps," each with a coefficient. The bumps are organised by **degree** (how fine the north–south
detail is) and **order** (how fine the east–west detail is). The biggest correction, called **J2**,
captures the equatorial bulge; it causes the orbit plane to slowly swivel (**nodal regression**) and
the ellipse to rotate — effects the program reproduces to match the textbook formula. The program
uses the **EGM96** gravity field up to **degree and order 70** (written "70×70"). Higher degree
means finer detail but more computation (cost grows roughly with degree²); the accuracy gain
saturates around 70 at this altitude.

**7.3 Solar radiation pressure (SRP).** Sunlight carries momentum, so photons hitting the satellite
give a tiny but continuous push away from the Sun. It depends on the lit area, the surface
reflectivity (the coefficient **Cr**), and whether the satellite is in sunlight or in the Earth's
shadow. The shadow is modelled with a realistic cone geometry: the dark **umbra** (full shadow) and
the partial **penumbra** (the Sun is partly blocked).

**7.4 Third-body gravity (Sun and Moon).** The Sun and Moon also pull on the satellite. Because they
pull a little differently on the satellite than on the Earth as a whole, the *difference* perturbs
the orbit. The Sun and Moon positions come from compact analytical formulas (accurate to a fraction
of a degree, ample for this purpose), or from a high-precision **JPL DE440** ephemeris file if
supplied.

**7.5 Solid and ocean tides.** The same Sun/Moon pull that raises ocean tides also flexes the solid
Earth and redistributes ocean water, which slightly changes the Earth's gravity field from moment
to moment. Both the **solid-Earth tide** and the **ocean tide** (using the bundled FES2004 model)
are included, following the international IERS-2010 conventions.

**7.6 General relativity.** Newton's gravity is extremely accurate but not perfect; Einstein's
**general relativity** adds a tiny correction (the **Schwarzschild** term), plus an even smaller
effect from the Earth's rotation dragging spacetime around with it (**frame dragging**, the
**Lense–Thirring** term). Both are included for completeness.

**7.7 Earth albedo and infrared.** Sunlight reflected off the Earth (**albedo**), and heat radiated
by the Earth (**infrared**), also push on the satellite — a smaller cousin of solar radiation
pressure, modelled with the Knocke–Ries method.

**7.8 Empirical acceleration.** A small, constant adjustable push used to absorb any force the
physics models miss. Off by default.

## 8. Atmospheric drag and the density models

This force deserves its own section because **it is the single largest source of forecast error for
LEO**, and the "density models" are exactly what makes it hard.

**Why drag matters.** Even at 800 km there is an extremely thin trace of atmosphere. As the
satellite ploughs through it at ~7.5 km/s, it feels air resistance that slowly saps energy and
lowers the orbit. The **drag acceleration** is

$$ \mathbf a_{\text{drag}} \;=\; -\tfrac{1}{2}\,\rho\,\frac{C_d A}{m}\,v^2\,\hat{\mathbf v} $$

where **ρ (rho)** is the local air **density**, *v* the speed, $\hat{\mathbf v}$ the direction of
motion, *A* the cross-sectional area, *m* the mass, and **Cd** the drag coefficient. The grouping
$C_d A / m$ is the **ballistic coefficient** — how "draggy" the satellite is for its mass.

**Why it is hard.** Everything in that formula is known reasonably well *except* the density **ρ**.
The density of the upper atmosphere at a fixed altitude can vary by a **factor of ten or more**
depending on the Sun's activity — and the Sun is not predictable days ahead. So the forecast error
is dominated by how well we can model and predict ρ. This is why the program offers several
**density models** (also called **atmosphere models**), trading speed against realism:

**8.1 Exponential.** The simplest: density falls off exponentially with height,
$\rho = \rho_0\,e^{-(h-h_0)/H}$, where *H* is the **scale height** (the height increase over which
density drops by a factor *e* ≈ 2.718). It has **no** time variation at all. Fast, crude — good for
quick illustrative runs.

**8.2 Harris–Priester.** Adds the **diurnal bulge**: the atmosphere is denser on the day side than
the night side, because the Sun heats and inflates it. The densest point lags a bit after local
noon (around 2 p.m. local solar time). The model interpolates between tabulated minimum (night) and
maximum (day) density profiles using the angle between the satellite and the bulge apex. It still
does **not** respond to solar activity, but it captures the day/night asymmetry cheaply.

**8.3 NRLMSISE-00 (the default, high-fidelity model).** "NRL-MSIS-E-00" stands for *US Naval
Research Laboratory — Mass Spectrometer and Incoherent Scatter radar — Extended — year 2000*. It is
an **empirical** model: rather than solving atmospheric physics from first principles, it was fitted
to decades of real measurements (satellite drag data, mass-spectrometer readings from spacecraft,
and ground radar). Given an altitude, latitude, longitude, local solar time, day of year, and the
current solar/geomagnetic activity, it returns the **temperature** and the **number densities of
each gas** (molecular nitrogen N₂, atomic oxygen O — the dominant species up there — molecular
oxygen O₂, helium He, hydrogen H, argon Ar, atomic nitrogen N), from which the total mass density ρ
follows. It reproduces the model's official reference test case to 7 significant figures in the
program's test suite. This is the most realistic of the shipped options and the default.

**8.4 JB2008 (Jacchia–Bowman 2008).** Another high-fidelity empirical model, designed to track
density **during geomagnetic storms** especially well by using extra solar indices. An alternative
to NRLMSISE-00 for the most demanding cases.

**8.5 What drives the high-fidelity models: space weather.** NRLMSISE-00 and JB2008 need to know how
active the Sun is, supplied as two **space-weather indices**:

- **F10.7** — the Sun's radio emission at a 10.7 cm wavelength, measured daily. It is an excellent
  *proxy* for the extreme-ultraviolet radiation that heats and puffs up the thermosphere. High F10.7
  → hotter, denser upper atmosphere → more drag.
- **Ap** — a daily **geomagnetic** activity index. During geomagnetic storms (driven by bursts from
  the Sun), energy is dumped into the upper atmosphere near the poles, heating it and raising density
  globally. High Ap → more drag.

These indices come from a space-weather file (the program reads a CelesTrak CSSI file for historical
values); when none is supplied it falls back to constant values. **Because F10.7 and Ap cannot be
predicted precisely days ahead, even a perfect density model leaves an irreducible forecast
uncertainty** — the ~20 km-after-7-days drift seen in the results ([§13](#13-results-and-validation))
is largely this.

---

# Part V — The mathematics

## 9. The algorithms

Four computational steps turn raw GPS measurements into a scored forecast.

**9.1 Single-point positioning (least squares at one instant).** At each epoch we have several
pseudoranges and four unknowns (position x, y, z and clock error). Because there are usually *more*
measurements than unknowns, and each is noisy, we don't solve exactly — we find the position+clock
that **best fits** all the pseudoranges by **least squares**: minimise the sum of the squared
differences between measured and computed distances. Since distance depends non-linearly on
position, this is solved iteratively: start from a guess, linearise, correct, repeat until it
settles. The result is one position **fix** per epoch (plus quality numbers like how many satellites
were used and the PDOP geometry factor).

**9.2 Numerical integration (turning forces into a trajectory).** The equation of motion
([§7](#7-the-forces-that-move-a-satellite)) is a differential equation: it gives the *acceleration*
at any state, and we want the *path* over time. There is no exact formula once drag and lumpy gravity
are included, so we integrate **numerically** — step the state forward in tiny time increments,
re-evaluating all the forces at each step. The program uses the **Dormand–Prince 5(4)** method, a
**Runge–Kutta** integrator that computes each step two ways (to 5th-order and 4th-order accuracy); the
difference estimates the local error, which it uses to **adapt the step size** automatically —
taking small steps where the dynamics are changing fast and large steps where they're smooth. Two
**tolerances** (relative `relTol` and absolute `absTol`) set how small that per-step error must be:
tighter tolerances mean more, smaller steps — more accurate but slower. This "integrate the summed
forces directly" approach is called **Cowell's method**. (A faster **DSST** mean-element propagator
also exists for very long runs.)

**9.3 Batch least-squares orbit determination (fitting one orbit to all the fixes).** The position
fixes from §9.1 are scattered and noisy and do **not** lie on any single perfect orbit. OD finds the
*one physically consistent orbit* that best fits them all. It works like this:

1. Guess an initial state (position + velocity at the cut-off).
2. Propagate it ([§9.2](#9-the-algorithms)) and compare the predicted positions to the fixes; the
   leftover differences are the **residuals**.
3. Work out how each residual would change if we nudged each component of the initial state — the
   matrix of these sensitivities is the **Jacobian**, computed here by **finite differences** (nudge
   one component a little, re-propagate, see how much the output moves). Using finite differences
   means the method works with *any* force model, with no hand-derived formulas.
4. Solve for the correction to the initial state that most reduces the squared residuals
   (**Gauss–Newton** step), with safeguards (parameter scaling and step-halving) so it converges
   smoothly.
5. Repeat until it stops improving.

The quality of the final fit is the **post-fit RMS** — the root-mean-square residual (in metres)
after convergence. A related check, the **variance factor**, compares the actual residual scatter to
the measurement noise we assumed: ≈1 means our noise assumption was about right. Optionally the drag
coefficient Cd (and reflectivity Cr) can be *estimated* alongside the state, which absorbs modelling
error and tightens the fit.

**9.4 Covariance (how confident are we?).** A single predicted position is not the whole story; we
also want its **uncertainty**. The **covariance** is a small matrix that says how uncertain each
component is and how the errors in different directions are correlated (e.g. along-track uncertainty
usually grows fastest because of drag). OD produces a covariance at the cut-off; to report it along
the forecast, the program **propagates** it forward using the **state-transition matrix (STM)** —
the matrix describing how a tiny change in the initial state maps to a change at a later time —
again obtained by finite differences (perturb each initial component, propagate, difference). The
result is written into the OEM and can be shown as an uncertainty ellipsoid or as radial/along/cross
(**RTN**) sigma curves. (Covariance costs roughly one extra propagation per estimated parameter, so
it is optional and off in the fast default profile.)

---

# Part VI — The system

## 10. What was built

A single Java application with **no external runtime libraries** (the physics core is plain,
auditable Java), in three layers:

- **The engine** — the numerical core from Part V: readers for every input format, single-point
  positioning, batch orbit determination, the switchable force models from Part IV, the numerical
  integrator, and the CCSDS OEM writer.
- **The data catalogue** — scans input files, recognises each by its **content** (not its name),
  records its time coverage, satellite, and a **SHA-256 checksum** (a short fingerprint of the file's
  bytes, so every result records exactly which inputs produced it), and selects the right files for a
  run.
- **The interface** — a small local web server that serves a browser interface (3-D globe, 2-D map,
  forms, plots). The browser is only the window; nothing leaves the computer.

![The Data tab: every input file catalogued by content, with a coverage timeline.](report-img/01-data-catalog.png)

*Figure 1 — The Data tab. Each file is identified by its content and placed on a coverage timeline
(blue = GPS observations, gold = broadcast navigation, purple = reference orbits, pink = attitude).*

## 11. The pipeline, step by step

For one cut-off time, the program runs the whole chain:

1. **Pick the inputs** from the catalogue: the satellite's GPS observations over the arc (6 hours by
   default), the best available GPS products, and (for scoring only) the POEORB truth orbit.
2. **Single-point positioning** at each epoch — about **73 fixes** over a 6-hour arc sampled every
   300 s ([§9.1](#9-the-algorithms)).
3. **Orbit determination** — batch least-squares fits one orbit to all the fixes and reports the
   post-fit RMS ([§9.3](#9-the-algorithms)).
4. **Propagation (forecast)** — integrate that orbit forward (7 days by default), applying all the
   forces from Part IV, writing a position every 60 s ([§9.2](#9-the-algorithms)).
5. **Output** — write a CCSDS OEM (optionally with covariance), plus KML, CZML, a printable report
   and a log. If a POEORB truth orbit is present, also compute the **error versus POEORB** for
   display.

![The Run tab, opened on the tested Sentinel-3A window with the inputs pre-selected.](report-img/02-run-setup.png)

*Figure 2 — The Run tab. It opens on the tested Sentinel-3A settings. The forecast length has
one-click 7/14/21/30-day presets; the cut-off can be filled from the data automatically; every
setting has an ⓘ note explaining it.*

## 12. The features (with screenshots)

### 12.1 The 3-D globe and the interactive 2-D map

Results appear on a 3-D globe and a flat **ground-track** map (the path of the point directly below
the satellite) side by side. **Hovering the map scrubs time** across the globe and the plots at
once; clicking pins a moment. Layers (forecast track, reference orbit, day/night shading, ground
stations) toggle on and off.

![Results: the 3-D globe and the interactive 2-D ground-track map.](report-img/03-results-globe-map.png)

*Figure 3 — The Results view. Left: the orbit on the 3-D globe. Right: the ground track on the 2-D
map with day/night shading and a legend. Moving the mouse over the map updates the latitude,
longitude, altitude and the error-versus-truth readout live.*

### 12.2 Realistic and generic satellite builds

Sentinel-3A is drawn as a recognisable 3-D model (body, solar wing, instruments); any other
satellite gets a clean generic build. A **follow camera** rides alongside the satellite.

<p>
<img src="report-img/04-sentinel3-build.png" alt="Sentinel-3A build in follow mode" width="48%"/>
<img src="report-img/05-generic-build.png" alt="Generic satellite build" width="48%"/>
</p>

*Figure 4 — Left: the Sentinel-3A build flown nadir-pointing (facing Earth) in follow mode. Right:
the generic build used for any other satellite.*

### 12.3 Running a whole window (the two-month data)

The **Window** tab repeats the OD + forecast for every day in a date range — the test campaign is
**61 daily cut-offs** — and charts the trends: orbit-fit quality, number of GPS fixes, and, where a
reference orbit exists, the forecast error. This is how the two-month data set is turned into a
single picture.

![Window trends: fit quality, fixes and error across the 61-day campaign.](report-img/06-window-trends.png)

*Figure 5 — The two-month window result: 61 of 61 days produced a forecast, with a median fit error
of 2.35 m and none flagged as a possible manoeuvre.*

### 12.4 Using precise IGS products (Reconstruction mode)

In **Reconstruction** (retrospective) mode the program uses the most accurate product present — the
**IGS Final** orbit (SP3) and clock (CLK). Figure 6 shows a run that used CODE Final products for 72
of its 73 epochs.

![A reconstruction run using IGS Final (CODE) products.](report-img/07-reconstruction-final.png)

*Figure 6 — A reconstruction run at the 2024-10-15 cut-off. "GPS products used" lists IGS Final
(COD); post-fit RMS 2.456 m; the orbit is rendered on the globe and map.*

### 12.5 Comparing GPS product tiers

A one-click **"Compare product tiers"** runs the same case with Broadcast, Ultra-rapid, Rapid and
Final products and overlays each one's **error against the POEORB truth** on a single chart — so you
can see directly how much the product choice matters.

![Product-tier comparison: error versus POEORB truth for each tier.](report-img/08-tier-comparison.png)

*Figure 7 — The four tiers overlaid. See [§13](#13-results-and-validation) for what this reveals.*

### 12.6 Offline help with retrieval-augmented answers (RAG)

The Help tab answers plain-language questions **without the internet** using **RAG**
(*Retrieval-Augmented Generation*): it first **retrieves** the most relevant passages from the
program's own documentation (ranked by a classic text-search method called **BM25** — it scores a
passage by how well and how distinctively its words match the question), then **generates** an answer
**from those passages only**, citing each source — so it cannot invent facts.

![The RAG help pipeline: retrieve from bundled docs, then answer with citations.](report-img/09-rag-help.png)

*Figure 8 — How the offline help works: your question → retrieve passages (BM25) → generate a cited
answer. An optional local model (Ollama) or a cloud model can phrase the answer from the same
retrieved passages; the retrieval step always stays offline.*

### 12.7 Diagnostics

Every run also produces engineering plots: altitude over time, GPS geometry and the number of
satellites in view, the positioning residuals, the orbit-determination residuals, and — where a
truth orbit exists — the radial/along-track/cross-track error.

![Diagnostic plots for a run.](report-img/10-diagnostics.png)

*Figure 9 — Diagnostic plots, which let an analyst judge the quality of the fit and the forecast.*

---

# Part VII — Evidence and judgement

## 13. Results and validation

**The two-month Sentinel-3A campaign.** The program ran one OD + forecast for each of the **61 daily
cut-offs** from 3 September to 2 November 2024, with the frozen test settings (6-hour measurement
arc, 7-day forecast, EGM96 70×70 gravity, NRLMSISE-00 drag).

| Measure | Value | What it means |
|---|---|---|
| Forecasts issued | **61 of 61** | every day converged; none flagged as a possible manoeuvre |
| Orbit-fit post-fit RMS | median **2.35 m** (1.85 – 4.38 m) | how closely the fitted orbit matches the GPS fixes |
| GPS position fixes per cut-off | 73 | a fix every 300 s over the 6-hour arc |
| Run time per cut-off | ~125 s | ≈ 2 hours for the whole window on one computer |
| Error vs POEORB **at the cut-off** | 2.17 m (8 Oct), 0.48 m (16 Oct) | how good the orbit is *now* |
| Error vs POEORB **after 1 day** | 268 m (7 Oct), 28.7 m (15 Oct) | how good the *forecast* is one day out |

Only days whose Sentinel-3A reference orbit is in the bundled data have an error figure — the
reference is for scoring, never an input.

**What the product-tier comparison reveals (Figure 7).** At a single cut-off the four tiers come out
very close (post-fit RMS: Broadcast 2.07 m, Ultra-rapid 2.56 m, Rapid 2.49 m, Final 2.45 m — a few
metres apart). On the error-versus-truth chart they almost coincide, because over a 7-day forecast
the orbit drifts ~**20 km** through **atmospheric-drag uncertainty** ([§8](#8-atmospheric-drag-and-the-density-models)),
which dwarfs the metre-level differences between GPS products. This is a genuine, useful finding:
**for a multi-day LEO forecast, the drag model and the forecast horizon dominate the error, not the
GPS product.** The product choice matters most for the *instantaneous* orbit (at the cut-off), where
the error is sub-metre to a few metres.

**Unit and reference validation.** Beyond the campaign, 120+ automated tests check the maths:
loss-free orbit conversions, energy conservation under two-body motion, the J2 nodal-regression
formula, the leap-second table, NRLMSISE-00 against its official reference case to 7 significant
figures, batch-OD recovery of a known state and drag coefficient from synthetic data, and CCSDS OEM
round-trips. The propagator is also checked directly against real Sentinel-3A precise orbits.

## 14. Problems met and solved

Real engineering is finding and fixing problems; two worth recording:

- **Wrong-satellite reference orbit.** One repository file named `S3A_POEORB_…` actually contained
  **Sentinel-3B** data (its own header said so). The program had compared Sentinel-3A forecasts
  against it, producing a nonsensical ~13,000 km "error" for two days. **Fix:** the catalogue now
  reads which satellite a reference orbit is for *from the file's header* and only compares against
  the matching satellite. The forecasts themselves were always correct.

- **Rejected precise clocks.** The main precise products are from **CODE**, and their high-rate clock
  files place one character of the header one column further right than the standard. The reader was
  rejecting all 69 CODE Final clocks, which would have stopped the Final orbits from being usable.
  **Fix:** accept the file-type character in either column. Reconstruction runs now use IGS Final
  products end to end (Figure 6).

Both came with a regression test so they cannot silently return.

## 15. Advantages — why this approach

- **It uses the satellite's own GPS data** — no ground tracking network needed; the measurements come
  for free with the spacecraft.
- **Fully modular physics.** Every force is an independent switch with a model choice, so you can run
  a quick coarse analysis or a high-fidelity one by changing *data, not code* — and you can see
  exactly what each force contributes.
- **Standards-based output.** CCSDS OEM, KML and CZML mean the results drop straight into other space
  tools and viewers.
- **Reproducible and auditable.** Every run stores its configuration and the SHA-256 checksums of its
  inputs, so any result can be reproduced and traced. The physics core is plain Java with no hidden
  dependencies.
- **Runs offline / air-gapped.** It works with no internet, as secure facilities require; even the
  help search is offline.
- **Honest validation.** It is measured against real ESA precise orbits, and the reference is never
  fed back into the computation.
- **Interactive and explainable.** The 3-D globe, interactive map, comparisons and the illustrated
  guide make it usable and understandable, not a black box.

## 16. Limitations and honesty

- **GPS products in real time.** A genuine *forecast* at the cut-off can only use what exists then —
  broadcast navigation and the predicted half of ultra-rapid. The more accurate Final/Rapid products
  appear ~1–2 weeks later and are used in *Reconstruction* mode, for the best retrospective orbit.
- **The 7-day drift.** Forecast error grows mainly through atmospheric drag, which depends on
  unpredictable solar activity; ~20 km after 7 days is expected for this altitude without updated
  space-weather input ([§8](#8-atmospheric-drag-and-the-density-models)).
- **Earth-orientation and ephemeris simplifications.** Without an IERS EOP file, UT1 = UTC and polar
  motion are zero (metre-level effect); Sun/Moon come from analytical series unless a DE440 file is
  supplied. Both are clearly marked and adequate here.
- **Rendering speed.** The screenshots and video were produced on a server with no graphics card, so
  the 3-D satellite is enlarged for visibility and some captures are time-lapsed; on a normal
  computer the globe animates smoothly.
- **Licensing.** The NRLMSIS 2.0 and DTM2020 atmosphere models are not included for licensing
  reasons; the shipped drag options are exponential, Harris-Priester, NRLMSISE-00 and JB2008.

## 17. How to run it

Full install and run instructions for **Linux and Windows** (from installing Java and Maven through
building and launching) are in the project **[README](../README.md)**. In short:

```bash
mvn clean package                               # build (or add -DskipTests to go faster)
java -jar target/leo-trajectory-0.1.0.jar gui   # start the browser interface
```

Then open the printed `http://127.0.0.1:…` address, press **▶ Run** (or **⚖ Compare product
tiers**) on the Run tab, and inspect the Results and Window tabs. The bundled two-month Sentinel-3A
data is ready to use immediately.

---

## Glossary

*Rough order: general → specific. Every physics and mathematics term used above appears here too.*

**Satellite** — an object placed in orbit around the Earth.

**Orbit** — the path a satellite follows; physically, continuous free-fall that keeps missing the
Earth.

**LEO (Low Earth Orbit)** — an orbit a few hundred km up (Sentinel-3A ≈ 815 km), circling the Earth
about every 100 minutes.

**Gravity / Newton's law of gravitation** — the attraction between masses, $F = GMm/r^2$; it weakens
with the square of distance.

**Newton's second law** — force equals mass times acceleration ($F = ma$); lets us turn forces into
motion.

**Acceleration** — the rate of change of velocity; the "second derivative" of position with respect
to time, written $\ddot{\mathbf r}$.

**Gravitational parameter (μ)** — the product $G \times (\text{Earth's mass})$; appears in the orbit
equations.

**State vector** — six numbers giving a satellite's position (x, y, z) and velocity (vx, vy, vz) at
an instant.

**Orbital (Keplerian) elements** — six numbers describing the orbit's shape and orientation:
semi-major axis (size), eccentricity (ovalness), inclination (tilt), RAAN (plane swivel), argument
of perigee (ellipse rotation in-plane), true anomaly (position along the orbit).

**Semi-major axis (a)** — half the long diameter of the orbital ellipse; its overall size.

**Eccentricity (e)** — how oval the orbit is: 0 = circle, near 1 = very stretched.

**Inclination (i)** — the tilt of the orbit plane to the equator (90° = polar).

**Perigee / apogee** — the lowest / highest points of the orbit.

**Sun-synchronous orbit** — an orbit whose plane turns ~1°/day so it always crosses a given latitude
at the same local solar time (Sentinel-3A's orbit).

**Epoch** — a specific instant in time at which a state or measurement is given.

**Ephemeris** — a table of a satellite's position (and velocity) over time.

**TAI / UTC / GPS time / UT1** — time scales. *TAI* is uniform atomic time; *UTC* is civil time (TAI
minus leap seconds); *GPS time* is TAI − 19 s with no leap seconds; *UT1* follows the Earth's actual
rotation angle.

**Leap second** — an extra second occasionally inserted into UTC to keep it near the Earth's
rotation.

**Reference frame** — a coordinate system for positions. **GCRF / EME2000 / J2000** is *inertial*
(fixed to the stars); **ITRF** rotates *with* the Earth.

**Precession / nutation / polar motion** — slow (~26,000 yr), faster (Moon-driven), and irregular
(metre-scale) components of how the Earth's rotation axis and pole move; needed to convert between
inertial and Earth-fixed frames.

**EOP (Earth Orientation Parameters)** — measured corrections (UT1−UTC, polar-motion x/y) for the
Earth's irregular rotation, published by the **IERS**; supplied as a `finals2000A` file.

**RTN (radial / along-track / cross-track)** — a satellite-centred frame for expressing errors:
radial (up/down), along-track (forward/back, where drag error shows), cross-track (sideways).

**Orbit determination (OD)** — reconstructing the true orbit from measurements.

**Trajectory prediction / propagation** — simulating the orbit forward in time by integrating the
forces.

**GNSS / GPS** — satellite navigation systems. *GNSS* is the general term; *GPS* is the American one
(~30 satellites at ~20,000 km).

**GPS receiver** — a device that computes its position from GNSS signals; LEO satellites carry one.

**Pseudorange** — the distance a receiver measures to a GPS satellite from signal travel time
("pseudo" because the receiver clock error is still mixed in).

**Single-point positioning (SPP)** — solving for a receiver's 3-D position (and clock error) from the
pseudoranges at one instant.

**Fix** — one computed position from SPP at one epoch.

**PDOP (Position Dilution of Precision)** — a number describing how well-spread the GPS satellites
are; lower = better geometry = more accurate fix.

**GNSSRD** — the Sentinel-3 file of the satellite's raw GPS observations (pseudoranges); the
mandatory measurement input.

**RINEX** — the standard text format for GNSS observations and navigation messages (versions 3 and 4
read here).

**GPS products / tiers** — the GPS satellites' positions and clocks, in rising accuracy and delay:
**Broadcast** (real-time, ~1–2 m) → **Ultra-rapid** (hours, ~5 cm) → **Rapid** (~1 day, ~2.5 cm) →
**Final** (~1–2 weeks, ~2 cm).

**IGS (International GNSS Service)** — the global collaboration that publishes precise GPS products;
**CODE, ESA, EMR, GFZ** are its analysis centres.

**SP3 / CLK** — file formats for precise GPS **orbit** (SP3) and **clock** (CLK) products.

**Broadcast ephemeris** — the lower-accuracy GPS orbit/clock transmitted by the GPS satellites
themselves, available in real time.

**POEORB (Precise Orbit Ephemeris)** — ESA's very accurate, after-the-fact reconstructed orbit for
Sentinel-3; used here **only to score** the result, never as an input.

**PROQUA** — the Sentinel-3 file of the satellite's measured **attitude** (pointing direction); used
only for box-wing shape modelling.

**Forecast mode vs Reconstruction mode** — *Forecast* (cut-off-safe) uses only products available at
the cut-off, like a real operation; *Reconstruction* (retrospective) uses the best products present,
for the most accurate orbit after the fact.

**Cut-off (UTC cut-off)** — the instant separating measurement from forecast: OD uses the arc ending
here; the forecast runs forward from here.

**Forecast span** — how far ahead the orbit is predicted (default 7 days; 14/21/30 one click).

**Equation of motion** — Newton's second law for the satellite: acceleration = sum of all force
accelerations.

**Force model / perturbation** — any physical effect that changes the orbit; each is a switchable
module.

**Spherical-harmonic gravity** — a description of the Earth's non-uniform gravity as a sum of standard
"bumps" organised by **degree** (north–south detail) and **order** (east–west detail); **EGM96** is
the field used, to **degree/order 70** ("70×70").

**J2** — the largest non-spherical gravity term, from the equatorial bulge; causes **nodal
regression** (slow swivel of the orbit plane).

**Atmospheric drag** — air resistance that slowly lowers a LEO orbit; the largest source of forecast
error.

**Density (ρ)** — mass of air per unit volume; the hard-to-know quantity in the drag equation.

**Ballistic coefficient** — the grouping $C_d A / m$ (drag coefficient × area ÷ mass); how "draggy" a
satellite is for its mass.

**Drag coefficient (Cd)** — a dimensionless factor (~2.2 for satellites) in the drag equation.

**Scale height (H)** — the altitude increase over which atmospheric density drops by a factor *e* ≈
2.718 (used by the exponential model).

**Atmosphere / density models** — ways to compute ρ: **Exponential** (static, simplest),
**Harris–Priester** (adds the day/night **diurnal bulge**), **NRLMSISE-00** (empirical, full
thermosphere driven by solar/geomagnetic activity — the default), **JB2008** (empirical, strong on
geomagnetic storms).

**Diurnal bulge** — the day-side swelling of the upper atmosphere from solar heating, peaking around
2 p.m. local solar time.

**Thermosphere** — the hot, very thin upper atmosphere (roughly 80–600+ km) where LEO drag happens.

**Space weather (F10.7, Ap)** — indices of solar/geomagnetic activity that drive atmospheric density.
**F10.7** = solar radio flux at 10.7 cm (a proxy for atmosphere-heating UV); **Ap** = a daily
geomagnetic-activity index. Both raise density (and drag) when high.

**Solar radiation pressure (SRP)** — the push of sunlight's momentum on the satellite; depends on
reflectivity **Cr** and on eclipse (shadow).

**Umbra / penumbra** — the full-shadow and partial-shadow regions of the Earth's shadow, used in the
eclipse model.

**Third body** — the perturbing gravity of the Sun or Moon.

**Solid / ocean tides** — the flexing of the solid Earth and redistribution of ocean water under
Sun/Moon gravity, which slightly change Earth's gravity field.

**General relativity (Schwarzschild, Lense–Thirring)** — Einstein's tiny corrections to Newtonian
gravity; the Schwarzschild term and the frame-dragging (Lense–Thirring) term.

**Albedo / infrared radiation pressure** — pushes from sunlight reflected off the Earth and heat
radiated by the Earth.

**Empirical acceleration** — a small adjustable constant push used to absorb unmodelled forces.

**JPL DE440** — a high-precision planetary/lunar ephemeris file (optional, improves Sun/Moon
positions).

**Numerical integration** — solving a differential equation by stepping forward in small time
increments, re-evaluating the forces each step.

**Cowell's method** — integrating the summed forces directly to get the orbit (as opposed to
averaging methods).

**Runge–Kutta / Dormand–Prince 5(4)** — a family of numerical integrators; Dormand–Prince 5(4)
computes each step to 5th- and 4th-order accuracy, using the difference to control error and adapt
the step size.

**Adaptive step size** — automatically using small steps where the motion changes fast and large
steps where it's smooth.

**Tolerance (relTol / absTol)** — limits on the per-step integration error; tighter = more accurate
but slower.

**DSST** — a semi-analytical "mean element" propagator that averages the dynamics so it can take very
large steps; much faster for long (multi-month) runs.

**Residual** — the leftover mismatch between a measurement and the model at one epoch.

**Least squares** — fitting by minimising the sum of squared residuals.

**Gauss–Newton** — the iterative method used to minimise the squared residuals for a non-linear
model: linearise, correct, repeat.

**Jacobian / partial derivatives** — the matrix of how each residual changes when each unknown is
nudged; here computed by **finite differences** (nudge, re-propagate, difference), so it works with
any force model.

**Batch least-squares OD** — fitting one orbit to *all* the position fixes at once via Gauss–Newton.

**Post-fit RMS** — the root-mean-square residual (metres) after OD converges; the fit-quality figure.

**Variance factor** — the ratio of the actual residual scatter to the assumed measurement noise;
≈1 means the noise assumption was about right.

**Covariance** — a matrix stating the uncertainty of the estimated state and how errors in different
directions are correlated.

**State-transition matrix (STM)** — the matrix describing how a tiny change in the initial state maps
to a change at a later time; used to propagate covariance (computed here by finite differences).

**CCSDS OEM (Orbit Ephemeris Message)** — the international standard text format for an ephemeris;
every result is written in it. **KML/CZML** are formats for viewing tracks in Google Earth / Cesium.

**Ground track** — the path of the point on the Earth's surface directly below the satellite.

**SHA-256 checksum** — a short fingerprint of a file's contents, recorded so a result can be traced to
its exact inputs.

**Air-gapped / offline** — a computer with no internet connection; the whole program runs locally.

**RAG (Retrieval-Augmented Generation)** — answering a question by first *retrieving* relevant
documentation passages, then *generating* an answer from those passages only, with citations, so it
cannot invent facts. **BM25** is the classic text-ranking method used to retrieve the passages.

**Manoeuvre flag** — a warning raised when the OD residuals jump, which can indicate the satellite
fired a thruster during the arc.

---

*Project built as a reference-quality, extensible foundation for LEO orbit determination and
forecasting from a satellite's own GPS data. Test satellite: ESA Sentinel-3A. Validated on the
two-month campaign of 2 September – 2 November 2024.*
