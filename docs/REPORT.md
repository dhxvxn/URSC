# GNSS Based Orbit Determination and Trajectory Prediction
### Project report — LEO orbit determination and forecasting from a satellite's own GPS data

*An internship project. Test satellite: ESA **Sentinel-3A**. This report is written so that a
reader new to the field can follow it; every specialised term is defined where it first appears
and again in the **[Glossary](#glossary)** at the end.*

---

## Contents
1. [The problem](#1-the-problem)
2. [Background: the ideas you need](#2-background-the-ideas-you-need)
3. [What was built](#3-what-was-built)
4. [How it works, step by step](#4-how-it-works-step-by-step)
5. [The features (with screenshots)](#5-the-features-with-screenshots)
6. [Results and validation](#6-results-and-validation)
7. [Problems met and solved](#7-problems-met-and-solved)
8. [Limitations and honesty](#8-limitations-and-honesty)
9. [How to run it](#9-how-to-run-it)
10. [Glossary](#glossary)

---

## 1. The problem

A satellite in **Low Earth Orbit (LEO)** — a few hundred kilometres up — circles the Earth about
15 times a day. To use it (point a camera, aim an antenna, avoid a collision, plan a ground-station
contact) you must know two things:

1. **Where is it now?** — called **orbit determination (OD)**: reconstructing the true orbit from
   measurements.
2. **Where will it be soon?** — called **trajectory prediction** or **propagation**: simulating
   the physics forward in time.

Modern LEO satellites carry a **GPS receiver** and record the satellite-navigation signals they
pick up, just like a phone's GPS but in space. Those recordings are the raw measurements. The
challenge of this project was to build **one program** that takes those recordings and produces a
trustworthy orbit and a forecast — and to make it usable, configurable, and able to run on a
**single computer with no internet connection** (many space-operations facilities are
"air-gapped" for security).

Concretely, the goals were:

- Read a satellite's own GPS measurements and the public GPS correction products.
- Determine the orbit and forecast it with a full, switchable physics model.
- Write the result in the international standard format (**CCSDS OEM**).
- Provide a visual, interactive interface to set up runs and inspect results.
- Validate everything on a real satellite over a real two-month period (**Sentinel-3A**,
  2 September – 2 November 2024).

---

## 2. Background: the ideas you need

**GNSS / GPS.** *GNSS* = Global Navigation Satellite System; *GPS* is the American one. ~30 GPS
satellites broadcast timing signals. A receiver that hears four or more can compute its own
position. Our LEO satellite's receiver does exactly this from orbit.

**GNSSRD.** The file type (for Sentinel-3) containing the satellite's **raw GPS measurements**
(the distances it measured to each GPS satellite, epoch by epoch). This is the *mandatory* input —
no measurements, no orbit.

**Where were the GPS satellites?** To turn a measured distance into a position you must know
where each *GPS* satellite was at that instant. That information comes as **GPS products**, which
exist in increasing accuracy and increasing delay:

| Product | Who / what | Accuracy | Available |
|---|---|---|---|
| **Broadcast** | transmitted by GPS itself | ~1–2 m | immediately (real time) |
| **Ultra-rapid** | IGS prediction | ~5 cm | a few hours (has a predicted half) |
| **Rapid** | IGS | ~2.5 cm | ~1 day later |
| **Final** | IGS | ~2 cm | ~1–2 weeks later |

*IGS* = the International GNSS Service, a global science collaboration. The program prefers the
most accurate product that is actually available for the chosen time.

**Single-point positioning (SPP).** The classic GPS calculation: from the measured distances at
one instant, solve for the receiver's 3-D position (and clock error). Doing this at every epoch
of the arc gives a cloud of position "fixes".

**Orbit determination (OD) — batch least-squares.** The fixes are noisy and don't lie on a perfect
orbit. OD finds the *single physically consistent orbit* that best fits them all, by least-squares
(minimising the sum of squared mismatches). We use a *batch* method: it adjusts the starting state
until the simulated orbit passes as close as possible to every fix.

**Propagation (the forecast).** Starting from that orbit, we integrate Newton's laws forward in
time, including every force that matters in LEO:
- **Gravity** — not a perfect sphere; Earth's bulges and lumps matter (modelled by a
  *spherical-harmonic* field, here EGM96 to degree/order 70).
- **Atmospheric drag** — even at 800 km there is thin air; it slowly lowers the orbit. This is the
  biggest source of forecast error because the air density depends on unpredictable solar activity.
- **Solar radiation pressure** — sunlight pushes on the satellite.
- **Sun and Moon gravity**, **solid and ocean tides**, **general relativity** — smaller effects,
  all included.

**The cut-off.** The **UTC cut-off** is the moment that separates "what we measured" from "what we
predict": OD uses the measurement arc ending at the cut-off, and the forecast runs forward from it.

**POEORB (the truth orbit).** After the fact, ESA publishes a very precise reconstructed orbit for
Sentinel-3 called **POEORB** (Precise Orbit Ephemeris). We use it only to *score* our result —
to measure our error — never as an input. This keeps the test honest.

**CCSDS OEM.** The Consultative Committee for Space Data Systems' **Orbit Ephemeris Message** — the
standard text file that lists position and velocity over time. Every result is written in this
format so other tools can read it.

**Air-gapped / offline.** A computer with no internet connection. The whole program — the 3-D
globe, maps, help documents, search — is bundled inside it, so nothing is ever fetched online.

---

## 3. What was built

A single Java application (no external runtime libraries) with three layers:

- **The engine** — the numerical core: readers for every input format, single-point positioning,
  batch orbit determination, the switchable force models, the numerical integrator, and the CCSDS
  OEM writer.
- **The data catalogue** — it scans input files, recognises each by its *content* (not its name),
  records its time coverage, satellite and a SHA-256 checksum, and picks the right files for a run.
- **The interface** — a small local web server that serves a browser interface (3-D globe, 2-D
  map, forms, plots). The browser is only the window; nothing leaves the computer.

![The Data tab: every input file catalogued by content, with a coverage timeline.](report-img/01-data-catalog.png)

*Figure 1 — The Data tab. Each file is identified by its content and placed on a coverage
timeline (blue = GPS observations, gold = broadcast navigation, purple = reference orbits, pink =
attitude).*

---

## 4. How it works, step by step

For one cut-off time, the program runs this pipeline:

1. **Pick the inputs.** From the catalogue it selects the satellite's GPS observations over the arc
   (6 h by default), the best available GPS products, and (for scoring only) the POEORB truth orbit.
2. **Single-point positioning.** At each epoch it solves for the satellite's position — about 73
   fixes over a 6-hour arc sampled every 300 s.
3. **Orbit determination.** Batch least-squares fits one orbit to all the fixes. It reports how
   well it fits — the **post-fit RMS** (root-mean-square mismatch, in metres).
4. **Propagation (forecast).** It integrates that orbit forward (7 days by default), applying
   gravity, drag, solar pressure, Sun/Moon, tides and relativity, writing a position every 60 s.
5. **Output.** It writes a CCSDS OEM file (with a formal **covariance** — the predicted
   uncertainty), plus KML, CZML, a printable report and a log. If a POEORB truth orbit is present,
   it also computes the **error versus POEORB** for display.

![The Run tab, opened on the tested Sentinel-3A window with the inputs pre-selected.](report-img/02-run-setup.png)

*Figure 2 — The Run tab. It opens on the tested Sentinel-3A settings. The forecast length has
one-click 7/14/21/30-day presets; the cut-off can be filled from the data automatically; every
setting has an ⓘ note explaining it.*

---

## 5. The features (with screenshots)

### 5.1 The 3-D globe and the interactive 2-D map

Results appear on a 3-D globe and a flat ground-track map side by side. **Hovering the map scrubs
time** across both the globe and the plots at once; clicking pins a moment. Layers (the forecast
track, the reference orbit, day/night shading, ground stations) can be toggled.

![Results: the 3-D globe and the interactive 2-D ground-track map.](report-img/03-results-globe-map.png)

*Figure 3 — The Results view. Left: the orbit on the 3-D globe. Right: the ground track on the 2-D
map with day/night shading and a legend. Moving the mouse over the map updates the latitude,
longitude, altitude and the error-versus-truth readout live.*

### 5.2 Realistic and generic satellite builds

Sentinel-3A is drawn as a recognisable 3-D model (body, solar wing, instruments); any other
satellite gets a clean generic build. A *follow camera* rides alongside the satellite.

<p>
<img src="report-img/04-sentinel3-build.png" alt="Sentinel-3A build in follow mode" width="48%"/>
<img src="report-img/05-generic-build.png" alt="Generic satellite build" width="48%"/>
</p>

*Figure 4 — Left: the Sentinel-3A build flown nadir-pointing (facing Earth) in follow mode. Right:
the generic build used for any other satellite.*

### 5.3 Running a whole window

The **Window** tab repeats the OD + forecast for every day in a date range — the test campaign is
61 daily cut-offs — and charts the trends: orbit-fit quality, number of GPS fixes, and, where a
reference orbit exists, the forecast error.

![Window trends: fit quality, fixes and error across the 61-day campaign.](report-img/06-window-trends.png)

*Figure 5 — The two-month window result: 61 of 61 days produced a forecast, with a median fit
error of 2.35 m and none flagged as a possible manoeuvre.*

### 5.4 Using precise IGS products (Reconstruction mode)

In *Reconstruction* (retrospective) mode the program uses the most accurate product present — the
**IGS Final** orbit and clock. Figure 6 shows a run that used CODE Final products for 72 of its 73
epochs.

![A reconstruction run using IGS Final (CODE) products.](report-img/07-reconstruction-final.png)

*Figure 6 — A reconstruction run at the 2024-10-15 cut-off. "GPS products used" lists IGS Final
(COD); post-fit RMS 2.456 m; the orbit is rendered on the globe and map.*

### 5.5 Comparing GPS product tiers

A one-click **"Compare product tiers"** runs the same case with Broadcast, Ultra-rapid, Rapid and
Final products and overlays each one's **error against the POEORB truth** on a single chart — so
you can see directly how much the product choice matters.

![Product-tier comparison: error versus POEORB truth for each tier.](report-img/08-tier-comparison.png)

*Figure 7 — The four tiers overlaid. See [§6](#6-results-and-validation) for what this reveals.*

### 5.6 Offline help with retrieval-augmented answers (RAG)

The Help tab answers plain-language questions **without the internet**. It uses **RAG**
(*Retrieval-Augmented Generation*): it first *retrieves* the most relevant passages from the
program's own documentation and settings notes (ranked by a classic text-search method called
*BM25*), then writes an answer *from those passages only*, citing each source — so it cannot
invent facts.

![The RAG help pipeline: retrieve from bundled docs, then answer with citations.](report-img/09-rag-help.png)

*Figure 8 — How the offline help works: your question → retrieve passages (BM25) → generate a
cited answer. An optional local model (Ollama) or a cloud model can phrase the answer from the
same retrieved passages; the retrieval step always stays offline.*

### 5.7 Diagnostics

Every run also produces engineering plots: altitude over time, GPS geometry and number of
satellites in view, the positioning residuals, the orbit-determination residuals, and — where a
truth orbit exists — the radial/along-track/cross-track error.

![Diagnostic plots for a run.](report-img/10-diagnostics.png)

*Figure 9 — Diagnostic plots. These let an analyst judge the quality of the fit and the forecast.*

---

## 6. Results and validation

**The two-month Sentinel-3A campaign.** The program ran one OD + forecast for each of the 61 daily
cut-offs from 3 September to 2 November 2024, with the frozen test settings (6-hour arc, 7-day
forecast, EGM96 70×70 gravity, NRLMSISE-00 drag).

| Measure | Value |
|---|---|
| Forecasts issued | **61 of 61** (all converged; none flagged) |
| Orbit-fit post-fit RMS | median **2.35 m** (range 1.85 – 4.38 m) |
| GPS position fixes per cut-off | 73 |
| Run time per cut-off | ~125 s (≈ 2 h for the whole window on one computer) |
| Error vs POEORB at the cut-off | 2.17 m (8 Oct), 0.48 m (16 Oct) |
| Error vs POEORB after 1 day | 268 m (7 Oct), 28.7 m (15 Oct) |

Only days whose Sentinel-3A reference orbit is in the bundled data have an error figure — the
reference is for scoring only.

**What the product-tier comparison revealed (Figure 7).** At a single cut-off the four tiers come
out very close (post-fit RMS: Broadcast 2.07 m, Ultra-rapid 2.56 m, Rapid 2.49 m, Final 2.45 m —
a few metres apart). On the error-versus-truth chart they almost coincide, because over a 7-day
forecast the orbit drifts ~20 km through **atmospheric-drag uncertainty**, which dwarfs the
metre-level differences between GPS products. This is a genuine, useful finding: for a multi-day
LEO *forecast*, the drag model and the forecast horizon dominate the error, not the GPS product.
The product choice matters most for the *instantaneous* orbit (at the cut-off), where the error is
sub-metre to a few metres.

**Unit and reference validation.** Beyond the campaign, 120+ automated tests check the maths:
loss-free orbit conversions, energy conservation, the J2 nodal-regression formula, the
leap-second table, NRLMSISE-00 against its official reference case to 7 significant figures,
batch-OD recovery of a known state and drag coefficient, and CCSDS OEM round-trips. The propagator
is also checked directly against real Sentinel-3A precise orbits.

---

## 7. Problems met and solved

Real engineering is finding and fixing problems; two worth recording:

- **Wrong-satellite reference orbit.** One repository file named `S3A_POEORB_…` actually contained
  **Sentinel-3B** data (its own header said so). The program had compared Sentinel-3A forecasts
  against it, producing a nonsensical ~13,000 km "error" for two days. Fix: the catalogue now reads
  which satellite a reference orbit is for *from the file's header* and only compares against the
  matching satellite. The forecasts themselves were always correct.

- **Rejected precise clocks.** The main precise products are from **CODE**, and their high-rate
  clock files place one character of the file header one column further right than the standard.
  The reader was rejecting all 69 CODE Final clocks, which would have stopped the Final orbits from
  being usable. Fix: accept the file-type character in either column. Reconstruction runs now use
  IGS Final products end to end (Figure 6).

Both came with a test so they cannot silently return.

---

## 8. Limitations and honesty

- **GPS products in real time.** A genuine *forecast* at the cut-off can only use what exists then —
  broadcast navigation and the predicted half of ultra-rapid. The more accurate Final/Rapid
  products appear ~1–2 weeks later and are used in *Reconstruction* mode, for the best retrospective
  orbit.
- **The 7-day drift.** Forecast error grows mainly through atmospheric drag, which depends on
  unpredictable solar activity; ~20 km after 7 days is expected for this altitude without updated
  space-weather input.
- **Rendering speed.** The screenshots and video in this project were produced on a server with no
  graphics card, so the 3-D satellite is enlarged for visibility and some captures are time-lapsed;
  on a normal computer the globe animates smoothly.
- **Licensing.** The NRLMSIS 2.0 and DTM2020 atmosphere models are not included for licensing
  reasons; the shipped drag options are exponential, Harris-Priester, NRLMSISE-00 and JB2008.

---

## 9. How to run it

Full install and run instructions for **Linux and Windows** (from installing Java and Maven
through building and launching) are in the project **[README](../README.md)**. In short:

```bash
mvn clean package                               # build (or add -DskipTests to go faster)
java -jar target/leo-trajectory-0.1.0.jar gui   # start the browser interface
```

Then open the printed `http://127.0.0.1:…` address, press **▶ Run** (or **⚖ Compare product
tiers**) on the Run tab, and inspect the Results and Window tabs.

---

## Glossary

Terms are listed roughly from general to specific.

**LEO (Low Earth Orbit)** — an orbit a few hundred km above Earth (Sentinel-3A: ~815 km). Satellites
here circle the Earth about every 100 minutes.

**Orbit** — the path a satellite follows around the Earth. Described either by a position+velocity
vector or by six **orbital elements** (see below).

**Orbital elements (Keplerian)** — six numbers that describe an orbit: *semi-major axis* (size),
*eccentricity* (how oval), *inclination* (tilt to the equator), *RAAN* (orientation of the orbit
plane), *argument of perigee* (orientation within the plane), and *true anomaly* (position along
the orbit).

**Ephemeris** — a table of a satellite's position (and velocity) over time.

**GNSS (Global Navigation Satellite System)** — any satellite-positioning system. **GPS** is the
American GNSS. Others: Galileo (EU), GLONASS (Russia), BeiDou (China).

**GPS receiver** — a device that listens to GNSS signals and computes its position. LEO satellites
carry one; it records the measurements this project uses.

**GNSSRD** — the Sentinel-3 file type holding the satellite's **raw GPS observations** (measured
distances to each GPS satellite, per epoch). The mandatory measurement input.

**RINEX** — Receiver Independent Exchange format: the standard text format for GNSS observations and
navigation messages. The program reads RINEX 3 and 4.

**Epoch** — a specific instant in time at which a measurement or state is given.

**Pseudorange** — the distance a receiver measures to a GPS satellite from signal travel time (called
"pseudo" because the receiver's clock error is still mixed in and must be solved for).

**SPP (Single-Point Positioning)** — computing the receiver's 3-D position (and clock error) from
the pseudoranges at one epoch.

**Fix** — one computed position from SPP at one epoch.

**Orbit Determination (OD)** — reconstructing the true orbit from measurements.

**Batch least-squares** — the OD method used here: adjust the orbit's starting state until the
simulated orbit best fits *all* the position fixes at once, minimising the sum of squared errors.

**Post-fit RMS** — the root-mean-square mismatch (in metres) between the fitted orbit and the fixes
after OD; a measure of fit quality.

**Residual** — the leftover mismatch between a measurement and the model, for one epoch.

**Propagation / Trajectory prediction** — simulating the orbit forward in time by integrating the
forces acting on the satellite.

**Numerical integration** — solving the equations of motion step by step. Here by the
**Dormand-Prince 5(4)** adaptive method (it automatically chooses step sizes to control error).

**Force model / perturbation** — any physical effect that changes the orbit: gravity, drag, solar
radiation pressure, Sun/Moon pull, tides, relativity. Each is switchable.

**Spherical-harmonic gravity** — a mathematical description of the Earth's non-uniform gravity using
coefficients up to a chosen **degree and order** (here EGM96 to 70×70). Higher degree = finer detail
= slower.

**Atmospheric drag** — air resistance that slowly lowers a LEO orbit. Modelled by an **atmosphere**
(exponential, Harris-Priester, or the solar-activity-driven **NRLMSISE-00** / **JB2008**).

**Space weather (F10.7, Ap)** — solar and geomagnetic activity indices that drive the upper
atmosphere's density, hence drag. *F10.7* is solar radio flux; *Ap* is a geomagnetic index.

**SRP (Solar Radiation Pressure)** — the tiny push of sunlight on the satellite, including Earth's
shadow (eclipse).

**Third body** — the gravity of the Sun or Moon perturbing the orbit.

**Tides (solid / ocean)** — the Earth's shape flexes under Sun/Moon gravity, slightly changing its
gravity field.

**Covariance** — a mathematical statement of uncertainty: how confident we are in the predicted
position, and how the errors in different directions relate. Reported in the OEM output.

**RTN / radial–along-track–cross-track** — a satellite-centred coordinate frame used to describe
errors: *radial* (up/down), *along-track* (forward/back, where drag error shows up), *cross-track*
(sideways).

**Reference frame** — the coordinate system a position is given in. **GCRF / EME2000 (J2000)** is
inertial (fixed relative to the stars); **ITRF** rotates with the Earth. Converting between them
needs Earth-orientation data.

**EOP (Earth Orientation Parameters)** — small, measured corrections for the Earth's irregular
rotation and pole wobble (e.g. the IERS `finals2000A` file), needed for the most accurate frame
conversion.

**Cut-off (UTC cut-off)** — the instant separating measurement from forecast: OD uses the arc ending
here; the forecast starts here.

**Forecast span** — how far ahead the orbit is predicted (default 7 days; 14/21/30 are one click).

**IGS (International GNSS Service)** — a global collaboration that publishes precise GPS orbit and
clock products. **CODE, ESA, EMR, GFZ** are analysis centres within it.

**GPS products / tiers** — the GPS satellites' positions and clocks, in increasing accuracy and
delay: **Broadcast** (real-time, ~1–2 m) → **Ultra-rapid** (hours, ~5 cm) → **Rapid** (~1 day,
~2.5 cm) → **Final** (~1–2 weeks, ~2 cm).

**SP3 / CLK** — the file formats for precise GPS **orbit** (SP3) and **clock** (CLK) products.

**Broadcast ephemeris** — the lower-accuracy GPS orbit/clock transmitted by the GPS satellites
themselves, available in real time.

**Forecast mode vs Reconstruction mode** — *Forecast* (cut-off-safe) uses only products that existed
at the cut-off, as a real operation would; *Reconstruction* (retrospective) uses the best products
present, for the most accurate orbit after the fact.

**POEORB (Precise Orbit Ephemeris)** — ESA's very accurate, after-the-fact reconstructed orbit for
Sentinel-3, used here **only to score** our result, never as an input.

**PROQUA** — the Sentinel-3 file type holding the satellite's measured **attitude** (which way it is
pointing), used only if box-wing modelling is selected.

**CCSDS OEM (Orbit Ephemeris Message)** — the international standard text format for an ephemeris;
every result is written in it. **KML/CZML** are formats for viewing tracks in Google Earth / Cesium.

**SHA-256 checksum** — a short fingerprint of a file's contents, used to record exactly which input
produced a result (reproducibility).

**Air-gapped / offline** — a computer with no internet connection; the whole program runs locally.

**RAG (Retrieval-Augmented Generation)** — answering a question by first *retrieving* relevant
documentation passages, then *generating* an answer from those passages only, with citations — so it
cannot invent facts. **BM25** is the classic text-ranking method used to retrieve the passages.

**Manoeuvre flag** — a warning raised when the orbit-fit residuals jump, which can indicate the
satellite fired a thruster (a manoeuvre) during the arc.

---

*Project built as a reference-quality, extensible foundation for LEO orbit determination and
forecasting from a satellite's own GPS data. Test satellite: ESA Sentinel-3A.*
