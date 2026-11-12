# Complete GUI screenshot and control guide

Version audited: 4c57944. Screenshots captured in Chromium on Linux; Windows native file/dialog appearance can differ. The application and screenshots run locally.

## Coverage and how to use this guide

This manual covers all seven application tabs, the shared toolbar, file browsers, conditionally visible fields, sweeps, comparison panels, station controls, and the separate interactive Guide page. It documents **85 parameter definitions** and **124 other controls/interactions**. Each fixed dropdown value is listed, including choices not currently selected. User preset names, catalog file names, filesystem roots and local run/window lists are dynamic rather than finite built-in choices.

Every parameter key was observed in a rendered conditional configuration, except `workflow`, which is represented by the top two-button toggle and is documented explicitly. The audit records source hashes, live control lists and screenshot captions; the coverage check requires no missing parameter keys.

**Data provenance:** Saved presentation screenshots show the actual archived two-month campaign. Populated Results, History and comparison screenshots use a separately created **SIMULATED** documentation dataset (SIMSAT, 15 January 2025), with short computed examples. The Data captures include clearly named simulated files and, in a supplemental capture, actual archived Sentinel inputs. The Window screenshot uses a clearly labelled one-date documentation record pointing to that simulated run, not a newly executed window campaign. These examples reveal controls and are not Sentinel-3A performance evidence. The original two-month campaign was not rerun.

**Presentation shortcut:** open `gui --presentation`, stay on Saved presentation, select an experiment/date and explain the recorded results. Run, Run whole window, Start sweep, Compare product tiers and Create demo dataset start new work. Viewing saved data and downloading stored OEMs do not run orbit determination or propagation.

## Suggested presentation order

1. Saved presentation: show the real 61-date campaign and an issued/rejected case.
2. Data: explain input categories, coverage and provenance.
3. Run: explain workflows, cutoff, product eligibility and the collapsed settings groups.
4. Results: explain an already saved local example, the view controls and formal uncertainty.
5. Window and History: distinguish local records from the bundled archive.
6. Help and Guide: show offline search and parameter explanations.
7. End with OEM downloads and their units/frame/time conventions.

## Screenshot atlas

### 01-saved-presentation

Saved presentation: real archived two-month results; no new computation.

![Saved presentation: real archived two-month results; no new computation.](screenshots/01-saved-presentation.png)

### 02-saved-case-details

Saved case metadata and eight daily benchmark state samples.

![Saved case metadata and eight daily benchmark state samples.](screenshots/02-saved-case-details.png)

### 03-data

Data catalog in the isolated documentation workspace. SIM_DEMO files are simulated.

![Data catalog in the isolated documentation workspace. SIM_DEMO files are simulated.](screenshots/03-data.png)

### 04-file-picker-multi

Shared file/folder browser: multi-selection mode.

![Shared file/folder browser: multi-selection mode.](screenshots/04-file-picker-multi.png)

### 05-file-picker-folder

Folder-selection mode also provides Use this folder.

![Folder-selection mode also provides Use this folder.](screenshots/05-file-picker-folder.png)

### 06-run-toolbar

Run toolbar, workflows, presets and initial groups. Missing files in a fresh setup are validation blockers, not new computations.

![Run toolbar, workflows, presets and initial groups. Missing files in a fresh setup are validation blockers, not new computations.](screenshots/06-run-toolbar.png)

### 07-00-mandatory-inputs

Run → Mandatory inputs: all currently visible controls.

![Run → Mandatory inputs: all currently visible controls.](screenshots/07-00-mandatory-inputs.png)

### 07-01-test-window

Run → Test window: all currently visible controls.

![Run → Test window: all currently visible controls.](screenshots/07-01-test-window.png)

### 07-03-gps-products-and-mode

Run → GPS products & mode: all currently visible controls.

![Run → GPS products & mode: all currently visible controls.](screenshots/07-03-gps-products-and-mode.png)

### 07-04-orbit-determination

Run → Orbit determination: all currently visible controls.

![Run → Orbit determination: all currently visible controls.](screenshots/07-04-orbit-determination.png)

### 07-05-forecast-and-output

Run → Forecast & output: all currently visible controls.

![Run → Forecast & output: all currently visible controls.](screenshots/07-05-forecast-and-output.png)

### 07-06-spacecraft

Run → Spacecraft: all currently visible controls.

![Run → Spacecraft: all currently visible controls.](screenshots/07-06-spacecraft.png)

### 07-07-perturbations

Run → Perturbations: all currently visible controls.

![Run → Perturbations: all currently visible controls.](screenshots/07-07-perturbations.png)

### 07-08-optional-data-and-environment

Run → Optional data & environment: all currently visible controls.

![Run → Optional data & environment: all currently visible controls.](screenshots/07-08-optional-data-and-environment.png)

### 07-09-integrator

Run → Integrator: all currently visible controls.

![Run → Integrator: all currently visible controls.](screenshots/07-09-integrator.png)

### 08-density-info

Density-model information popover: explanation, guidance, key and default.

![Density-model information popover: explanation, guidance, key and default.](screenshots/08-density-info.png)

### 09-box-wing

Box-wing conditional controls: attitude selector, face areas, array area and cant angle.

![Box-wing conditional controls: attitude selector, face areas, array area and cant angle.](screenshots/09-box-wing.png)

### 10-empirical

Empirical acceleration enabled: radial, transverse and normal fields become visible.

![Empirical acceleration enabled: radial, transverse and normal fields become visible.](screenshots/10-empirical.png)

### 11-propagate-typed

Propagate workflow with typed initial state: epoch, frame and six SI components.

![Propagate workflow with typed initial state: epoch, frame and six SI components.](screenshots/11-propagate-typed.png)

### 12-propagate-oem

State source OEM file: the typed vector fields are replaced by a file input.

![State source OEM file: the typed vector fields are replaced by a file input.](screenshots/12-propagate-oem.png)

### 13-file-picker-single

Single-file picker used for OEM, POEORB and optional file inputs.

![Single-file picker used for OEM, POEORB and optional file inputs.](screenshots/13-file-picker-single.png)

### 14-propagate-poeorb

State source POEORB file; blank epoch selects the first state, otherwise selection uses the requested epoch.

![State source POEORB file; blank epoch selects the first state, otherwise selection uses the requested epoch.](screenshots/14-propagate-poeorb.png)

### 15-sweep-enum

Sweep dialog for an enum: parameter selector and choice checkboxes.

![Sweep dialog for an enum: parameter selector and choice checkboxes.](screenshots/15-sweep-enum.png)

### 16-sweep-numeric

Sweep dialog for a numeric setting: comma-separated values.

![Sweep dialog for a numeric setting: comma-separated values.](screenshots/16-sweep-numeric.png)

### 17-sweep-boolean

Sweep dialog for a boolean: on/off checkboxes.

![Sweep dialog for a boolean: on/off checkboxes.](screenshots/17-sweep-boolean.png)

### 18-results-empty

Results before selecting a local saved run. Bundled archive cases are separate from local History.

![Results before selecting a local saved run. Bundled archive cases are separate from local History.](screenshots/18-results-empty.png)

### 19-results-loaded

Results for a short SIMULATED documentation run, not measured Sentinel-3A campaign accuracy.

![Results for a short SIMULATED documentation run, not measured Sentinel-3A campaign accuracy.](screenshots/19-results-loaded.png)

### 20-results-visualization

Live visualization controls, map layers, playback and covariance ellipsoid using simulated data.

![Live visualization controls, map layers, playback and covariance ellipsoid using simulated data.](screenshots/20-results-visualization.png)

### 21-results-follow

Sentinel-3 drawing explicitly selected for a SIMULATED trajectory: changes drawing only, not physics.

![Sentinel-3 drawing explicitly selected for a SIMULATED trajectory: changes drawing only, not physics.](screenshots/21-results-follow.png)

### 22-results-full-view

Full-view globe layout; Escape restores the normal page.

![Full-view globe layout; Escape restores the normal page.](screenshots/22-results-full-view.png)

### 23-stations-passes

Station editor and pass table for a SIMULATED trajectory.

![Station editor and pass table for a SIMULATED trajectory.](screenshots/23-stations-passes.png)

### 24-results-plots

Conditional result plots: altitude, formal covariance, reference error and positioning diagnostics for the simulated case.

![Conditional result plots: altitude, formal covariance, reference error and positioning diagnostics for the simulated case.](screenshots/24-results-plots.png)

### 25-spp-per-epoch

GNSS positioning detail table expanded; screenshot is a short visible excerpt.

![GNSS positioning detail table expanded; screenshot is a short visible excerpt.](screenshots/25-spp-per-epoch.png)

### 26-window

Window controls illustrated by a clearly labelled one-date documentation record referring to the simulated run; not a rerun of the two-month campaign.

![Window controls illustrated by a clearly labelled one-date documentation record referring to the simulated run; not a rerun of the two-month campaign.](screenshots/26-window.png)

### 27-history

Local history holding two short SIMULATED documentation runs; bundled two-month archive remains separate.

![Local history holding two short SIMULATED documentation runs; bundled two-month archive remains separate.](screenshots/27-history.png)

### 28-comparison

Comparison of two SIMULATED saved runs. Run-to-run differences are not real-world accuracy.

![Comparison of two SIMULATED saved runs. Run-to-run differences are not real-world accuracy.](screenshots/28-comparison.png)

### 29-help

Help tab with local documentation navigation and provider status.

![Help tab with local documentation navigation and provider status.](screenshots/29-help.png)

### 30-help-search

Offline help search with passages and provider attribution.

![Offline help search with passages and provider attribution.](screenshots/30-help-search.png)

### 31-help-input-data

Help guide: Input data

![Help guide: Input data](screenshots/31-help-input-data.png)

### 31-help-gps-product-priority

Help guide: GPS product priority

![Help guide: GPS product priority](screenshots/31-help-gps-product-priority.png)

### 31-help-perturbations-and-models

Help guide: Perturbations & models

![Help guide: Perturbations & models](screenshots/31-help-perturbations-and-models.png)

