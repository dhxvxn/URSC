<div class="keyfacts">
<span>Project <b>GNSS Based OD &amp; Trajectory Prediction</b> (formerly URSC)</span>
<span>Test satellite <b>Sentinel-3A</b></span>
<span>Two assistants: <b>Codex</b> + <b>Claude</b></span>
</div>

> **Purpose.** This report is the consolidated, polished **prompt history** that drove the project —
> both the **Codex** phase (engine, orbit determination, propagation, data acquisition) and the
> **Claude** phase (the offline web application, visualisation, documentation and media). The prompts
> are rewritten to be **self-contained and reproducible**: given to a capable AI coding assistant,
> working against the project repository, the ordered sequence below rebuilds the project. Each prompt
> is shown in a copy-paste block.
>
> **Honesty note (carried over from the original Codex report).** This is an *editorial*
> reconstruction, not a verbatim keystroke log. Real development was iterative; the prompts here are
> the cleaned-up, sufficient instructions, with the original intent preserved. Codex-phase items cite
> their source IDs (CX-###) from the supplied history; Claude-phase items are polished from the actual
> session requests. Nothing is invented to fill gaps.

---

# 1. How to use this playbook

Give the prompts to an AI coding assistant **in order**, one at a time, in a workspace that contains
(or will clone) the project repository. Confirm each step builds and the tests pass before moving on.
Phase A establishes the numerical engine and the data; Phase B turns it into the offline application,
the visualisation, and the documentation. Section 5 gives a single consolidated prompt for anyone who
prefers one comprehensive brief over the step sequence.

Throughout, the assistant should: keep the physics modular (every force a switch), keep the system
dependency-free and offline-capable, validate against ESA POEORB **for scoring only**, and never
present isolated results as general performance.

---

# 2. Standing constraints

These held for the whole project and should be stated to the assistant up front.

```text
Standing constraints for every task on this project:
- Target accuracy: ~10 m at +1 day and < 1 km at +7 days for the Sentinel-3A forecast.
- Forecasts must be cut-off-safe: at the chosen UTC cut-off use only data that existed then
  (broadcast navigation and the predicted half of ultra-rapid). Never treat post-cut-off
  observations or late products as if they were available in real time.
- GPS product priority is Final > Rapid > Ultra-rapid > Broadcast, chosen per satellite and
  per epoch, with automatic fall-back.
- Keep the physics modular: every force model and every parameter is a configuration switch,
  not hard-coded.
- Validate against ESA POEORB precise orbits for scoring only; POEORB is never an input.
- Report honestly: show all dates including failures and worsening cases; a run converging
  does not prove future accuracy; do not generalise one date's result.
- Keep the system dependency-free and able to run fully offline (air-gapped). Do not add a
  required internet service or a generative-model dependency.
- Licensing: do not embed NRLMSIS 2.0 or DTM2020 code or coefficient files. Ship exponential,
  Harris-Priester, NRLMSISE-00 and JB2008 as the drag options.
- Keep credentials and secrets out of all committed files and reports.
- Distinguish, at all times, what was supplied, what is currently accessible, what was used
  only in a test, and what was actually committed and pushed.
```

---

# 3. Phase A — Codex

*Engine, orbit determination, propagation, and data. Polished from the 13 Codex workstreams
(CX-001…CX-111). Each prompt is a consolidated, actionable brief for its workstream.*

## A1 · Environment, repository and documentation  *(CX-001, 002, 003, 009, 010)*
```text
Set up the cloud development environment for the GitHub repository dhxvxn/URSC. Review the
project handoff notes and the existing code. Then prepare accurate documentation: installation,
dependencies, build, start-up and usage, verified against an actual build.
```

## A2 · Physical model and accuracy objectives  *(CX-004–008, 011, 012, 020)*
```text
Using the supplied Sentinel-3A spacecraft properties (mass ~1130 kg, drag and SRP areas ~8 m^2,
Cd 2.2, Cr 1.3), analyse the force model and recommend the highest-impact accuracy improvements.
Hold to the accuracy objective of ~10 m at one day and < 1 km at seven days. Use forecast-time
(cut-off-safe) space-weather assumptions for the prediction window, never future observations.
```

## A3 · Propagation, orbit determination and validation  *(CX-013–019, 021)*
```text
Explain and implement the numerical propagation and the batch orbit determination. Document the
test states and the frame conventions (ITRF input states, GCRF propagation). Validate against
Sentinel-3A POEORB precise orbits and review the multi-date prediction results against the
accuracy objective. Report the configurations used; do not treat an isolated good result as
general performance.
```

## A4 · Architecture and offline project assistance  *(CX-024–028)*
```text
Implement the requested subset of the agreed hand-drawn architecture, excluding the
filter/monitoring/manoeuvre services for now. Add an offline project-question assistant that
answers from the project's own documentation and requires no generative-model dependency.
```

## A5 · Input acquisition, RINEX compatibility and coverage  *(CX-022, 023, 029–041)*
```text
Identify exactly which inputs are required to run. Archive the supplied broadcast navigation and
GNSS observations with checksums. Add RINEX 4.00-4.02 GPS navigation support (including gzip).
Audit the available coverage and select the two-month window with the greatest input availability;
produce readable missing-date checklists.
```

## A6 · Architecture clarification and consolidation  *(CX-042–054)*
```text
Document the agreed estimation/prediction architecture precisely: GNSS satellite position/clock
computation, single-point-positioning fixes, covariance-weighted batch orbit determination, and
the forecast cut-off. In the architecture diagram, merge the three blocks for OD, forecast and
output into one; keep deferred implementation work clearly separate from the diagram update.
```

## A7 · Observation, orbit and attitude input campaigns  *(CX-055–063)*
```text
Archive the incoming GNSSRD observations, POEORB reference orbits and PROQUA attitude files using
their exact product naming conventions. Check for missing dates. Before relying on anything as a
backup, verify what is actually present in the GitHub repository.
```

## A8 · Forecast analytics and runtime product selection  *(CX-064–069)*
```text
Run the agreed OD + forecast workflow across the window and present the forecast errors in tables.
At runtime, accept user-selected input folders, and select the GPS product as Final > Rapid >
Ultra-rapid > Broadcast, applying the specified cut-off and observation-arc settings.
```

## A9 · Precise products and automated downloads  *(CX-070–083)*
```text
Identify the precise-product file formats (SP3, CLK) and their naming conventions and uses.
Automate authenticated acquisition of precise products from CDDIS with a Python downloader; keep
all credential values out of the code and reports, and provide browser-based configuration guidance.
```

## A10 · Matched comparison and manoeuvre investigation  *(CX-084–101)*
```text
Compare Broadcast, Ultra-rapid, Rapid and Final products over the full 61-day experiment with
matched settings. Separately investigate manoeuvre effects, explain retrospective reconstruction
versus real-time forecasting, and seek independent manoeuvre information for cross-verification.
```

## A11 · Estimator improvements, convergence and reporting  *(CX-090–106)*
```text
Improve the one-day and seven-day predictions and the orbit-determination reliability. Investigate
the dates whose results worsened. Report before/after results for all dates with explicit
limitations, and make clear that a converged solution does not guarantee future accuracy.
```

## A12 · GitHub backup and input inventory  *(CX-107–110)*
```text
Audit and publish all available input categories, including Final, Rapid and Ultra-rapid products.
Maintain category tables, checksums and aliases, and explicitly distinguish usable original files
from incomplete downloads and from data that is simply unavailable.
```

## A13 · Prompt-history documentation and hand-off  *(CX-111)*
```text
Polish the visible prompt history into an academic-submission report, preserving provenance, and
prepare an editable structure into which the second assistant's prompt history can be merged.
```

---

# 4. Phase B — Claude

*The offline web application, the visualisation, the data integration, the documentation and the
media. Polished from the actual session requests. Give these after Phase A, against the same repo.*

## B1 · The offline local web application
```text
Build an offline local web GUI over the existing OD + forecast engine. Use the JDK's built-in HTTP
server bound to 127.0.0.1 only, with zero third-party runtime dependencies. Vendor three.js and
Natural Earth country borders for an offline 3-D globe. Every trajectory output is CCSDS OEM 2.0.
Screens: Data (catalogue input files by content, with coverage and SHA-256, watched folders);
Run setup (every parameter, each with an info note, grouped sensibly); Results (3-D globe, 2-D
ground track, diagnostic plots, OEM/KML/CZML download); History; and an offline Help search.
Drive the forms and validation and the info notes from a single parameter-registry file. Stream
run progress to the browser with server-sent events. Assert in a test that a full run makes zero
requests outside 127.0.0.1.
```

## B2 · Tested-window defaults
```text
Make the tested two-month Sentinel-3A window the start-up defaults, loaded when the app opens and
fully editable: cut-off 2024-09-03 05:59:42 UTC (= 06:00 GPS), 6-hour arc, 300 s sampling,
pseudorange sigma 5 m, 12 iterations, 7-day forecast, 60 s output step, spacecraft 1130 kg /
8 m^2 / Cd 2.2 / Cr 1.3, EGM96 70x70, NRLMSISE-00 drag, Dormand-Prince 1e-13 rel / 1e-10 abs /
30 s max step, covariance off. Add a Reset button that restores these defaults and a Generic
button that loads the plain library defaults.
```

## B3 · Window (batch) runs
```text
Add a Window tab that repeats orbit determination and a 7-day forecast for every day in a date
range (up to ~400 cut-offs), selecting each day's inputs from the catalogue. Show a progress bar,
summary tiles (cut-offs, forecasts issued, OD-not-usable, no-data, flagged, median post-fit RMS),
trend charts (post-fit RMS, SPP fixes, error vs POEORB where a reference exists), and a per-day
table whose rows open that day in Results. Update live from server-sent events.
```

## B4 · 3-D satellite builds and follow camera
```text
Add a recognisable Sentinel-3A 3-D build (bus, single solar wing, instruments) and a clean generic
build for any other satellite, selected automatically by object name. Add a follow camera that
rides nadir-pointing along the predicted velocity, a full-view presentation mode, a starfield, and
dynamic near/far planes.
```

## B5 · Interactive feature guide and demonstration video
```text
Prepare an interactive guide that shows every feature with, for each, what it does, why it is
needed, and how to use it, plus a demo walkthrough and a defaults table; make it viewable as a
page. Produce a live-propagation video of the application running the two-month window, including
the Sentinel-3A build moving on the globe and the generic build for other satellites.
```

## B6 · IGS Final-product preference (verify / fix)
```text
Ensure the product resolver actually prefers IGS Final when it is present, following
Final > Rapid > Ultra-rapid > Broadcast per satellite and per epoch, and that suggested inputs
include precise SP3/CLK products from the catalogue. Add a test.
```

## B7 · CCSDS OEM output for the whole window
```text
Produce the full 61-day window as one CCSDS OEM 2.0 file per cut-off (7-day forecast each), with
provenance comments, metadata, 60 s steps and formal covariance, packaged for cross-checking.
```

## B8 · Rename (display only)
```text
Rename the user-facing product name to "GNSS Based Orbit Determination and Trajectory Prediction"
everywhere a user sees it (app title and header, CLI banner, OEM ORIGINATOR, guide, help, the
printable report, the build's display name). Keep the internal Java package, the session-token
header and the jar filename unchanged.
```

## B9 · Highly interactive, balanced visualisation
```text
Make the visualisation highly interactive and polished without relying solely on 3-D. Keep the
3-D globe, but make the 2-D ground-track map interactive: hovering it scrubs time across the globe,
map and plots together; clicking pins a moment; add togglable layers (full track, reference orbit,
day/night with a terminator, stations, grid), hover read-outs (lat/lon/alt, error vs reference),
and a legend.
```

## B10 · Catalogue new inputs, run, and surface essentials
```text
Catalogue all newly added input files, run the model on them and render the output, and state which
inputs are essential for a run versus optional. While doing so, fix any reader or catalogue defects
the new data exposes (for example, a reference orbit whose header names a different spacecraft than
its filename, or precise clock files whose header column is shifted). Add regression tests.
```

## B11 · Auto-fill cut-off and forecast-length presets
```text
Make the UTC cut-off fill automatically from the end of the latest observation arc (editable, still
validated), with a "latest from data" action; and add one-click 7 / 14 / 21 / 30-day presets to the
forecast length, keeping a free custom value and a default of 7.
```

## B12 · Product-tier error comparison
```text
Add a one-click "compare product tiers" action that runs the same case with Broadcast, Ultra-rapid,
Rapid and Final (in reconstruction mode so every tier present can run; skip tiers with no data) and
overlays each tier's error against the POEORB truth orbit on one chart.
```

## B13 · Portable build (Windows)
```text
Make the full build pass on Windows as well as Linux. The catalogue tests stage files with
symbolic links, which Windows refuses without a privilege; fall back to copying the file when a
link cannot be created.
```

## B14 · README and illustrated report
```text
Restructure the README to open with a short plain-language description, then a complete
install-and-run guide for Linux and Windows (from installing Java and Maven through build and
launch). Write an illustrated, student-readable report (problem to results) with screenshots and a
glossary that defines every term, so a reader with no physics background can follow it.
```

## B15 · Formalise the report
```text
Make the report formal and colour-coded, with an index at the start. Add a software-architecture
section (layers, components, data-flow diagram), a section analysing how accuracy differs between
GPS products using the two-month data (tables of post-fit RMS and orbit difference per tier), and
an expanded "difficulties encountered and resolutions" section covering every problem and its fix.
```

## B16 · Re-capture media and presentation materials
```text
Re-capture every screenshot from the renamed build; render a demonstration video with a
scene-by-scene narration script; and write a 60-120 minute presentation script for a scientific
audience covering the data, the architecture and why each language is used, the physics with the
rationale for each model versus its alternatives, the mathematics, a live-demo sequence, the
results, why some runs did not converge and how it was fixed, every other problem faced,
limitations, and a prepared question-and-answer section.
```

---

# 5. The single master build prompt

*For anyone who prefers one comprehensive brief. Give this to a capable AI coding assistant with a
Java 17 toolchain and the input data; it captures the whole project.*

```text
Build a dependency-free Java 17 application that determines and forecasts the orbit of a low-Earth-
orbit satellite (test satellite: ESA Sentinel-3A) from the satellite's own GPS observations, and
runs fully offline.

ENGINE. Implement the pipeline: read GNSSRD GPS observations (RINEX 3 and 4, gzip); compute GPS
satellite positions/clocks from products with priority Final > Rapid > Ultra-rapid > Broadcast,
chosen per satellite and epoch; solve single-point positioning at each epoch; fit one orbit to all
fixes by covariance-weighted batch least squares (Gauss-Newton, finite-difference partials, so it
is force-model-agnostic); then propagate with a Dormand-Prince 5(4) adaptive integrator (Cowell)
under a switchable force model: spherical-harmonic gravity (EGM96, degree/order selectable, default
70), atmospheric drag (exponential / Harris-Priester / NRLMSISE-00 / JB2008; NRLMSISE-00 default;
do NOT embed NRLMSIS 2.0 or DTM2020), solar radiation pressure with conical eclipse, Sun and Moon
third bodies, solid and ocean tides, general relativity (Schwarzschild + Lense-Thirring), Earth
albedo/IR, and an optional empirical acceleration. Propagate the OD covariance to the output epochs
via a finite-difference state-transition matrix. Use full IAU-2006/2000B frames (GCRF<->ITRF) with
optional IERS EOP. Write every trajectory as CCSDS OEM 2.0 (with covariance); also export KML and
CZML. Forecasts must be cut-off-safe. Validate against ESA POEORB precise orbits for scoring only.

DATA CATALOGUE. Recognise each input file by its content (not its name), recording coverage,
satellite (from the file's own header), product class and a SHA-256 checksum; select the right
files for a given cut-off; quarantine unreadable/partial files.

APPLICATION. Serve a browser interface from the JDK's built-in HTTP server on 127.0.0.1 with zero
external requests. Screens: Data (catalogue + coverage timeline), Run (every parameter with an info
note, driven by one parameter-registry file; tested-window defaults; cut-off auto-filled from data;
7/14/21/30-day forecast presets), Window (one run per day over a range with trend charts), Results
(interactive 3-D globe with a Sentinel-3A build and a generic build and a follow camera; an
interactive 2-D ground-track map where hovering scrubs time across globe/map/plots, with togglable
layers; diagnostic plots; OEM/KML/CZML/report downloads), a product-tier comparison overlaying each
tier's error vs POEORB, History, and an offline retrieval-augmented Help search. Vendor three.js and
country borders.

VALIDATION. Run the two-month Sentinel-3A campaign (2 Sep - 2 Nov 2024, 61 daily cut-offs, 6-hour
arc, 7-day forecast) and report, honestly, median post-fit RMS and error vs POEORB per date,
including failures. Cover everything with unit tests (including NRLMSISE-00 against its reference
case) and keep the build green on Linux and Windows.

DELIVERABLES. A rename-ready display name "GNSS Based Orbit Determination and Trajectory
Prediction"; a README with Linux/Windows install from Maven to run; an illustrated report with a
glossary; and a self-contained offline packaging path (jlink/jpackage). Keep credentials out of the
repository.
```

---

# 6. Provenance map

| Phase | Assistant | Source | Items |
|---|---|---|---|
| A1–A13 | Codex | supplied prompt history (`PROMPT_DEVELOPMENT_REPORT`) | CX-001 … CX-111, grouped into 13 workstreams |
| B1–B16 | Claude | this project session | the application, data integration, visualisation, documentation and media requests |
| Master | — | consolidation of both phases | one comprehensive brief |

**Notes.** The Codex-phase briefs are editorial consolidations of their cited source IDs, not
single verbatim prompts. The Claude-phase prompts are polished from the actual session requests and
preserve their intent. Both phases obey the standing constraints in §2. The project's internal name
was URSC during Phase A and is presented to users as *GNSS Based Orbit Determination and Trajectory
Prediction* after the Phase B rename (B8); the Java package and repository name remain `ursc` /
`URSC`.

*Related evidence: the written report (`docs/REPORT.md`), the presentation script
(`docs/PRESENTATION.md`), and the input inventory (`data/README.md`).*
