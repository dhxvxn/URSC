# IGS merged Broadcast navigation

62 original `BRDC00IGS_R_*_01D_MN.rnx.gz` files, daily coverage **September 2-November 2, 2024**, under `2024/`. These are merged multi-GNSS Broadcast messages, not precise IGS Final/Rapid/Ultra-Rapid orbit products.

[manifest.json](manifest.json) records every original filename, date, RINEX header version, byte size and SHA-256. Gzip integrity and source-copy checksums were checked before publication. Publication-time eligibility still needs verification for as-issued forecasting.

The existing 63 DLR `BRD400DLR_S` files remain under `../2024/`, covering September 1-November 2. All 62 overlapping downloaded DLR copies were byte-identical, so no duplicate DLR dataset was added. The IGS files are alternative inputs; this archive commit does not change the model's input selection or claim a new accuracy result.