### 31-help-outputs-oem-covariance

Help guide: Outputs (OEM, covariance)

![Help guide: Outputs (OEM, covariance)](screenshots/31-help-outputs-oem-covariance.png)

### 31-help-offline-operation-and-ai

Help guide: Offline operation & AI

![Help guide: Offline operation & AI](screenshots/31-help-offline-operation-and-ai.png)

### 31-help-troubleshooting

Help guide: Troubleshooting

![Help guide: Troubleshooting](screenshots/31-help-troubleshooting.png)

### 32-interactive-guide

Separate interactive feature guide: walkthrough steps and navigation. Historical explanatory text is not a replacement for current campaign provenance.

![Separate interactive feature guide: walkthrough steps and navigation. Historical explanatory text is not a replacement for current campaign provenance.](screenshots/32-interactive-guide.png)

### 33-guide-filter

Guide feature filter; results depend on all typed terms.

![Guide feature filter; results depend on all typed terms.](screenshots/33-guide-filter.png)

### 34-guide-zoom

Guide screenshot zoom modal; Close, Escape or backdrop dismiss it.

![Guide screenshot zoom modal; Close, Escape or backdrop dismiss it.](screenshots/34-guide-zoom.png)

### 35-data-real-and-simulated

Data catalog with real archived Sentinel inputs alongside clearly named SIM_DEMO files, and a watched documentation folder. No real orbit processing was launched.

![Data catalog with real archived Sentinel inputs alongside clearly named SIM_DEMO files, and a watched documentation folder. No real orbit processing was launched.](screenshots/35-data-real-and-simulated.png)

### 36-spacecraft-selector

Conditional Spacecraft selector appears because both S3A and S3B observations cover this cutoff. Choose one spacecraft; do not combine their observations.

![Conditional Spacecraft selector appears because both S3A and S3B observations cover this cutoff. Choose one spacecraft; do not combine their observations.](screenshots/36-spacecraft-selector.png)

### 37-run-progress

Active-job progress, log and Cancel shown during a short SIMULATED documentation example; no two-month campaign was started.

![Active-job progress, log and Cancel shown during a short SIMULATED documentation example; no two-month campaign was started.](screenshots/37-run-progress.png)

## Every control outside the parameter form

### Global navigation

#### C001 — Tab buttons

**Control:** Navigation. **Visible when:** Always in this section.

Select Saved presentation, Data, Run, Results, Window, History or Help. Changing tabs does not start an orbit computation.

**All choices:** `Saved presentation`; `Data`; `Run`; `Results`; `Window`; `History`; `Help`.

#### C002 — Quick search

**Control:** Search field + Enter. **Visible when:** Always in this section.

Type a help question and press Enter. Opens Help and searches the local documentation. It does not change run settings.

#### C003 — Job indicator

**Control:** Progress display. **Visible when:** An active job has emitted progress.

Shows active job stage and fractional completion when a new run is executing.

#### C004 — Guide

**Control:** Link. **Visible when:** Always in this section.

Opens the separate interactive feature guide in a new browser tab. Its older walkthrough images/text can differ from the current app; this manual uses current controls.

#### C005 — Theme (◐)

**Control:** Button. **Visible when:** Always in this section.

Switch between light and dark themes. Saved in browser local storage; does not change scientific results.

#### C006 — Quit (⏻)

**Control:** Button + confirmation. **Visible when:** Always in this section.

Stops the local Java server and cancels active jobs. Confirm to stop, cancel to keep presenting. The browser page then reports shutdown.

### Saved presentation

#### C007 — Experiment

**Control:** Dropdown. **Visible when:** Always in this section.

Select a stored campaign profile; filters the archived table without running anything.

**All choices:** `Improved Broadcast — nominal`; `Original four-product comparison`; `Retrospective — original candidate maneuvers`; `Retrospective — corrected candidate maneuvers`.

#### C008 — Product tier

**Control:** Dropdown. **Visible when:** Always in this section.

Restrict the selected archived profile by GPS product tier. A filter with no cases gives an empty-state message.

**All choices:** `all`; `broadcast`; `ultra-rapid`; `rapid`; `final`.

#### C009 — Download all OEMs

**Control:** Download link. **Visible when:** Always in this section.

Downloads the complete archived OEM ZIP, including all experiment profiles and accompanying interpretation notes.

#### C010 — Output explanation PDF

**Control:** Download link. **Visible when:** Always in this section.

Downloads the report explaining the OEM fields, units, coverage and limitations.

#### C011 — Complete coverage CSV

**Control:** Download link. **Visible when:** Always in this section.

Downloads all 427 attempted cases, including unissued results.

#### C012 — View

**Control:** Per-row button. **Visible when:** Always in this section.

Opens saved details for the selected case. The first matching case is selected automatically after filtering.

#### C013 — Download this OEM

**Control:** Button. **Visible when:** Selected case has an issued OEM.

Downloads exactly the selected archived ephemeris. It does not recompute or select a better result.

#### C014 — Saved inputs, fit and environment metadata

**Control:** Disclosure. **Visible when:** Always in this section.

Expand to inspect input basenames, saved fit settings, environment assumptions and the source CSV SHA-256. These are provenance records, not editable live settings.

#### C015 — Archived summary and state table

**Control:** Read-only displays. **Visible when:** Always in this section.

Attempt/issued/rejected counts, status, day-one/day-seven scored errors, interval and eight UTC state samples. Display values are rounded; the OEM retains stored precision. Not scored is not zero error. Daily samples must not be interpolated into a dense orbit track.

### Data

#### C016 — + Add files / folders

**Control:** Button → multi-picker. **Visible when:** Always in this section.

Choose existing files or directories on the machine running the Java server. Directories are expanded and recognized files are catalogued by content. Does not move or delete the originals.

#### C017 — Watch folder

**Control:** Button → folder picker. **Visible when:** Always in this section.

Register a directory for automatic discovery of new or changed files. This is input indexing, not orbit processing.

#### C018 — Rescan

**Control:** Button. **Visible when:** Always in this section.

Re-read known files and update changed metadata/checksums. Does not run OD or propagation.

#### C019 — Create demo dataset

**Control:** Button → folder picker. **Visible when:** Always in this section.

Writes SIMULATED observations, navigation, precise products and a reference orbit to the selected directory. This does computation and writes files. The demo is not real Sentinel-3A data.

#### C020 — Watched-folder ×

**Control:** Per-folder button. **Visible when:** Always in this section.

Stop watching that folder; does not delete files.

#### C021 — Kind filter

**Control:** Dropdown. **Visible when:** Always in this section.

Filter catalog rows by recognized type; Coverage canvas and total count still describe the catalog.

**All choices:** `All kinds`; `GNSS observations (GNSSRD / RINEX obs)`; `GPS broadcast navigation`; `IGS precise orbit (SP3)`; `IGS precise clock (RINEX CLK)`; `Precise orbit ephemeris (POEORB, reference only)`; `Measured attitude (AUX_PROQUA)`; `Earth orientation parameters`; `Space weather (CSSI)`; `JPL planetary ephemeris (DE4xx)`; `Gravity field (ICGEM .gfc)`; `CCSDS orbit ephemeris message (OEM)`; `Unrecognised`.

#### C022 — Filter by name

**Control:** Search input. **Visible when:** Always in this section.

Case-insensitive substring filter on file names. Combines with Kind filter; does not search file contents.

#### C023 — Catalog row hover

**Control:** Tooltip. **Visible when:** Always in this section.

Hover Name to see the full path and SHA-256. Columns are Kind, Name, Sat, Class, UTC coverage, Size, Note and Forget.

#### C024 — Forget ×

**Control:** Per-file button. **Visible when:** Always in this section.

Remove that catalog entry only. The original file is not deleted; watching/rescanning may rediscover it.

#### C025 — Coverage canvas

**Control:** Display. **Visible when:** Always in this section.

Colored intervals group files by recognized kind and show their validity dates. It shows availability, not forecast accuracy or issue-time eligibility.

### Shared file picker

#### C026 — Root / Home

**Control:** Dropdown. **Visible when:** Always in this section.

Navigate the filesystem roots returned by the local server or its Home directory. Options vary by OS: Windows drives or Linux root, plus Home.

#### C027 — Path

**Control:** Text input + Enter. **Visible when:** Always in this section.

Type a directory path on the server machine; press Enter or Go to navigate. Browser download paths and server filesystem paths are distinct.

#### C028 — Go

**Control:** Button. **Visible when:** Always in this section.

Navigate to the typed Path.

#### C029 — Parent (⬑ ..) and folder names

**Control:** Navigation rows. **Visible when:** Always in this section.

Click a parent/folder name to navigate inside it; this does not itself select the directory.

#### C030 — Selection checkbox / radio

**Control:** Selection input. **Visible when:** Always in this section.

Multi mode allows any files/folders and multiple selections. Single-file mode allows one file; folder mode allows one folder. Selection can be preserved across navigation in multi mode.

#### C031 — Select

**Control:** Button. **Visible when:** Always in this section.

Return the checked/radio-selected path(s) to the calling input or catalog action. An empty selection returns no paths.

#### C032 — Use this folder

**Control:** Button. **Visible when:** Folder picker mode.

Choose the directory currently being browsed, even if its radio item is not checked.

#### C033 — Cancel / Escape / background click

**Control:** Close action. **Visible when:** Always in this section.

Dismiss without applying a selection. Shared custom dialogs support Escape and clicking the backdrop.

### Run toolbar and actions

#### C034 — Workflow

**Control:** Two-button toggle. **Visible when:** Always in this section.

OD + Forecast fits pre-cutoff GNSS position fixes and predicts forward. Propagate from a state starts from a typed/file state and skips GNSS orbit fitting.

**All choices:** `OD + Forecast`; `Propagate from a state`.

#### C035 — Presets

**Control:** Dropdown. **Visible when:** Always in this section.

Lists saved user preset names plus the empty placeholder. Presets depend on the current workspace and are not fixed model options.

#### C036 — Load

**Control:** Button. **Visible when:** Always in this section.

