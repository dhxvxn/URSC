# URSC Interactive Research Board

`index.html` is a **self-contained, interactive mind-map** of the whole URSC project —
LEO orbit determination and prediction, with Sentinel-3A as the test satellite.

It is a single HTML file with **zero external dependencies** (no libraries, no network
calls): just open it in any modern web browser.

## How to use it

- **Click a node** to expand its subtopics on the canvas (connected by lines).
- **Click again** (or click any node) to open a side panel with the full, plain-language
  explanation — what it is, why it matters, the mathematics, real file excerpts, and the
  design trade-offs.
- **Drag** to pan, **scroll** to zoom. Bottom-left controls: zoom in/out, **Fit**,
  **Expand all**, **Collapse**. `Esc` closes the panel. On a phone the panel becomes a
  bottom sheet.
- Node colour encodes the category (legend, top-right).

## What it covers

Ten top-level branches, each drilling down to detailed leaves:

1. **Mission & test satellite** — Sentinel-3A orbit, the CPOD physical (box-wing) model, attitude.
2. **Inputs & data products** — POEORB (truth), GNSSRD (raw GPS obs), PROQUA attitude, space
   weather, EOP, gravity/DE440/tides — with real file excerpts.
3. **Reference frames & time** — GCRF↔ITRF, the five time scales, the dR/dt velocity fix.
4. **The propagator** — equation of motion, Cowell's method, the DP5(4) integrator.
5. **Perturbations** — gravity, drag, SRP, third-body, tides, relativity, albedo
   (each with its model, alternatives, and rationale).
6. **Density models** — NRLMSISE-00, JB2008, NRLMSIS 2.0, DTM2020, the shared drivers, and the
   measured comparison result.
7. **Processing strategy** — single-state discipline, forecast-only weather, per-day scoring,
   RTN error geometry, the maneuver wall.
8. **Orbit determination** — dynamic, kinematic, and reduced-dynamic OD.
9. **GNSS positioning / SPP** — observables, the measurement math, GPS satellite position, the
   built pipeline, and the current blocker.
10. **Status & roadmap** — what is done, what remains, and the key findings.

The content is a teaching/explanation aid; the authoritative numbers live in the study reports
under `docs/reports/`.
