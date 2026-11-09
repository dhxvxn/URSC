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