Load the selected preset settings into the form. Does not run it.

#### C037 — Save as…

**Control:** Button + name prompt. **Visible when:** Always in this section.

Save the current configuration under the entered preset name. Cancel or an empty name does nothing.

#### C038 — Delete preset

**Control:** Button + confirmation. **Visible when:** Always in this section.

Delete only the selected saved preset after confirmation. Does not delete a previous run or raw input.

#### C039 — Export

**Control:** Button. **Visible when:** Always in this section.

Download current settings as JSON using the run name or ursc-config. This is a configuration, not an OEM.

#### C040 — Import

**Control:** Native JSON file picker. **Visible when:** Always in this section.

Choose a local .json settings file; parse it and load it into the form. Invalid JSON gives an error toast. Does not start computation.

#### C041 — Reset

**Control:** Button + confirmation. **Visible when:** Always in this section.

Restore startup/test-window defaults and attempt catalog input selection. This changes the form, not already saved results.

#### C042 — Generic

**Control:** Button + confirmation. **Visible when:** Always in this section.

Load generic library defaults: generic cannonball spacecraft and no test window. This differs from the Sentinel-3A startup preset.

#### C043 — Group headings / settings counts

**Control:** Disclosure. **Visible when:** Always in this section.

Open or collapse a settings group. Counts report visible settings and modified values. Changing workflow/shape/force switches can reveal other fields.

#### C044 — Auto-select from catalog

**Control:** Button. **Visible when:** OD + Forecast → Mandatory inputs.

Populate observations for one spacecraft and suitable product/reference/attitude/environment files for the cutoff. Adds matching files, not an accuracy guarantee.

#### C045 — Spacecraft selector

**Control:** Dynamic dropdown. **Visible when:** Auto-select found multiple spacecraft.

Select among spacecraft with observations for the current cutoff. Appears only if more than one spacecraft is available; reruns catalog suggestion, not orbit determination.

#### C046 — Latest from data

**Control:** Button. **Visible when:** UTC cutoff field in OD + Forecast.

Set UTC cutoff to the end of the latest matching catalogued observation arc. It does not mean the current wall-clock time.

#### C047 — Run whole window

**Control:** Button + confirmation. **Visible when:** OD + Forecast → Test window.

Start one new OD + forecast for each configured cutoff date. Uses the catalog separately per day; missing data remain visible. Confirmation states date count. This can take a long time.

#### C048 — Run

**Control:** Button. **Visible when:** Always in this section.

Start one new computation using the current workflow/settings. Disabled when validation errors exist or a job is active. For presenting existing results, use Saved presentation instead.

#### C049 — Sweep…

**Control:** Button → dialog. **Visible when:** Always in this section.

Vary one enum/bool/numeric setting while keeping other form settings fixed. Starting a sweep runs new computations; opening/cancelling its dialog does not.

#### C050 — Compare product tiers

**Control:** Button. **Visible when:** OD + Forecast with observations/products; otherwise explains missing prerequisites.

Launch a four-tier sweep using Broadcast, Ultra-rapid, Rapid and Final. It explicitly changes the sweep copy to RECONSTRUCTION so later products can be compared. It does not certify forecast-safe accuracy.

#### C051 — Cancel

**Control:** Button. **Visible when:** Active run, sweep or window.

Requests cancellation of the currently tracked job. It is shown while active work is running; exact stop timing depends on cancellation checks. It is not a delete-results action.

#### C052 — Ready/errors/progress/log

**Control:** Displays. **Visible when:** Always in this section.

Ready to run means configuration validation passed, not that accuracy targets will be met. Error list names blockers. Progress/log show stages, sweep/window case numbers and execution messages.

### Shared setting controls

#### C053 — ⓘ / i

**Control:** Per-field info button. **Visible when:** Always in this section.

Shows what the setting means, how to choose it, its configuration key and startup default. Click elsewhere to dismiss. Workflow itself is represented by the top toggle rather than a normal field.

#### C054 — Changed marker / reset

**Control:** Per-field indicator and button. **Visible when:** Always in this section.

Non-mandatory settings differing from startup defaults show changed and reset. Reset restores only that setting, then revalidates. Mandatory fields do not have this marker/reset behavior.

#### C055 — Checkbox

**Control:** Field widget. **Visible when:** Always in this section.

Toggle on/off; conditional dependent fields may appear or disappear. Exact impact is documented for each of the 85 settings.

#### C056 — Number field

**Control:** Field widget. **Visible when:** Always in this section.

Enter an integer or numeric value; browser min/max/step are shown in the field inventory. These UI bounds are not a complete statement of server-side scientific validation.

#### C057 — Date / datetime / text

**Control:** Field widgets. **Visible when:** Always in this section.

Dates are calendar cutoff dates. Scientific epoch fields are interpreted as UTC despite using a datetime-local browser control; do not convert them to your local time zone.

#### C058 — Span presets

**Control:** Four buttons. **Visible when:** Always in this section.

Set forecast.spanDays to 7, 14, 21 or 30 days immediately. They change the form, not an existing saved output.

**All choices:** `7`; `14`; `21`; `30`.

#### C059 — Add from catalog…

**Control:** Per-file-field dropdown. **Visible when:** Always in this section.

Only lists catalog entries of the kinds allowed by that field. Options vary with the catalog and its file names. Select to add or replace the input. Multi-file values are deduplicated.

#### C060 — Browse…

**Control:** Per-file-field button. **Visible when:** Always in this section.

Open the shared file picker. Multi-file inputs add paths; single-file inputs replace the selection.

#### C061 — Selected file ×

**Control:** Per-path button. **Visible when:** Always in this section.

Remove the path from this configuration only. Does not delete or forget the file globally.

#### C062 — none selected / not set (default used)

**Control:** Field state messages. **Visible when:** Always in this section.

Multi-file fields show none selected; a single optional field not set may use a built-in/default model. Check its specific documented behavior and run defaults list.

### Sweep dialog

#### C063 — Setting to vary

**Control:** Dropdown. **Visible when:** Always in this section.

Includes every enum, boolean, integer and number parameter except workflow, even if a field is conditional in the current form. The complete candidates are indexed in the parameter appendix. Avoid choices incompatible with the selected workflow.

#### C064 — Value checkboxes

**Control:** Dynamic checkboxes. **Visible when:** Always in this section.

For an enum, check the listed model choices; for a boolean, choose on and off. All are initially checked. At least two valid values are required.

#### C065 — Values, separated by commas

**Control:** Text field. **Visible when:** Always in this section.

For numeric parameters, enter comma-separated values in the parameter units. Initially suggests half/current/1.5× values. Review integer/bounds constraints before starting.

#### C066 — Cancel / Start sweep

**Control:** Dialog buttons. **Visible when:** Always in this section.

Cancel dismisses; Start sweep submits up to 12 sequential cases and eventually opens a comparison. Escape/backdrop also cancel.

#### C067 — Current value / explanation

**Control:** Display. **Visible when:** Always in this section.

Shows the selected parameter definition, current value and units. It does not modify the parameter until a sweep starts.

### Results

#### C068 — Run picker

**Control:** Dropdown. **Visible when:** Always in this section.

Select a saved local History run. These are workspace runs, separate from the bundled Saved presentation archive. Opening a saved run does not recompute its OD or trajectory.

#### C069 — Download OEM

**Control:** Button. **Visible when:** Always in this section.

Download the selected run's stored OEM, when present. Failed runs may have no OEM.

#### C070 — Log

**Control:** Button. **Visible when:** Always in this section.

Open the saved execution log in a new tab. Use it to inspect defaults, progress and failure messages.

#### C071 — Re-use settings

**Control:** Button. **Visible when:** Always in this section.

Load this result's configuration into Run; does not execute it until Run is pressed.

#### C072 — KML

**Control:** Button. **Visible when:** Always in this section.

Export the selected trajectory for Google Earth. This derives an export from saved output, not a new orbit propagation.

#### C073 — CZML

**Control:** Button. **Visible when:** Always in this section.

Export the selected trajectory for Cesium from saved data.

#### C074 — Report

**Control:** Button. **Visible when:** Always in this section.

Open the printable run report in a new browser tab; use browser Print/Save PDF. Includes saved summaries, inputs, settings, figures and passes.

#### C075 — Earth-fixed / Inertial

**Control:** View toggle. **Visible when:** Selected run has visualization trajectory.

Change the globe rendering frame; do not confuse a visualization toggle with the configured OEM output frame.

**All choices:** `Earth-fixed`; `Inertial`.

#### C076 — Spacecraft model

**Control:** Dropdown. **Visible when:** Trajectory available.

Automatic chooses a procedural drawing from object name; explicit models override the drawing only. It does not alter force-model geometry or physics.

**All choices:** `Model: automatic`; `Model: Sentinel-3 build`; `Model: Generic satellite`; `Model: dot only`.

#### C077 — Follow satellite

**Control:** Button. **Visible when:** Trajectory available.

Camera follows the satellite. Drag to look around; scroll changes follow distance. Toggle off for globe-centred navigation.

#### C078 — Full view / Leave full view

**Control:** Button. **Visible when:** Trajectory available.

Enlarge the globe to the application window. Use Leave full view or Escape to restore the layout.

#### C079 — Globe mouse gestures

**Control:** Canvas controls. **Visible when:** Trajectory available.

Drag to rotate, scroll to zoom, double-click to reset the camera. In follow mode these act around the spacecraft.

#### C080 — Map layers

**Control:** Five checkboxes. **Visible when:** Trajectory available.

Toggle Full track, Reference, Day / night, Stations and Grid independently. Layer changes only affect drawing. Reference is POEORB display data, not a correction to prediction.

**All choices:** `Full track`; `Reference`; `Day / night`; `Stations`; `Grid`.

#### C081 — Map hover / click

**Control:** Canvas controls. **Visible when:** Trajectory available.

Hover scrubs the shared time when playback is paused. Click selects that epoch and pauses playback; subsequent pointer movement can scrub again. It is not a persistent time-lock switch.

