# Sentinel-3A two-month campaign: OEM outputs

This package exports the completed archived experiments. It does not rerun orbit
determination, alter the physics, improve accuracy, or fill rejected dates.

See [OEM_OUTPUT_REPORT.md](OEM_OUTPUT_REPORT.md) for the field-by-field explanation,
worked state example, coordinate/time conventions, and interpretation guidance.

## Window and contents

- Daily forecast cutoffs: **3 September–2 November 2024**, 61 dates inclusive.
- Each cutoff: **05:59:42 UTC**; each issued forecast extends seven days.
- Overall prediction sample range: **3 September–9 November 2024**.
- Satellite: Sentinel-3A, international designator 2016-011A.
- CCSDS OEM 2.0 KVN; Earth-centred ITRF; UTC; position km, velocity km/s.
- Eight samples per forecast: cutoff, then daily through day seven.

| Group | Attempts | OEM forecasts | Not issued |
|---|---:|---:|---:|
| Original Final/Rapid/Ultra-Rapid/Broadcast comparison | 244 | 205 | 39 |
| Improved Broadcast nominal prediction | 61 | 60 | 1 |
| Retrospective replay with original candidate maneuvers | 61 | 60 | 1 |
| Retrospective replay with background-corrected maneuvers | 61 | 60 | 1 |

Use **improved-broadcast-all-issued.oem** for the newer nominal Broadcast trial.
Use **original-four-tier-all-issued.oem** for the original product comparison.
The two files starting **retrospective-** are diagnostic replays, not forecasts
that could have been issued at the cutoff.

Each combined OEM contains independent metadata/data segments, one per successful
run. The segments overlap in time. They are not one stitched two-month trajectory;
consumers requiring a single monotonic segment should use individual files.
Within the original comparison, product tiers are identified in segment comments.
No profile is selected per date according to its later observed accuracy.

## Important limits for presentation

**These are daily benchmark samples, not dense orbit tracks.** One-day spacing is
far longer than a Sentinel-3A orbital period. Do not interpolate them for orbital
visualisation, tracking, passes or operations. No interpolation method is asserted.
Dense ephemerides require another propagation/export run at an appropriate cadence;
the present package never invents intermediate states.

Final and Rapid GNSS products were used retrospectively and can contain information
produced after the forecast cutoff. Ultra-Rapid publication eligibility was not
verified. Original past-only weather and EOP assumptions are retained, but product
publication vintages were not verified, including for the Broadcast trial. Therefore
this package must not be described as a fully certified, as-issued forecast campaign.
Future Sentinel-3A reference positions were used for scoring, not nominal prediction.
Maneuver replays additionally use future reference-derived, unconfirmed events.

The improved Broadcast trial rejects **16 October 2024** for fit quality. It has no
OEM on that date. The original comparison has 39 rejected/nonconverged cases, listed
individually in coverage.csv. Rejected cases are not replaced with reference orbits.
Formal covariance is not present in the source CSV and is not fabricated here.
Reference coverage does not reach all late seven-day prediction endpoints: predictions
through 9 November exist, but those final endpoints are not all independently scored.

## Files and verification

- Four `*-all-issued.oem` files: combined exports for convenient presentation.
- Group folders: 385 individual run OEMs.
- `coverage.csv`: all 427 attempted cases, including missing/rejected outputs.
- `manifest.json`: source CSV hashes, output hashes and group counts.
- `SHA256SUMS`: integrity hashes for the package files.
- `export_oem.py`: conversion source; requires the archived results in this repository.
- `verification.json`: conversion and parser checks.

Every state value was compared to its source CSV using exact decimal arithmetic,
including the m-to-km and m/s-to-km/s conversion. All 389 OEM files were parsed by
the existing Java Oem reader: 385 individual forecasts plus four aggregate files.
The aggregates duplicate the individual data; the package contains 3,080 unique
sampled states, not 6,160 independent states. This verification checks export
fidelity and parser compatibility, not independent physical accuracy or external
CCSDS conformance certification.
