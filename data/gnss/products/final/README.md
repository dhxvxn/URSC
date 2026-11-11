# Archived final GNSS products

174 original compressed files. The [archive manifest](archive-manifest.json) records paths, nominal dates, providers, streams, byte sizes and SHA-256 hashes.

| Provider | Stream | Product type | Files |
|---|---|---|---:|
| COD | MGX | ATT.OBX | 3 |
| COD | MGX | CLK.CLK | 3 |
| COD | MGX | ERP.ERP | 4 |
| COD | MGX | ORB.SP3 | 4 |
| COD | MGX | OSB.BIA | 4 |
| COD | OPS | CLK.CLK | 66 |
| COD | OPS | ERP.ERP | 1 |
| COD | OPS | ORB.SP3 | 62 |
| COD | OPS | OSB.BIA | 1 |
| COD | OPS | SOL.SNX | 1 |
| COD | OPS | SUM.SUM | 1 |
| COD | OPS | TRO.TRO | 2 |
| EMR | OPS | CLK.CLK | 6 |
| ESA | OPS | CLK.CLK | 7 |
| ESA | OPS | ORB.SP3 | 5 |
| GFZ | OPS | CLK.CLK | 2 |
| GFZ | OPS | ORB.SP3 | 2 |

CODE OPS coverage includes the September 2-November 2, 2024 campaign. Other providers/streams are partial uploaded collections; use the manifest for exact dates. Do not mix providers or orbit/clock conventions silently. Final/Rapid products can contain post-cutoff information; Ultra-Rapid observed/predicted segments and publication vintage must be checked for forecasting.

GNSS ATT.OBX products describe GNSS satellite attitude, not Sentinel-3A attitude or maneuver execution. These files are archived for future use; this commit does not change product selection or claim a new model test.