#### C082 — Play / Pause

**Control:** Button. **Visible when:** Trajectory available.

Animate saved samples; wraps to the start at the end. Does not integrate a new orbit.

#### C083 — Playback speed

**Control:** Dropdown. **Visible when:** Trajectory available.

Choose simulated-time speed relative to wall time. It affects animation only.

**All choices:** `60×`; `300×`; `600×`; `1800×`; `3600×`.

#### C084 — Time slider

**Control:** Range input. **Visible when:** Trajectory available.

Scrub the saved sample sequence; UTC and elapsed-hours readouts update. Drawing may interpolate between samples, so use sufficiently dense trajectories for meaningful tracks.

#### C085 — 1σ ellipsoid

**Control:** Checkbox + enlargement label. **Visible when:** Saved trajectory contains formal covariance.

Show the formal position uncertainty ellipsoid when covariance exists. The visual scale multiplier is explicitly displayed. It is not measured error and excludes unmodelled effects.

#### C086 — Run summary and warnings

**Control:** Displays. **Visible when:** Always in this section.

Status/mode/object/cutoff, state count, fit type/count/iterations, pre/post-fit RMS, variance factor, optional estimated Cd/Cr and uncertainties, product usage/rejection/fallbacks, defaults, warnings, errors, flags and runtime. Missing optional quantities are not invented.

#### C087 — Diagnostic plots

**Control:** Conditional displays. **Visible when:** Always in this section.

Altitude; RTN formal 1σ; 3D/R/T/N error against reference; satellite count/PDOP; accepted/rejected SPP residual RMS; OD residual norms. A plot appears only when its saved data exist.

#### C088 — GNSS positioning per epoch

**Control:** Disclosure + table. **Visible when:** Saved SPP rows exist.

Expand to inspect UTC, GPS product, satellite count, PDOP, RMS, accepted/rejected indicator and reason. Predicted orbit tags identify predicted GPS orbit nodes.

### Results — stations and passes

#### C089 — Stations

**Control:** Disclosure. **Visible when:** Always in this section.

Expand the workspace-wide station editor. Changes affect pass calculations/display for runs in that workspace; they do not refit the orbit.

#### C090 — Station Name

**Control:** Text input. **Visible when:** Always in this section.

Human-readable station label.

#### C091 — Latitude / Longitude

**Control:** Numeric inputs. **Visible when:** Always in this section.

Geodetic degrees; latitude north-positive and longitude east-positive. Use valid terrestrial coordinates.

#### C092 — Height

**Control:** Numeric input. **Visible when:** Always in this section.

Station ellipsoidal height in metres, not kilometres.

#### C093 — Elevation mask

**Control:** Numeric input. **Visible when:** Always in this section.

Minimum elevation angle in degrees above the station horizon for a pass to count.

#### C094 — Station ×

**Control:** Per-row button. **Visible when:** Always in this section.

Remove that station from the editor; use Save stations to persist the edited list.

#### C095 — Add station

**Control:** Button. **Visible when:** Always in this section.

Append a station with zero coordinates/height and 5-degree elevation mask. Edit it before saving.

#### C096 — Save stations

**Control:** Button. **Visible when:** Always in this section.

Validate and persist the station list, update globe/map markers and derive passes from saved trajectories. This does not run OD/propagation.

#### C097 — Pass row

**Control:** Clickable result. **Visible when:** Always in this section.

AOS/LOS are acquisition/loss of signal times. The row shows duration and maximum elevation; click to jump to AOS. A truncated span is marked. Requires an adequate sampling cadence and station(s).

### Window

#### C098 — Window picker

**Control:** Dropdown. **Visible when:** Always in this section.

Choose a locally saved window campaign; shows its name, date interval and completion count. The bundled two-month archive is in Saved presentation, not automatically this local list.

#### C099 — Refresh

**Control:** Button. **Visible when:** Always in this section.

Reload the existing local window list and selected record. Does not rerun it.

#### C100 — Delete record

**Control:** Button + confirmation. **Visible when:** Always in this section.

Delete the window summary after confirmation. Its individual run records stay in History.

#### C101 — Progress and summary tiles

**Control:** Displays. **Visible when:** Always in this section.

Cutoff count, processed, issued, unusable OD, no data, flagged cases and median post-fit RMS. No data is separate from fitting failure. Active campaigns show a progress bar and stage.

#### C102 — Trend charts

**Control:** Displays. **Visible when:** Always in this section.

OD RMS and SPP fix count per date; errors vs POEORB appear where reference scores exist. Missing values are gaps, not zeros.

#### C103 — Per-day row / flags hover

**Control:** Clickable row + tooltip. **Visible when:** Always in this section.

Shows cutoff date, status, GPS product, fixes, RMS, variance factor, flags, cutoff/day-one/day-seven errors and runtime. Hover products/flags for details; click a row with a runId to open Results.

### History

#### C104 — Run checkboxes

**Control:** Per-row checkboxes. **Visible when:** Always in this section.

Select two or more locally saved runs for comparison. They do not select bundled snapshot cases.

#### C105 — Compare selected

**Control:** Button. **Visible when:** Always in this section.

Open comparison for the checked runs; at least two required. It compares saved trajectories rather than recomputing the physics.

#### C106 — Open

**Control:** Per-row button. **Visible when:** Always in this section.

Open that saved run in Results.

#### C107 — Re-use

**Control:** Per-row button. **Visible when:** Always in this section.

Load that run's settings in Run, without executing them.

#### C108 — Delete run

**Control:** Per-row button + confirmation. **Visible when:** Always in this section.

Delete the chosen saved run and its output files after confirmation. This differs from forgetting a raw input or deleting a window summary.

#### C109 — History columns

**Control:** Displays. **Visible when:** Always in this section.

Run ID, name, workflow, status, mode, cutoff/epoch, object, sample count and post-fit RMS. Values come from saved result records.

### Comparison panel

#### C110 — Base run and color legend

**Control:** Display. **Visible when:** Always in this section.

First selected run is the base. Other runs are compared to it; base is not automatically the most accurate run.

#### C111 — Position difference table

**Control:** Display. **Visible when:** Always in this section.

Maximum, RMS and end-of-span 3D separation from the base, in metres. Run-to-run separation is not reference-truth error.

#### C112 — Settings that differ

**Control:** Display. **Visible when:** Always in this section.

Configuration keys and values that changed between compared runs. Identical settings get an explicit message.

#### C113 — Comparison charts

**Control:** Displays. **Visible when:** Always in this section.

Reference error curves when stored reference results exist; 3D difference and RTN difference curves for compatible sampled trajectories. Interpolation is used for non-coincident epochs; sparse daily campaign points must not be treated as dense tracks.

### Help

#### C114 — Question / Search

**Control:** Search input + button. **Visible when:** Always in this section.

Type a project question and click Search or press Enter. Retrieves local passages and shows a grounded answer when an answer provider returns one.

#### C115 — Provider status line

**Control:** Display. **Visible when:** Always in this section.

Shows indexed passage/document counts, knowledge folder and configured provider order/status. Default extractive provider uses no model; optional provider availability is configuration-dependent.

#### C116 — Guides

**Control:** Seven navigation buttons. **Visible when:** Always in this section.

Load the corresponding bundled help page.

**All choices:** `Getting started`; `Input data`; `GPS product priority`; `Perturbations & models`; `Outputs (OEM, covariance)`; `Offline operation & AI`; `Troubleshooting`.

#### C117 — Interactive feature guide

**Control:** Link. **Visible when:** Always in this section.

Open the separate feature guide in a new tab, just like the global Guide link.

#### C118 — Answer / passage citations

**Control:** Displays. **Visible when:** Always in this section.

Answer includes provider attribution; passage cards show source, title and excerpts. No-result message means try different terms; it is not a new model training step.

### Interactive Guide page

#### C119 — Section index links

**Control:** Links. **Visible when:** Always in this section.

Jump to walkthrough, defaults/results, offline/search and feature groups. Active section styling follows scrolling.

#### C120 — Walkthrough steps

**Control:** Ten buttons. **Visible when:** Always in this section.

Choose any of the ten walkthrough panels.

**All choices:** `Start the app`; `The data catalog`; `Run setup with tested defaults`; `Explain any setting with ⓘ`; `Perturbation switches`; `One forecast on the globe`; `Ride along with Sentinel-3A`; `The two-month window`; `Compare modelling choices`; `Outputs and help`.

#### C121 — Previous / Next

**Control:** Buttons + arrow keys. **Visible when:** Always in this section.

Move between walkthrough steps; disabled at endpoints. Left/right keyboard arrows also navigate when not typing in a text field or viewing an enlarged screenshot.

#### C122 — Filter features

**Control:** Search input. **Visible when:** Always in this section.

Enter one or more terms; each shown feature must contain every term. The counter shows remaining features and a no-results message appears when appropriate.

#### C123 — Screenshot zoom

**Control:** Figure click / Enter / Space. **Visible when:** Always in this section.

Click a screenshot, or focus it and press Enter/Space, to enlarge it. Close with Close, Escape or the modal backdrop.

#### C124 — Feature cards and guide text

**Control:** Read-only content. **Visible when:** Always in this section.

Explain existing features. Some historical claims in the built-in guide predate the current data archive; use the Saved presentation provenance and current report for campaign facts, rather than its old no-IGS wording.

## Complete Run parameter reference — all 85 definitions

For each field: key, type, unit, startup default, visibility rule, meaning, selection advice, bounds/preset shortcuts and every enum value. Defaults are the audited startup defaults, not an assertion that every stored experiment used them. Generic, imported and reused settings can differ. Field bounds are browser-form constraints; server checks and scientific validity can impose additional requirements.

### Mandatory inputs

#### GNSS observations (GNSSRD) — `inputs.gnssrd`

**Type:** files. **Unit:** none / see explanation. **Startup default:** `[]`.

**Visibility:** Workflow: OD_FORECAST.

The spacecraft's own GPS receiver measurements (Sentinel AUX_GNSSRD archives, .DBL or any RINEX 3 observation file). These are the measurements the orbit is determined from.

