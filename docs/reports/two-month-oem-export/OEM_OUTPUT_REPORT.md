# Sentinel-3A two-month OEM output report

## Purpose and scope

This report explains the archived orbit predictions exported as CCSDS Orbit
Ephemeris Messages (OEM). The export preserves the completed experimental results;
it does not run new orbit determination, improve the model, reconstruct missing
forecasts, or substitute reference orbit positions for predictions.

**The files contain daily benchmark samples, not dense orbit tracks.** They are
appropriate for reviewing the saved one-day through seven-day predictions. Their
24-hour spacing cannot resolve Sentinel-3A's roughly 100-minute orbit. Do not
interpolate them for tracking, passes, continuous ground tracks or operational use.
A dense ephemeris requires another propagation/export at a suitable cadence.

## Which file should be presented?

| File or folder | Meaning |
|---|---|
| [improved-broadcast-all-issued.oem](improved-broadcast-all-issued.oem) | The newer nominal Broadcast-only estimation trial: 60 independent forecasts. Start with this file when presenting the improved Broadcast experiment. |
| [original-four-tier-all-issued.oem](original-four-tier-all-issued.oem) | Original comparison using Final, Rapid, Ultra-Rapid and Broadcast GPS products: 205 forecasts, with product tier in each segment's comments. |
| [retrospective-old-maneuvers-all-issued.oem](retrospective-old-maneuvers-all-issued.oem) | 60 replays using the newly estimated state and the original candidate maneuver reconstruction. Diagnostic and retrospective. |
| [retrospective-corrected-maneuvers-all-issued.oem](retrospective-corrected-maneuvers-all-issued.oem) | 60 replays using background-corrected candidate maneuvers. Diagnostic and retrospective. |
| Group folders | One OEM per issued run, suitable for readers expecting a single segment. |
| [coverage.csv](coverage.csv) | Every attempted case, including rejected cases with no OEM. |
| [manifest.json](manifest.json) | Output hashes and the paths/hashes of the original CSV sources. |

The two maneuver files are separate experimental profiles. They are not corrections
that should automatically replace all nominal predictions. Their future,
reference-derived maneuver information was unavailable at the original cutoff.
No per-date best-performing profile was chosen to construct this package.

## Time window and completeness

- Observation campaign daily forecast cutoffs: **3 September–2 November 2024**,
  inclusive: 61 dates at **05:59:42 UTC**.
- Each issued run has a seven-day horizon and eight state samples: elapsed days
  0, 1, 2, 3, 4, 5, 6 and 7.
- The first forecast spans 3–10 September; the last spans 2–9 November.
- Overall exported sample epochs span **3 September–9 November 2024**.
- These are rolling forecasts; they are not one unbroken two-month propagation.

| Experiment | Attempted cases | Issued OEMs | Not issued |
|---|---:|---:|---:|
| Original four-tier comparison | 244 | 205 | 39 |
| Improved Broadcast | 61 | 60 | 1 |
| Replay: original candidate maneuvers | 61 | 60 | 1 |
| Replay: background-corrected maneuvers | 61 | 60 | 1 |
| Total | 427 | 385 | 42 |

The improved Broadcast run for **16 October 2024** failed the fit-quality gate; its
replay profiles also have no issued output. All original comparison rejections
remain visible in the coverage table. A missing OEM is a rejected/unissued result,
not zero error. Availability of a prediction through 9 November does not imply
reference data exists to score every late prediction endpoint.

## OEM format and structure

The output is **CCSDS OEM version 2.0**, **KVN** (Keyword = Value Notation), stored
as ASCII text. OEM is a standard way to exchange sampled orbital position and
velocity. It is not a TLE, a raw GPS observation file, a broadcast navigation
message, a force-model configuration, or an error report.

A file has a header and one or more metadata/data segments. A representative
single-run extract is:

```text
CCSDS_OEM_VERS = 2.0
CREATION_DATE = 2026-11-12T04:11:44
ORIGINATOR = URSC

COMMENT Campaign: improved-broadcast; case: 2024-09-03-broadcast
COMMENT UTC cutoff: 2024-09-03T05:59:42.000
META_START
OBJECT_NAME = SENTINEL-3A
OBJECT_ID = 2016-011A
CENTER_NAME = EARTH
REF_FRAME = ITRF
TIME_SYSTEM = UTC
START_TIME = 2024-09-03T05:59:42.000
STOP_TIME = 2024-09-10T05:59:42.000
META_STOP
COMMENT Position km; velocity km/s. No interpolation method asserted.
2024-09-03T05:59:42.000 2991.737690361976 5993.733816487692 2585.138768381534 2.696252010539 1.624476786231 -6.858906667896
```

The extract omits additional provenance comments and the remaining seven data
lines for readability. The actual files preserve all those comments and samples.

### Header and metadata fields

| Field | Explanation |
|---|---|
| `CCSDS_OEM_VERS` | OEM format version, 2.0. |
| `CREATION_DATE` | UTC time of this export, not the observation date, forecast cutoff or original experiment execution time. A 2026 export can describe a 2024 historical experiment. |
| `ORIGINATOR` | Producing project: URSC. |
| `COMMENT` | Human-readable provenance, profile, cutoff and caveats; not another state sample. Preserve these when sharing files. |
| `META_START` / `META_STOP` | Delimit metadata for one independent segment. The state lines follow `META_STOP`. |
| `OBJECT_NAME` | Satellite being predicted: SENTINEL-3A. |
| `OBJECT_ID` | International designator: 2016-011A. |
| `CENTER_NAME` | Earth is the coordinate origin. |
| `REF_FRAME` | ITRF, an Earth-fixed rotating terrestrial frame. This export retains the archived frame rather than transforming again. It does not add a claim of a particular ITRF realization. |
| `TIME_SYSTEM` | UTC applies to state epochs and segment bounds. These timestamps do not use GPS time. |
| `START_TIME` / `STOP_TIME` | First and last sample in that segment, not the bounds of the whole input archive. |

