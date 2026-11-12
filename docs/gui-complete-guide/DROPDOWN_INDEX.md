# Dropdown and choice checklist

Use the full guide for the meaning, defaults, visibility conditions and consequences of each choice. Dynamic lists contain workspace/catalog/OS entries rather than additional built-in model choices.

## Parameter dropdowns and workflow toggle

### Workflow — `workflow`

- OD + Forecast (`OD_FORECAST`)
- Propagate from a state (`PROPAGATE`)

### Product mode — `products.mode`

- Forecast (cutoff-safe) (`FORECAST`)
- Reconstruction (retrospective) (`RECONSTRUCTION`)

### Issue-time evidence — `products.eligibility`

- Nominal latency (assumed) (`NOMINAL_LATENCY`)
- Verified issue times only (`REQUIRE_VERIFIED`)

### Force product tier — `products.tier`

- Automatic (priority order) (`AUTO`)
- IGS Final only (`FINAL`)
- IGS Rapid only (`RAPID`)
- IGS Ultra-rapid only (`ULTRA_RAPID`)
- Broadcast only (`BROADCAST`)

### OEM reference frame — `forecast.frame`

- EME2000 (inertial, J2000) (`EME2000`)
- GCRF (inertial, ICRF-aligned) (`GCRF`)
- ITRF (Earth-fixed) (`ITRF`)

### Shape model — `spacecraft.shape`

- Cannonball (generic) (`CANNONBALL`)
- Box-wing (attitude-dependent) (`BOX_WING`)

### Attitude source — `spacecraft.attitude`

- Nominal yaw steering (`NOMINAL_YAW_STEERING`)
- Measured (AUX_PROQUA) (`MEASURED_PROQUA`)

### Density model — `forces.dragModel`

- NRLMSISE-00 (`NRLMSISE00`)
- JB2008 (`JB2008`)
- Harris-Priester (`HARRIS_PRIESTER`)
- Exponential (`EXPONENTIAL`)

### Sun/Moon ephemeris — `forces.thirdBodyEphemeris`

- Analytic series (`ANALYTIC`)
- JPL DE440 file (`DE440`)

### Weather after cutoff — `weather.afterCutoff`

- Persist (cutoff-safe) (`PERSIST`)
- Use file (retrospective) (`USE_FILE`)

### EOP after cutoff — `eop.afterCutoff`

- Persist (cutoff-safe) (`PERSIST`)
- Use file (retrospective) (`USE_FILE`)

### State source — `initialState.source`

- Typed (`TYPED`)
- From an OEM file (`OEM_FILE`)
- From a POEORB file (`POEORB_FILE`)

### Frame of typed state — `initialState.frame`

- ITRF (`ITRF`)
- GCRF (`GCRF`)
- EME2000 (`EME2000`)

## Other selectors and choice sets

### Global navigation → Tab buttons

Select Saved presentation, Data, Run, Results, Window, History or Help. Changing tabs does not start an orbit computation.

- Saved presentation
- Data
- Run
- Results
- Window
- History
- Help

### Saved presentation → Experiment

Select a stored campaign profile; filters the archived table without running anything.

- Improved Broadcast — nominal
- Original four-product comparison
- Retrospective — original candidate maneuvers
- Retrospective — corrected candidate maneuvers

### Saved presentation → Product tier

Restrict the selected archived profile by GPS product tier. A filter with no cases gives an empty-state message.

- all
- broadcast
- ultra-rapid
- rapid
- final

### Data → Kind filter

Filter catalog rows by recognized type; Coverage canvas and total count still describe the catalog.

- All kinds
- GNSS observations (GNSSRD / RINEX obs)
- GPS broadcast navigation
- IGS precise orbit (SP3)
- IGS precise clock (RINEX CLK)
- Precise orbit ephemeris (POEORB, reference only)
- Measured attitude (AUX_PROQUA)
- Earth orientation parameters
- Space weather (CSSI)
- JPL planetary ephemeris (DE4xx)
- Gravity field (ICGEM .gfc)
- CCSDS orbit ephemeris message (OEM)
- Unrecognised

### Shared file picker → Root / Home

Navigate the filesystem roots returned by the local server or its Home directory. Options vary by OS: Windows drives or Linux root, plus Home.

### Run toolbar and actions → Workflow

OD + Forecast fits pre-cutoff GNSS position fixes and predicts forward. Propagate from a state starts from a typed/file state and skips GNSS orbit fitting.

- OD + Forecast
- Propagate from a state

### Run toolbar and actions → Presets

Lists saved user preset names plus the empty placeholder. Presets depend on the current workspace and are not fixed model options.

### Run toolbar and actions → Spacecraft selector

Select among spacecraft with observations for the current cutoff. Appears only if more than one spacecraft is available; reruns catalog suggestion, not orbit determination.

### Shared setting controls → Span presets

Set forecast.spanDays to 7, 14, 21 or 30 days immediately. They change the form, not an existing saved output.

- 7
- 14
- 21
- 30

### Shared setting controls → Add from catalog…

Only lists catalog entries of the kinds allowed by that field. Options vary with the catalog and its file names. Select to add or replace the input. Multi-file values are deduplicated.

### Sweep dialog → Setting to vary

Includes every enum, boolean, integer and number parameter except workflow, even if a field is conditional in the current form. The complete candidates are indexed in the parameter appendix. Avoid choices incompatible with the selected workflow.

### Results → Run picker

Select a saved local History run. These are workspace runs, separate from the bundled Saved presentation archive. Opening a saved run does not recompute its OD or trajectory.

### Results → Earth-fixed / Inertial

Change the globe rendering frame; do not confuse a visualization toggle with the configured OEM output frame.

- Earth-fixed
- Inertial

### Results → Spacecraft model

Automatic chooses a procedural drawing from object name; explicit models override the drawing only. It does not alter force-model geometry or physics.

- Model: automatic
- Model: Sentinel-3 build
- Model: Generic satellite
- Model: dot only

### Results → Map layers

Toggle Full track, Reference, Day / night, Stations and Grid independently. Layer changes only affect drawing. Reference is POEORB display data, not a correction to prediction.

- Full track
- Reference
- Day / night
- Stations
- Grid

### Results → Playback speed

Choose simulated-time speed relative to wall time. It affects animation only.

- 60×
- 300×
- 600×
- 1800×
- 3600×

### Window → Window picker

Choose a locally saved window campaign; shows its name, date interval and completion count. The bundled two-month archive is in Saved presentation, not automatically this local list.

### Help → Guides

Load the corresponding bundled help page.

- Getting started
- Input data
- GPS product priority
- Perturbations & models
- Outputs (OEM, covariance)
- Offline operation & AI
- Troubleshooting

### Interactive Guide page → Walkthrough steps

Choose any of the ten walkthrough panels.

- Start the app
- The data catalog
- Run setup with tested defaults
- Explain any setting with ⓘ
- Perturbation switches
- One forecast on the globe
- Ride along with Sentinel-3A
- The two-month window
- Compare modelling choices
- Outputs and help

## Repeated file inputs

Every file/files setting has an Add from catalog selector restricted to that field’s accepted catalog kinds. The complete field list and accepted kinds are documented under each parameter; filenames are dynamic. Single-file selection replaces the old file; multi-file selection adds/deduplicates paths.