**How to choose/use:** Add the files or folders covering at least the fitting arc before the cutoff. Several days can be added; only epochs inside the arc are used. 'Auto-select' picks matching files from the catalog.

**Input widgets:** selected path(s) with remove ×; Add from catalog dropdown; Browse button. Multiple files/folders can be accumulated. Allowed catalog kinds: GNSS_OBSERVATIONS.

#### GPS orbit & clock products — `inputs.gpsProducts`

**Type:** files. **Unit:** none / see explanation. **Startup default:** `[]`.

**Visibility:** Workflow: OD_FORECAST.

GPS satellite orbit and clock information used to interpret the spacecraft observations. Supply compatible broadcast navigation or precise orbit/clock products.

**How to choose/use:** Choose by coverage, provider/solution compatibility and issue-time eligibility; nominal product accuracy figures are not a guarantee of the resulting LEO forecast error.

**Input widgets:** selected path(s) with remove ×; Add from catalog dropdown; Browse button. Multiple files/folders can be accumulated. Allowed catalog kinds: IGS_ORBIT_SP3, IGS_CLOCK, GPS_BROADCAST_NAV.

#### UTC cutoff — `cutoffUtc`

**Type:** datetime. **Unit:** none / see explanation. **Startup default:** `"2024-09-03T05:59:42"`.

**Visibility:** Workflow: OD_FORECAST.

The UTC forecast issue boundary: measurements are fitted up to this epoch and prediction begins here.

**How to choose/use:** Choose an epoch with observation coverage. Cutoff-safe claims also depend on product issue-time evidence and on weather, EOP and attitude policies; selecting Forecast alone is not independent proof.

#### Workflow — `workflow`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"OD_FORECAST"`.

**Visibility:** Both workflows.

What to run: orbit determination from GNSS measurements followed by a forecast, or a plain propagation from one known state.

**How to choose/use:** Use OD + Forecast for real operations. Use Propagate when you already have a state (typed, or from an OEM/POEORB) and want to study force models without fitting.

**Every dropdown/toggle choice:**

- **OD + Forecast** (`OD_FORECAST`): Fit pre-cutoff GNSS fixes, then forecast from the estimated cutoff state.
- **Propagate from a state** (`PROPAGATE`): Use an existing typed or file-based state; do not run GNSS orbit determination.

### Test window

#### First cutoff date — `window.startDate`

**Type:** date. **Unit:** UTC. **Startup default:** `"2024-09-03"`.

**Visibility:** Workflow: OD_FORECAST.

First day of a window run. A window run repeats orbit determination and a forecast once per day, each day with its own cutoff and its own input files from the catalog.

**How to choose/use:** The tested Sentinel-3A window starts on 2024-09-03. Press 'Run whole window' to process every day up to the last date; press '▶ Run' for the single cutoff above.

#### Last cutoff date — `window.endDate`

**Type:** date. **Unit:** UTC. **Startup default:** `"2024-11-02"`.

**Visibility:** Workflow: OD_FORECAST.

Last day of the window run (inclusive).

**How to choose/use:** The tested window ends on 2024-11-02 (61 daily cutoffs). Each day needs observations and a GPS product in the catalog; days without data are listed as 'no data' and skipped.

#### Cutoff time of day — `window.cutoffTimeUtc`

**Type:** text. **Unit:** UTC hh:mm:ss. **Startup default:** `"05:59:42"`.

**Visibility:** Workflow: OD_FORECAST.

Time of day of every cutoff in the window.

**How to choose/use:** 05:59:42 UTC is 06:00:00 GPS time in 2024 (GPS runs 18 s ahead of UTC); the tested window used it, so the six-hour arc covers GPS 00:00-06:00 of each day.

#### Days between cutoffs — `window.stepDays`

**Type:** int. **Unit:** days. **Startup default:** `1`.

**Visibility:** Workflow: OD_FORECAST.

Spacing of the cutoffs in the window run.

**How to choose/use:** 1 = every day (the tested set). Use 7 for a quick weekly overview: about 9 runs instead of 61.

**Form bounds:** 1 to 30.

### Initial state

#### State source — `initialState.source`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"TYPED"`.

**Visibility:** Workflow: PROPAGATE.

Where the starting state comes from.

**How to choose/use:** Typed: enter it. OEM/POEORB: pick a file; the state nearest the epoch below (or the first) is used.

**Every dropdown/toggle choice:**

- **Typed** (`TYPED`): Enter epoch, frame, XYZ in metres and velocity in m/s.
- **From an OEM file** (`OEM_FILE`): Choose an OEM and select the requested/first starting state; this is propagation from supplied ephemeris.
- **From a POEORB file** (`POEORB_FILE`): Choose a POEORB and select the starting state; this deliberately uses a precise seed and is not GNSS-only OD.

#### State file — `initialState.file`

**Type:** file. **Unit:** none / see explanation. **Startup default:** `""`.

**Visibility:** Workflow: PROPAGATE; also initialState.source is not TYPED.

OEM or POEORB file holding the initial state.

**How to choose/use:** Pick from the catalog or browse.

**Input widgets:** selected path(s) with remove ×; Add from catalog dropdown; Browse button. A single file replaces the prior selection. Allowed catalog kinds: OEM, POEORB.

#### Epoch (UTC) — `initialState.epochUtc`

**Type:** datetime. **Unit:** none / see explanation. **Startup default:** `""`.

**Visibility:** Workflow: PROPAGATE.

Epoch of the typed state, or the epoch to pick from a file.

**How to choose/use:** For files, blank = first state.

#### Frame of typed state — `initialState.frame`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"ITRF"`.

**Visibility:** Workflow: PROPAGATE; also initialState.source equals TYPED.

Reference frame of the typed position/velocity.

**How to choose/use:** ITRF for states from POEORB/GNSS, EME2000/GCRF for inertial states.

**Every dropdown/toggle choice:**

- **ITRF** (`ITRF`): Interpret the typed vector in Earth-fixed ITRF; position and velocity must both belong to that frame.
- **GCRF** (`GCRF`): Interpret the typed vector in GCRF.
- **EME2000** (`EME2000`): Interpret the typed vector in EME2000.

#### Position X — `initialState.x`

**Type:** number. **Unit:** m. **Startup default:** `0`.

**Visibility:** Workflow: PROPAGATE; also initialState.source equals TYPED.

Initial position component.

**How to choose/use:** Metres in the frame above.

#### Position Y — `initialState.y`

**Type:** number. **Unit:** m. **Startup default:** `0`.

**Visibility:** Workflow: PROPAGATE; also initialState.source equals TYPED.

Initial position component.

**How to choose/use:** Metres in the frame above.

#### Position Z — `initialState.z`

**Type:** number. **Unit:** m. **Startup default:** `0`.

**Visibility:** Workflow: PROPAGATE; also initialState.source equals TYPED.

Initial position component.

**How to choose/use:** Metres in the frame above.

#### Velocity X — `initialState.vx`

**Type:** number. **Unit:** m/s. **Startup default:** `0`.

**Visibility:** Workflow: PROPAGATE; also initialState.source equals TYPED.

Initial velocity component.

**How to choose/use:** Metres per second in the frame above.

#### Velocity Y — `initialState.vy`

**Type:** number. **Unit:** m/s. **Startup default:** `0`.

**Visibility:** Workflow: PROPAGATE; also initialState.source equals TYPED.

Initial velocity component.

**How to choose/use:** Metres per second in the frame above.

#### Velocity Z — `initialState.vz`

**Type:** number. **Unit:** m/s. **Startup default:** `0`.

**Visibility:** Workflow: PROPAGATE; also initialState.source equals TYPED.

Initial velocity component.

**How to choose/use:** Metres per second in the frame above.

### GPS products & mode

#### Product mode — `products.mode`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"FORECAST"`.

**Visibility:** Workflow: OD_FORECAST.

Choose cutoff-restricted prediction or retrospective reconstruction.

**How to choose/use:** Use Forecast with disclosed issue-time assumptions for prediction tests; Reconstruction allows later information and must be labelled accordingly.

**Every dropdown/toggle choice:**

- **Forecast (cutoff-safe)** (`FORECAST`): Restrict inputs using the configured cutoff policy. Publication assumptions must still be disclosed.
- **Reconstruction (retrospective)** (`RECONSTRUCTION`): Allow later retrospective products; do not call this an as-issued forecast.

#### Issue-time evidence — `products.eligibility`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"NOMINAL_LATENCY"`.

**Visibility:** Workflow: OD_FORECAST.

How a precise product's publication time is established for Forecast mode. Nominal latency assumes IGS's documented delays (Ultra-rapid: 3 h after its observed part ends; Rapid: 17 h after the day; Final: 18 days after). Verified requires real issue-time metadata.

**How to choose/use:** Nominal latency is the practical default and is reported as an assumption. Choose Verified only if your archive records when each file arrived.

**Every dropdown/toggle choice:**

- **Nominal latency (assumed)** (`NOMINAL_LATENCY`): Assume class-dependent publication delays; this is an estimate, not proof of actual issue time.
- **Verified issue times only** (`REQUIRE_VERIFIED`): Require recorded issue-time evidence. Products without acceptable evidence can be excluded.

#### Force product tier — `products.tier`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"AUTO"`.

**Visibility:** Workflow: OD_FORECAST.

Automatic picks the best eligible product per epoch. Forcing a tier uses only that class, for comparisons.

**How to choose/use:** Leave on Automatic for operations. Force Broadcast vs Rapid etc. to measure how much precise products improve the orbit.

**Every dropdown/toggle choice:**

- **Automatic (priority order)** (`AUTO`): Choose best eligible product per satellite/epoch: Final → Rapid → Ultra-Rapid → Broadcast.
- **IGS Final only** (`FINAL`): Restrict to Final-class precise products; eligible coverage must exist.
- **IGS Rapid only** (`RAPID`): Restrict to Rapid-class products.
- **IGS Ultra-rapid only** (`ULTRA_RAPID`): Restrict to Ultra-Rapid products; observed/predicted status and cutoff rules still apply.
- **Broadcast only** (`BROADCAST`): Restrict to broadcast navigation ephemerides.