No `INTERPOLATION` or `INTERPOLATION_DEGREE` is asserted: daily samples must not be
made to look like a continuous high-fidelity orbit. No covariance blocks are
present because the source campaign CSVs did not store covariance.

### Reading a state line

Each state line contains seven whitespace-separated fields:

```text
epoch_UTC  X_km  Y_km  Z_km  VX_km/s  VY_km/s  VZ_km/s
```

For the illustrated state:

| Component | Value | Meaning |
|---|---:|---|
| Epoch | 2024-09-03 05:59:42 UTC | Day-zero cutoff state for this forecast. |
| X | 2991.737690361976 km | Earth-fixed Cartesian X position. |
| Y | 5993.733816487692 km | Earth-fixed Cartesian Y position. |
| Z | 2585.138768381534 km | Earth-fixed Cartesian Z position. |
| VX | 2.696252010539 km/s | X velocity expressed consistently in the rotating frame. |
| VY | 1.624476786231 km/s | Y velocity expressed consistently in the rotating frame. |
| VZ | -6.858906667896 km/s | Z velocity expressed consistently in the rotating frame. |

X points toward the equator at the Greenwich meridian, Y toward the equator at
90 degrees east, and Z toward the terrestrial north pole. These Cartesian
coordinates are not latitude, longitude and altitude. Position norm is distance
from Earth's centre, not height above the ellipsoid. A negative component means
the vector points opposite that axis; it is not an error or a negative altitude.

ITRF velocity must not be treated as inertial velocity. Converting position and
velocity to GCRF requires the appropriate epoch-dependent Earth-orientation
transform, including the rotating-frame velocity term. The exporter copies the
existing ITRF position and velocity and does not perform a second transformation.

The source CSV uses metres and metres per second. Every component is divided by
1000 for OEM. For example, 2,991,737.690361976 m becomes 2991.737690361976 km.
Twelve fractional digits preserve the stored decimal values; they do not imply
picometre physical accuracy.

## Why combined files overlap

Each successful cutoff is an independently initialized seven-day prediction. The
4 September segment overlaps the 3 September segment over most of their horizons.
Two positions at the same UTC can therefore differ: they belong to different
forecast issue dates, horizons or product profiles.

The combined file contains separate metadata/data segments and preserves each
forecast. It does not average, overwrite or concatenate their state lines into
one chronological track. Some OEM consumers cannot accept overlapping segments;
use the individual files with those tools. Within each segment, epochs increase
strictly and have 86,400-second spacing.

For accuracy assessment, compare a sample to the independent reference at the
same UTC, in the same frame and units, and keep its forecast issue date. Day one
means one day after that segment's cutoff; day seven means seven days after it.
Do not compare a day-one state from one segment with a day-seven state from another
and call them the same forecast horizon.

## Forecast provenance and interpretation

The nominal workflow uses pre-cutoff GNSS observations to form position fixes,
fits an initial orbit, and propagates it with the experiment's force assumptions.
The stored future weather/EOP policy is retained by this export. It does not inject
new measured future weather, precise reference states or new attitude information.

However, the original Final and Rapid GPS products were retrospective products
and may incorporate post-cutoff information. Ultra-Rapid publication eligibility
was not verified. The archived experiments do not establish publication-vintage
eligibility for every input, including the Broadcast trial. Consequently these
exports must not be presented as a fully verified operational as-issued forecast
campaign. The forecast/reconstruction distinction survives the format conversion.

The maneuver replays use unconfirmed events inferred from future reference orbits.
They measure a retrospective compensation experiment; they do not demonstrate
that those events could have been forecast or that official maneuver telemetry
was available. All such files are labelled `retrospective` in names and comments.

OEM values alone do not establish the one-day 10 m or seven-day 1 km targets.
Errors need independent reference comparisons; the absence of covariance is also
not a claim of zero uncertainty. Previous performance reports remain the source
for scored accuracy, and late endpoints without reference coverage remain unscored.

## Validation performed

1. Exported all 385 issued source CSVs; kept all 427 attempts in the coverage table.
2. Checked eight samples per issued forecast, finite state values, increasing
   epochs, 86,400-second spacing, frame and bounds.
3. Compared all six state components with their source using exact decimal
   arithmetic after reversing the unit conversion. No value was altered.
4. Parsed all 389 OEM files with the project's existing Java OEM reader: 385
   individual files plus four aggregate files.
5. Recorded source/output SHA-256 hashes and verified the ZIP archive integrity.

There are **3,080 unique sampled state records**. Aggregates duplicate the
individual records, so parsing all files counts 6,160 records and 770 segments.
These checks establish export fidelity and project-parser compatibility. They
are not an independent external CCSDS conformance certification, a physical
accuracy validation, or a certification for operational use.

## Package and reproducibility

[Download the complete package](TWO_MONTH_OEM_PACKAGE.zip). It includes this report,
the OEMs, coverage, provenance manifest, verification summary and checksums.
The Markdown report is intended for GitHub reading; the companion PDF is intended
for submission and printing.

`export_oem.py` converts the archived campaign CSVs when those source directories
are available. It does not acquire missing observations or regenerate experiments.
Some source directories may exist only in the research workspace; the manifest
records exact CSV hashes so source identity remains checkable. Downloaded OEMs
can be read without those source files or a Java installation.

When sharing with the guide, include this report and the profile being discussed.
For a new dense run, specify the cutoff campaign, model profile, sampling interval,
reference frame and forecast policy before generating another output set.
