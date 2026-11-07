# Reusable input data

Raw uploaded files are retained byte-for-byte, compressed where originally supplied.
`input-manifest.json` lists newly archived input files, sizes, SHA-256 hashes, roles,
and duplicate aliases. Navigation has its own detailed manifest under
`gnss/navigation/2024/manifest.json`.

| Input | Location | Coverage |
|---|---|---|
| GPS broadcast navigation | `gnss/navigation/2024/` |56consecutive days:September1–October26,2024 |
| Sentinel-3A GNSS observations | `gnss/observations/2024/` | GPS September4–October30 and November1–2,2024;59unique archives |
| Sentinel-3B GNSS observations | `gnss/observations/2024/` | October15–16,2024; one GNSSRD archive |
| Sentinel-3A measured attitude | `attitude/2024/` | daily September17–October2 (15 files) and October15–16,2024; 16 PROQUA archives |
| Sentinel-3A precise orbit (POEORB) | `orbits/2024/` | October1–2,2024 (Oct7–8 and Oct15–16 remain under the report inputs) |
| Sentinel-3B precise orbit (POEORB) | `orbits/2024/` | October15–16,2024 |
| Sentinel-3A two-month seed states | `seed-states/` | 62 daily first-OSV ITRF states, August1–October1,2024 |
| JPL DE440 ephemeris | `ephemeris/` | Sun/Moon/planet binary ephemeris (environment input) |
| August initial/reference states | `../docs/reports/august-2024-holdout/inputs/` | August1 seed, August2/8 reference endpoints |
| Daily seed/reference pairs | `../docs/reports/one-day-multi-epoch-audit/` | Four cases: August1/7 and October7/15 |
| Numerical reference fixture | `../src/test/resources/numerics/j2-dop853-reference.csv` | Synthetic Cartesian J2/DOP853 reference, not satellite truth |

The older POEORB files, weather, and EOP inputs remain at
`docs/reports/perturbation-ladder-truestate-7day/inputs/`.
Gravity/EOP inputs also remain at `docs/project-journal/inputs/`.
The full original August–October two-month POEORB *file* set is not present, but the
62 daily first-OSV ITRF seed states derived from it are archived at
`seed-states/first_osv_blocks_2024-08-01_to_10-01.txt` — the exact per-day seeds used by
the two-month study.

## Times and evaluation

GNSSRD and PROQUA epochs use GPS time. GPS−UTC=18s in2024. Archive validity dates
are UTC, explaining midnight-adjacent filename boundaries. Observation archives are
RINEX3.03; navigation archives are RINEX4.02 (GPS LNAV supported by the reader).
Sentinel POEORB and supplied orbit-state fixtures are Earth-fixed ITRF states.

November navigation is not included yet. September4–October26 now have observation/navigation overlap. A November SPP/OD run requires matching November navigation.
POEORB/reference states are for evaluation and must not be used to influence a
forecast after its cutoff. These products are retrospective; publication-time
availability must be established separately for operational replay.

Original archive duplicates and copied fixture duplicates were omitted; the manifest
identifies aliases. Generated predictions, build artifacts, reports, and unrelated
uncommitted implementation changes are not part of this input-data commit.

## Verify the newly archived inputs

From the repository root:

```sh
python3 - <<'PY'
from pathlib import Path
import hashlib, json
for item in json.loads(Path('data/input-manifest.json').read_text())['new_input_files']:
    p = Path(item['path'])
    assert p.stat().st_size == item['bytes'], p
    assert hashlib.sha256(p.read_bytes()).hexdigest() == item['sha256'], p
print('All archived input hashes match.')
PY
```

See [the GNSS observation inventory](gnss/observations/README.md) for exact dates.
See [the merged architecture reference](../docs/MERGED_ARCHITECTURE.md) for the agreed structure.