### Orbit determination

#### Fitting arc — `od.arcHours`

**Type:** number. **Unit:** h. **Startup default:** `6`.

**Visibility:** Workflow: OD_FORECAST.

Length of the measurement window before the cutoff that the orbit is fitted to.

**How to choose/use:** 4-12 h is typical for LEO. Longer arcs average out noise and constrain drag better, but a manoeuvre inside the arc spoils the fit and drag mismodelling grows with length. 6 h is a good default.

**Form bounds:** 0.5 to 72.

#### Observation sampling — `od.samplingSeconds`

**Type:** number. **Unit:** s. **Startup default:** `300`.

**Visibility:** Workflow: OD_FORECAST.

Spacing of the GNSS epochs used for positioning and fitting.

**How to choose/use:** 300 s is plenty for a 6 h arc and fast. 30-60 s adds more fixes (slightly less noise) at higher cost. Must divide evenly into the data rate.

**Form bounds:** 1 to 3600.

#### Pseudorange sigma — `od.pseudorangeSigma`

**Type:** number. **Unit:** m. **Startup default:** `5`.

**Visibility:** Workflow: OD_FORECAST.

Assumed 1-sigma noise of the ionosphere-free code measurement; sets the weight of each fix and the size of the formal covariance.

**How to choose/use:** 2-5 m for dual-frequency code with broadcast orbits; 1-2 m with IGS products. Too small makes the covariance optimistic; the run reports a variance factor near 1 when this is right.

**Form bounds:** 0.1 to 50.

#### Covariance weighting — `od.covarianceWeighting`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `true`.

**Visibility:** Workflow: OD_FORECAST.

Use the position-fix covariance to weight measurement residuals instead of equal weights.

**How to choose/use:** Keep the covariance/noise model physically meaningful; weighting does not itself guarantee smaller true errors or calibrated uncertainty.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Max OD iterations — `od.maxIterations`

**Type:** int. **Unit:** none / see explanation. **Startup default:** `12`.

**Visibility:** Workflow: OD_FORECAST.

Upper limit on Gauss-Newton iterations of the batch least-squares fit. A run that has not converged issues no forecast.

**How to choose/use:** Convergence normally takes 3-6 iterations; 12 leaves margin. Raising it rarely helps a fit that is not converging; check the data instead.

**Form bounds:** 1 to 50.

#### Min GPS satellites — `od.minSatellites`

**Type:** int. **Unit:** none / see explanation. **Startup default:** `4`.

**Visibility:** Workflow: OD_FORECAST.

Minimum satellites a GPS product must cover at an epoch to be selected for it (4 unknowns: position and clock).

**How to choose/use:** Keep 4. Raising it to 5-6 rejects weak epochs at the cost of fewer fixes.

**Form bounds:** 4 to 12.

#### Max PDOP — `od.maxPdop`

**Type:** number. **Unit:** none / see explanation. **Startup default:** `6`.

**Visibility:** Workflow: OD_FORECAST.

Position dilution of precision limit; fixes with worse satellite geometry are rejected.

**How to choose/use:** 4-6 is typical. Lower is stricter (fewer, better fixes).

**Form bounds:** 1 to 50.

#### Max SPP residual RMS — `od.maxSppRms`

**Type:** number. **Unit:** m. **Startup default:** `30`.

**Visibility:** Workflow: OD_FORECAST.

Rejects position fixes whose pseudorange residuals are larger than this, which usually means a bad measurement or satellite.

**How to choose/use:** 10-30 m with broadcast orbits, 5-10 m with precise products.

**Form bounds:** 1 to 500.

#### Estimate drag coefficient — `od.estimateCd`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `false`.

**Visibility:** Workflow: OD_FORECAST.

Solve for the drag coefficient Cd together with the orbit, instead of keeping it fixed.

**How to choose/use:** Helps when drag dominates the error (low orbits, high solar activity) and the arc is long enough (>= 6-12 h). On short arcs it can be poorly determined and absorb noise.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Estimate radiation coefficient — `od.estimateCr`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `false`.

**Visibility:** Workflow: OD_FORECAST.

Solve for the solar radiation pressure coefficient Cr.

**How to choose/use:** Usually weakly observable over a few hours; leave off unless the arc spans many orbits and SRP matters (high area-to-mass).

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

### Forecast & output

#### Run name — `runName`

**Type:** text. **Unit:** none / see explanation. **Startup default:** `"Sentinel-3A tested window"`.

**Visibility:** Both workflows.

Free label stored with the run and shown in the history.

**How to choose/use:** Use something you will recognise later, e.g. 'S3A 16 Oct rapid vs broadcast'.

#### Forecast / propagation span — `forecast.spanDays`

**Type:** number. **Unit:** days. **Startup default:** `7`.

**Visibility:** Both workflows.

How far ahead of the cutoff (or initial state) the orbit is predicted.

**How to choose/use:** 7 days is the default; 14, 21 or 30 are one click away, or type any value. Errors grow with time mainly through drag; beyond a week the formal covariance and the uncertainty in solar activity dominate.

**Form bounds:** 0.01 to 60.

**Shortcut buttons:** 7, 14, 21, 30. These set the field immediately.

#### Output step — `forecast.outputStepSeconds`

**Type:** number. **Unit:** s. **Startup default:** `60`.

**Visibility:** Both workflows.

Spacing of stored ephemeris samples, not the adaptive internal integration step.

**How to choose/use:** Choose adequate resolution for orbit users (often tens of seconds). Existing daily campaign OEMs are sparse benchmark exports and do not inherit the dense pipeline interpolation declaration.

**Form bounds:** 1 to 3600.

#### OEM reference frame — `forecast.frame`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"EME2000"`.

**Visibility:** Both workflows.

Frame of the positions/velocities written to the OEM.

**How to choose/use:** EME2000 is the most widely read inertial frame. GCRF differs from it by ~23 mas (frame bias, ~1 m at LEO). ITRF is Earth-fixed, convenient for ground stations and maps.

**Every dropdown/toggle choice:**

- **EME2000 (inertial, J2000)** (`EME2000`): Write inertial Earth mean equator/equinox of J2000 coordinates.
- **GCRF (inertial, ICRF-aligned)** (`GCRF`): Write geocentric celestial inertial-frame coordinates.
- **ITRF (Earth-fixed)** (`ITRF`): Write rotating Earth-fixed terrestrial coordinates. Velocity must use the corresponding rotating-frame transform.

#### Write formal covariance — `forecast.covariance`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `false`.

**Visibility:** Workflow: OD_FORECAST.

Propagates the OD uncertainty forward and writes 6x6 covariance blocks into the OEM.

**How to choose/use:** Costs one extra (simplified) propagation per estimated parameter. It is formal uncertainty from the measurement noise model, not a guarantee of actual error (drag and manoeuvres are not in it).

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Covariance every N steps — `forecast.covarianceEveryNSteps`

**Type:** int. **Unit:** none / see explanation. **Startup default:** `60`.

**Visibility:** Workflow: OD_FORECAST.

Write a covariance block every N ephemeris lines.

**How to choose/use:** 60 with a 60 s step gives hourly covariance, a good balance of detail and file size.

**Form bounds:** 1 to 10000.

### Spacecraft

#### Object name — `spacecraft.objectName`

**Type:** text. **Unit:** none / see explanation. **Startup default:** `"SENTINEL-3A"`.

**Visibility:** Both workflows.

OEM OBJECT_NAME. Blank = taken from the GNSSRD file (e.g. SENTINEL-3A).

**How to choose/use:** Leave blank for Sentinel files; set it for other spacecraft.

#### Object ID — `spacecraft.objectId`

**Type:** text. **Unit:** none / see explanation. **Startup default:** `"2016-011A"`.

**Visibility:** Both workflows.

OEM OBJECT_ID, normally the COSPAR international designator.

**How to choose/use:** e.g. 2016-011A for Sentinel-3A. Blank = filled for known Sentinels, else UNKNOWN.

#### Mass — `spacecraft.massKg`

**Type:** number. **Unit:** kg. **Startup default:** `1130`.

**Visibility:** Both workflows.

Spacecraft mass. Drag and radiation accelerations scale with area divided by mass.

**How to choose/use:** Use the current mass including propellant. Only the ratios Cd*A/m and Cr*A/m matter, so an error in mass can be absorbed by estimating Cd.

**Form bounds:** 1 to 100000.

#### Shape model — `spacecraft.shape`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"CANNONBALL"`.

**Visibility:** Both workflows.

Cannonball: one constant area in every direction. Box-wing: a body box plus solar array whose projected area follows the attitude.

**How to choose/use:** Cannonball is the generic default and is adequate when Cd is fixed or estimated. Box-wing is more physical for elongated spacecraft with large arrays and needs the plate areas.

**Every dropdown/toggle choice:**

- **Cannonball (generic)** (`CANNONBALL`): Use constant drag/SRP areas, Cd, Cr and mass; the separately entered face areas are not used.
- **Box-wing (attitude-dependent)** (`BOX_WING`): Use attitude-dependent projected body/array geometry; exposes attitude, face areas, wing area and cant.

#### Drag area — `spacecraft.dragAreaM2`

**Type:** number. **Unit:** m^2. **Startup default:** `8`.

**Visibility:** Both workflows; also spacecraft.shape equals CANNONBALL.

Cross-section facing the flow (cannonball model).

**How to choose/use:** Average projected area; with Cd 2.2 typical LEO spacecraft have A/m of 0.005-0.02 m^2/kg.

**Form bounds:** 0.01 to 1000.

#### Radiation area — `spacecraft.srpAreaM2`

**Type:** number. **Unit:** m^2. **Startup default:** `8`.

**Visibility:** Both workflows; also spacecraft.shape equals CANNONBALL.

Area facing the Sun (cannonball model).

**How to choose/use:** Include the solar array, which usually dominates.

**Form bounds:** 0.01 to 1000.

