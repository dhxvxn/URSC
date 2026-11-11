# Archived project inputs

Original bytes are retained. [The central checksum manifest](input-manifest.json) records newly archived inputs; DLR navigation has its own existing manifest. [The archive audit](all-input-archive-manifest.json) records aliases and retained same-name variants. [Published verification](ARCHIVE_VERIFICATION.json) records the checked data revision and category counts.

| Input category | Location | Files / coverage |
|---|---|---|
| DLR Broadcast navigation | [gnss/navigation/](gnss/navigation/README.md) | 63 daily files; September 1-November 2, 2024 |
| IGS merged Broadcast navigation | [gnss/navigation/IGS/](gnss/navigation/IGS/README.md) | 62 daily files; September 2-November 2, 2024 |
| Final GNSS products | [gnss/products/final/](gnss/products/final/README.md) | 174 files; CODE OPS campaign plus partial uploaded providers; exact dates in manifest |
| Rapid GNSS products | [gnss/products/rapid/](gnss/products/rapid/README.md) | 143 files; CODE OPS campaign plus partial uploaded providers; exact dates in manifest |
| Ultra-Rapid GNSS products | [gnss/products/ultra-rapid/](gnss/products/ultra-rapid/README.md) | 289 files; CODE OPS campaign plus partial uploaded providers; exact dates in manifest |
| Sentinel GNSSRD observations | `gnss/observations/2024/` | 61 Sentinel-3A + 1 Sentinel-3B archives; filenames/validity intervals in manifest |
| Sentinel PROQUA auxiliaries | `attitude/2024/` | 63 unique archives; uploaded September-November 2024 collection |
| Sentinel POEORB references | `orbits/2024/` | 63 files; archived reference arcs and fixtures |
| Weather, Earth orientation and gravity | [Gravity/EOP](../docs/project-journal/inputs/) and [weather/EOP](../docs/reports/perturbation-ladder-truestate-7day/inputs/) | Existing original inputs retained; checksums verified |
| JPL DE440 ephemeris | `ephemeris/` | Existing binary retained |
| Historical daily seed states | `seed-states/` | Existing August-October first-OSV states retained |
| Unconfirmed partial downloads | [unconfirmed-downloads/](unconfirmed-downloads/README.md) | Backup only; excluded from model input coverage |

## Availability and use

Archiving does not change the forecast cutoff rule. Measurements and plans must be available by cutoff. Later Final/Rapid products and reconstructed maneuvers belong in explicitly retrospective comparisons. POEORB is reference data and must not supply future spacecraft states to nominal forecasts.

The original August-October full POEORB collection is not available in this workspace; its daily seed states remain archived. Available September-November reference arcs are retained, but November 5-9, 2024 endpoints remain missing. See manifests for exact coverage; no completeness claim is made for unavailable files.

No credentials, authentication sessions, generated forecasts or unrelated uncommitted implementation changes are included. Every complete newly added gzip passed decompression/CRC validation; partial downloads are separately quarantined.
