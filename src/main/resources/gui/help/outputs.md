# Outputs

## OEM file
Every trajectory is written as a **CCSDS Orbit Ephemeris Message, version 2.0, KVN** text file: header comments with provenance (mode, GPS products used and excluded, OD statistics, defaults used), a META block (object, centre EARTH, frame, UTC), position (km) and velocity (km/s) lines, and 6x6 covariance blocks (lower triangle, km-based units). The files have been checked with Orekit's CCSDS parser.

## Frames
- **EME2000**: inertial J2000 frame, the most widely read. Differs from GCRF by the frame bias (~23 milliarcseconds, ~1 m at LEO), which is applied.
- **GCRF**: inertial, ICRF-aligned.
- **ITRF**: Earth-fixed, realised with the supplied Earth orientation parameters.

## Covariance
The covariance is **formal**: the OD's uncertainty given the assumed measurement noise, propagated forward. It does not include errors in the drag model, solar activity or manoeuvres, so real errors can be larger. The OD variance factor shows whether the assumed pseudorange sigma matches the data (about 1 when it does).

## Run history
Each run is stored in the workspace under `runs/<id>/`: `config.json` (all settings, re-loadable), `result.json` (status, provenance, diagnostics), `output.oem`, `trajectory.json` (visualisation data) and `log.txt`. Settings can also be saved as named presets and exported to share.

## Comparing runs
In **History**, tick two or more runs and press **Compare**. The first ticked run is the base.
The comparison lists the settings that differ and plots each other run's position difference
to the base: 3D, and split into radial, along-track and cross-track. Runs must use the same
output frame. Epochs that do not coincide are interpolated.

## Sweeps
**Run → Sweep…** repeats the current case while varying one setting — for example the density
model, a perturbation switch, Cd or the OD arc length — and opens the comparison when all runs
finish. Use it to see how much a modelling choice moves the forecast. At most 12 runs.

## Manoeuvre and quality flags
After orbit determination URSC checks the residuals. A flag appears when they are much larger
than the assumed pseudorange noise (variance factor above 9), when they grow towards one end of
the arc, or when they jump abruptly. Typical causes: a manoeuvre inside the arc, bad
observations, or force settings unsuitable for the period. Flags are warnings: inspect the OD
residual plot, shorten the arc so it starts after the event, or move the cutoff.

## Ground-station passes
In **Results → Ground-station passes**, add stations by latitude, longitude, height and an
elevation mask. They are saved in the workspace and used for every run. The table lists
acquisition (AOS), loss (LOS), duration and maximum elevation; click a pass to jump the globe
to it. Passes cut by the start or end of the trajectory are marked.

## Uncertainty ellipsoid
With covariance enabled, tick **1σ ellipsoid** under the globe. The ellipsoid is enlarged by
the factor shown so it is visible at globe scale; its axes are radial, along-track and
cross-track. Along-track normally grows fastest.

## Report, KML and CZML
**Report** opens a printable page with the summary, flags, provenance, plots, passes and all
settings (non-default values highlighted); print it to PDF from the browser. **KML** opens in
Google Earth, **CZML** in Cesium. Both carry the time-tagged track; the OEM stays the
authoritative output.