#### Drag coefficient Cd — `spacecraft.cd`

**Type:** number. **Unit:** none / see explanation. **Startup default:** `2.2`.

**Visibility:** Both workflows.

How efficiently the thin upper atmosphere transfers momentum to the spacecraft.

**How to choose/use:** 2.0-2.4 for most satellites; 2.2 is the standard assumption. Estimate it in the OD when drag dominates.

**Form bounds:** 0.5 to 5.

#### Radiation coefficient Cr — `spacecraft.cr`

**Type:** number. **Unit:** none / see explanation. **Startup default:** `1.3`.

**Visibility:** Both workflows.

Fraction of sunlight momentum transferred: 1 = fully absorbed, 2 = perfect mirror.

**How to choose/use:** 1.2-1.5 is typical.

**Form bounds:** 0.5 to 2.5.

#### Attitude source — `spacecraft.attitude`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"NOMINAL_YAW_STEERING"`.

**Visibility:** Both workflows; also spacecraft.shape equals BOX_WING.

Choose modelled nominal orientation or eligible measured PROQUA quaternions for box-wing forces.

**How to choose/use:** Use documented attitude conventions and coverage. Do not assume nominal and measured attitudes agree to a fixed angle for every date.

**Every dropdown/toggle choice:**

- **Nominal yaw steering** (`NOMINAL_YAW_STEERING`): Use the modelled nominal orientation; no measured-attitude input required. This is an assumption, not recovered telemetry.
- **Measured (AUX_PROQUA)** (`MEASURED_PROQUA`): Use eligible measured quaternion inputs where supplied/covered; inspect cutoff and fallback warnings.

#### Body face X area — `spacecraft.faceAreaX`

**Type:** number. **Unit:** m^2. **Startup default:** `2`.

**Visibility:** Both workflows; also spacecraft.shape equals BOX_WING.

Plate area of the box-wing model.

**How to choose/use:** From the spacecraft's macro-model document (e.g. Sentinel-3 CPOD: X 2.84, Y 5.41, Z 6.10, array 10.5 m^2).

**Form bounds:** 0 to 1000.

#### Body face Y area — `spacecraft.faceAreaY`

**Type:** number. **Unit:** m^2. **Startup default:** `2`.

**Visibility:** Both workflows; also spacecraft.shape equals BOX_WING.

Plate area of the box-wing model.

**How to choose/use:** From the spacecraft's macro-model document (e.g. Sentinel-3 CPOD: X 2.84, Y 5.41, Z 6.10, array 10.5 m^2).

**Form bounds:** 0 to 1000.

#### Body face Z area — `spacecraft.faceAreaZ`

**Type:** number. **Unit:** m^2. **Startup default:** `2`.

**Visibility:** Both workflows; also spacecraft.shape equals BOX_WING.

Plate area of the box-wing model.

**How to choose/use:** From the spacecraft's macro-model document (e.g. Sentinel-3 CPOD: X 2.84, Y 5.41, Z 6.10, array 10.5 m^2).

**Form bounds:** 0 to 1000.

#### Solar array area — `spacecraft.wingAreaM2`

**Type:** number. **Unit:** m^2. **Startup default:** `5`.

**Visibility:** Both workflows; also spacecraft.shape equals BOX_WING.

Plate area of the box-wing model.

**How to choose/use:** From the spacecraft's macro-model document (e.g. Sentinel-3 CPOD: X 2.84, Y 5.41, Z 6.10, array 10.5 m^2).

**Form bounds:** 0 to 1000.

#### Array cant angle — `spacecraft.arrayCantDeg`

**Type:** number. **Unit:** deg. **Startup default:** `0`.

**Visibility:** Both workflows; also spacecraft.shape equals BOX_WING.

Tilt of the solar array relative to its rotation plane.

**How to choose/use:** Sentinel-3: 24 deg.

**Form bounds:** -90 to 90.

### Perturbations

#### Earth gravity field — `forces.gravity.enabled`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `true`.

**Visibility:** Both workflows.

Earth's non-spherical gravity (spherical harmonics). Off = central point mass only (two-body).

**How to choose/use:** Always on for real work; switch off only to see how much the field matters.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Gravity degree — `forces.gravity.degree`

**Type:** int. **Unit:** none / see explanation. **Startup default:** `70`.

**Visibility:** Both workflows; also forces.gravity.enabled equals True.

Highest spherical-harmonic degree used.

**How to choose/use:** Effect saturates near degree 70-80 at 800 km. 20-40 is faster with small loss; bundled EGM96 stops at 70 (add an ICGEM file for more).

**Form bounds:** 2 to 360.

#### Gravity order — `forces.gravity.order`

**Type:** int. **Unit:** none / see explanation. **Startup default:** `70`.

**Visibility:** Both workflows; also forces.gravity.enabled equals True.

Highest order used (<= degree).

**How to choose/use:** Normally equal to the degree. Order 0 keeps only the zonal terms (J2, J3...).

**Form bounds:** 0 to 360.

#### Atmospheric drag — `forces.drag.enabled`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `true`.

**Visibility:** Both workflows.

Drag from the thin upper atmosphere; the dominant uncertainty for LEO prediction.

**How to choose/use:** Always on below ~1000 km.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Density model — `forces.dragModel`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"NRLMSISE00"`.

**Visibility:** Both workflows; also forces.drag.enabled equals True.

Choose the implemented atmospheric density model used by drag.

**How to choose/use:** Compare on matched held-out dates and consistent drivers. A model is not universally best; storm response alone does not establish improved forecast accuracy.

**Every dropdown/toggle choice:**

- **NRLMSISE-00** (`NRLMSISE00`): Empirical NRLMSISE-00 thermospheric density driven by solar/geomagnetic inputs.
- **JB2008** (`JB2008`): JB2008 implementation with its required solar/geomagnetic drivers; verify driver coverage and future-input policy.
- **Harris-Priester** (`HARRIS_PRIESTER`): Simplified diurnal-density model; useful for sensitivity studies and quick checks.
- **Exponential** (`EXPONENTIAL`): Simple altitude-density approximation; useful as a baseline, not a guarantee of precise prediction.

#### Solar radiation pressure — `forces.srp.enabled`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `true`.

**Visibility:** Both workflows.

Push of sunlight, with Earth shadow (umbra/penumbra).

**How to choose/use:** On for all real work; metre-level over a day.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Sun gravity — `forces.sun.enabled`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `true`.

**Visibility:** Both workflows.

Third-body attraction of the Sun.

**How to choose/use:** Keep on; tens of metres over a day.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Moon gravity — `forces.moon.enabled`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `true`.

**Visibility:** Both workflows.

Third-body attraction of the Moon.

**How to choose/use:** Keep on; tens of metres over a day.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Sun/Moon ephemeris — `forces.thirdBodyEphemeris`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"ANALYTIC"`.

**Visibility:** Both workflows.

Choose the Sun/Moon ephemeris source used by the force/environment setup.

**How to choose/use:** Analytic needs no file; DE440 requires a compatible file. Neither is exact, and an improvement must be measured.

**Every dropdown/toggle choice:**

- **Analytic series** (`ANALYTIC`): Use bundled analytical Sun/Moon series without a DE440 file.
- **JPL DE440 file** (`DE440`): Use a supplied compatible JPL DE440 file. More detailed ephemerides are not mathematically exact or an automatic accuracy improvement.

#### Solid Earth tides — `forces.solidTides.enabled`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `true`.

**Visibility:** Both workflows.

Gravity change from the Earth's body tides.

**How to choose/use:** Small (decimetres/day); keep on for precise work.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Ocean tides — `forces.oceanTides.enabled`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `true`.

**Visibility:** Both workflows.

Gravity change from ocean tides (FES2004, bundled).

**How to choose/use:** Small (centimetres-decimetres/day).

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### General relativity — `forces.relativity.enabled`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `true`.

**Visibility:** Both workflows.

Schwarzschild correction to Earth's gravity.

**How to choose/use:** Centimetre-level; keep on for precise work.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Lense-Thirring — `forces.lenseThirring`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `true`.

**Visibility:** Both workflows; also forces.relativity.enabled equals True.

Frame-dragging relativistic term.

**How to choose/use:** Millimetre-level; harmless to keep on.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Earth albedo & infrared — `forces.albedo.enabled`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `true`.

**Visibility:** Both workflows.

Radiation pressure from sunlight reflected by Earth and its thermal emission.

**How to choose/use:** Decimetre-level for LEO; keep on.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Empirical acceleration — `forces.empirical.enabled`

**Type:** bool. **Unit:** none / see explanation. **Startup default:** `false`.

**Visibility:** Both workflows.

Constant extra acceleration in radial/transverse/normal axes, for experiments.

**How to choose/use:** Leave off unless testing the sensitivity to unmodelled forces.

**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.

#### Empirical radial — `forces.empirical.radial`

**Type:** number. **Unit:** m/s^2. **Startup default:** `0`.

**Visibility:** Both workflows; also forces.empirical.enabled equals True.

Constant radial acceleration.

**How to choose/use:** 1e-8 m/s^2 shifts a LEO orbit by metres per day.

#### Empirical transverse — `forces.empirical.transverse`

**Type:** number. **Unit:** m/s^2. **Startup default:** `0`.

**Visibility:** Both workflows; also forces.empirical.enabled equals True.

Constant transverse acceleration.

**How to choose/use:** 1e-8 m/s^2 shifts a LEO orbit by metres per day.

#### Empirical normal — `forces.empirical.normal`

**Type:** number. **Unit:** m/s^2. **Startup default:** `0`.

**Visibility:** Both workflows; also forces.empirical.enabled equals True.

Constant normal acceleration.

**How to choose/use:** 1e-8 m/s^2 shifts a LEO orbit by metres per day.

### Optional data & environment

#### POEORB reference orbits — `inputs.poeorb`

**Type:** files. **Unit:** none / see explanation. **Startup default:** `[]`.

**Visibility:** Both workflows.

Independent reference files used by the nominal OD/forecast workflow for overlays and scoring.

**How to choose/use:** Reference comparison does not feed back into nominal prediction. The separate Initial state → POEORB source deliberately uses a reference state as a propagation seed.

**Input widgets:** selected path(s) with remove ×; Add from catalog dropdown; Browse button. Multiple files/folders can be accumulated. Allowed catalog kinds: POEORB.

#### Measured attitude (PROQUA) — `inputs.proqua`

**Type:** files. **Unit:** none / see explanation. **Startup default:** `[]`.

**Visibility:** Both workflows.

AUX_PROQUA quaternion files for the box-wing model.

**How to choose/use:** Only used when Shape = Box-wing and Attitude = Measured.

**Input widgets:** selected path(s) with remove ×; Add from catalog dropdown; Browse button. Multiple files/folders can be accumulated. Allowed catalog kinds: ATTITUDE_PROQUA.

#### Earth orientation file — `inputs.eopFile`

**Type:** file. **Unit:** none / see explanation. **Startup default:** `""`.

**Visibility:** Both workflows.

IERS finals2000A or EOP C04 file (polar motion and UT1-UTC).

**How to choose/use:** Strongly recommended: without it UT1-UTC and polar motion are taken as zero, which costs metres to tens of metres in the Earth-fixed frame.

**Input widgets:** selected path(s) with remove ×; Add from catalog dropdown; Browse button. A single file replaces the prior selection. Allowed catalog kinds: EOP.

#### Space weather file — `inputs.spaceWeatherFile`

**Type:** file. **Unit:** none / see explanation. **Startup default:** `""`.

**Visibility:** Both workflows.

CSSI file with solar flux F10.7 and geomagnetic Ap history.

**How to choose/use:** Recommended for NRLMSISE-00. Without it the constant values below are used.

**Input widgets:** selected path(s) with remove ×; Add from catalog dropdown; Browse button. A single file replaces the prior selection. Allowed catalog kinds: SPACE_WEATHER.

#### JPL DE440 file — `inputs.de440File`

**Type:** file. **Unit:** none / see explanation. **Startup default:** `""`.

**Visibility:** Both workflows.

Compatible JPL planetary ephemeris data used when DE440 is selected.

**How to choose/use:** Supply the proper data format and time coverage; the file is not needed for the analytic option.

**Input widgets:** selected path(s) with remove ×; Add from catalog dropdown; Browse button. A single file replaces the prior selection. Allowed catalog kinds: DE_EPHEMERIS.

#### Gravity field file — `inputs.gravityFile`

**Type:** file. **Unit:** none / see explanation. **Startup default:** `""`.

**Visibility:** Both workflows.

ICGEM .gfc gravity model (e.g. EIGEN-6S, EGM2008).

**How to choose/use:** Optional; the bundled EGM96 (to degree 70) is the default.

**Input widgets:** selected path(s) with remove ×; Add from catalog dropdown; Browse button. A single file replaces the prior selection. Allowed catalog kinds: GRAVITY_FIELD.

#### F10.7 (constant) — `weather.f107`

**Type:** number. **Unit:** sfu. **Startup default:** `150`.

**Visibility:** Both workflows.

Daily solar radio flux at 10.7 cm, a proxy for the Sun's heating of the thermosphere. Used when no weather file is given.

**How to choose/use:** Solar minimum ~70, moderate ~150, maximum 200-250.

**Form bounds:** 50 to 400.

#### F10.7 81-day mean (constant) — `weather.f107a`

**Type:** number. **Unit:** sfu. **Startup default:** `150`.

**Visibility:** Both workflows.

81-day mean solar-flux driver used by the atmosphere; the constant is used if no weather file overrides it.

**How to choose/use:** A retrospective centred average can contain future information. Forecast experiments must retain the configured cutoff-safe treatment and document which value was available.

**Form bounds:** 50 to 400.

#### Ap (constant) — `weather.ap`

**Type:** number. **Unit:** none / see explanation. **Startup default:** `15`.

**Visibility:** Both workflows.

Daily geomagnetic index; storms heat and expand the atmosphere.

**How to choose/use:** Quiet ~5, unsettled ~15, storm 50-400.

**Form bounds:** 0 to 400.

#### Weather after cutoff — `weather.afterCutoff`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"PERSIST"`.

**Visibility:** Both workflows.

How solar and geomagnetic drivers are treated after the cutoff when a weather file is present.

**How to choose/use:** Persist (cutoff-safe) holds F10.7 at its cutoff value and uses the forecast Ap below. 'Use file' reads actual later values: retrospective only.

**Every dropdown/toggle choice:**

- **Persist (cutoff-safe)** (`PERSIST`): Use the cutoff-based persistence policy and configured forecast Ap; do not read future measured drivers.
- **Use file (retrospective)** (`USE_FILE`): Use later file values: retrospective scenario, not operational future-weather prediction.

#### Forecast Ap — `weather.forecastAp`

**Type:** number. **Unit:** none / see explanation. **Startup default:** `5`.

**Visibility:** Both workflows.

Assumed post-cutoff geomagnetic index for the selected persistence policy.

**How to choose/use:** It is a scenario assumption, not a guaranteed storm forecast. Compare quiet and stressed scenarios without inserting future measured storm values.

**Form bounds:** 0 to 400.

#### EOP after cutoff — `eop.afterCutoff`

**Type:** enum. **Unit:** none / see explanation. **Startup default:** `"PERSIST"`.

**Visibility:** Both workflows.

Earth orientation after the cutoff.

**How to choose/use:** Persist holds the cutoff values (cutoff-safe); 'Use file' reads later values (retrospective).

**Every dropdown/toggle choice:**

- **Persist (cutoff-safe)** (`PERSIST`): Hold cutoff Earth-orientation values according to the configured policy.
- **Use file (retrospective)** (`USE_FILE`): Read later EOP file values; label the experiment retrospective.

### Integrator

#### Absolute tolerance — `integrator.absTol`

**Type:** number. **Unit:** m. **Startup default:** `1e-10`.

**Visibility:** Both workflows.

Absolute error-control parameter for the numerical integrator.

**How to choose/use:** Use convergence checks for the actual state scaling/problem. Numerical tolerance is not the total orbit prediction error or an accuracy guarantee.

#### Relative tolerance — `integrator.relTol`

**Type:** number. **Unit:** none / see explanation. **Startup default:** `1e-13`.

**Visibility:** Both workflows.

Relative error-control parameter for the numerical integrator.

**How to choose/use:** Lower usually costs more computation; verify numerical convergence separately from model and measurement errors.

#### Max step — `integrator.maxStepSeconds`

**Type:** number. **Unit:** s. **Startup default:** `30`.

**Visibility:** Both workflows.

Largest allowed integration step.

**How to choose/use:** 30 s default keeps drag and eclipse transitions well resolved; 60-120 s is faster.

**Form bounds:** 1 to 600.

#### Min step — `integrator.minStepSeconds`

**Type:** number. **Unit:** s. **Startup default:** `0.001`.

**Visibility:** Both workflows.

Smallest allowed step.

**How to choose/use:** Leave at 1e-3 s.

**Form bounds:** 1e-06 to 10.

## Status, diagnostics and scientific interpretation

- **ISSUED / SUCCESS:** an output exists; this does not mean it meets the accuracy target.
- **FIT_REJECTED / OD_NOT_CONVERGED / FAILED_OD:** do not invent an ephemeris or count the case as zero error. The archive and live pipeline use different status vocabularies.
- **FAILED_VALIDATION / FAILED_SPP / CANCELLED / ERROR:** inspect saved errors/logs; a failed record may have no trajectory/OEM.
- **NO_DATA in Window:** inputs were unavailable/invalid for that cutoff; separate it from completed fitting failures.
- **Post-fit RMS:** fit of the observations within the estimation arc, not the future orbit error.
- **Variance factor:** checks assumed noise against residual scatter; model mismatch or maneuvers can also affect it.
- **Formal covariance / 1σ:** uncertainty under the assumed measurement and dynamics model, not guaranteed real forecast error.
- **PDOP:** geometric dilution; smaller is generally stronger geometry, but it is not a direct measured position error.
- **Reference error:** compare independent states at the same epoch, frame and units. Missing reference coverage means unscored.
- **Run-to-run difference:** separation from the selected base, not proof of which run is closer to truth.
- **Forecast vs reconstruction:** disclose issue-time evidence and future weather/EOP/attitude policy. A retrospective model comparison is not an as-issued prediction.

## Important distinctions visible in the GUI

The procedural spacecraft drawing selector changes only the visualization; Run → Spacecraft → Shape controls the force model. Earth-fixed/Inertial changes the globe view; Forecast & output → OEM reference frame controls exported coordinates. Output step controls stored sampling; Integrator min/max step controls internal numerical integration. Pseudorange sigma weights observations; post-fit RMS measures residual fit; neither is the measured seven-day prediction error.

The archived two-month OEMs have daily samples and no covariance. The separate short simulated Results example has dense samples and formal covariance to illustrate playback and ellipsoid controls. Do not present the simulated example’s small errors as achieved Sentinel-3A accuracy.

The built-in interactive guide contains historical narrative, including an old statement that no IGS products are in the repository. That statement is superseded by the actual current archive and Saved presentation. This manual uses source-level controls and current captures rather than repeating that old campaign claim.

## Completeness and reproducibility

The finite parameter set is taken from `src/main/resources/gui/params.json`; non-parameter interactions were inspected in index.html, app.js, data.js, run.js, results.js, windowview.js, history.js, compare.js, help.js, ui.js, presentation.js, globe.js, map2d.js, spacecraft.js and guide/index.html/guide.js. `capture-evidence.json` stores the visible control inventory and dropdown lists for each capture. `coverage-audit.json` records the machine-checkable counts and source hashes. Native browser/OS prompts and file dialogs are described in text; their appearance varies by platform.

No production settings, saved two-month results, or existing user workspace were changed to make the screenshots. Documentation scripts are included; recapturing populated Results needs an isolated saved example. Open README.html offline for search/navigation, or use the PDF/Word version for submission.
