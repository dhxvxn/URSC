# Uploaded GPS broadcast navigation

Original, compressed CDDIS downloads supplied by the user. No observation values or
navigation coefficients were modified. The manifest records SHA-256 hashes, file sizes,
GPS day, RINEX version, and independently counted GPS LNAV messages.

## Coverage

September 1–10, 2024: day-of-year 245–254, ten daily files (~15MB compressed).

| Date | DOY | GPS LNAV messages | GPS PRNs |
|---|---:|---:|---:|
| September 1 | 245 | 422 | 32 |
| September 2 | 246 | 417 | 32 |
| September 3 | 247 | 417 | 32 |
| September 4 | 248 | 355 | 32 |
| September 5 | 249 | 410 | 32 |
| September 6 | 250 | 426 | 32 |
| September 7 | 251 | 429 | 32 |
| September 8 | 252 | 430 | 32 |
| September 9 | 253 | 422 | 32 |
| September 10 | 254 | 429 | 32 |

For the requested August1–November1 range, August1–31 and September11–November1
remain missing (83days). These September navigation files do not match the November
GNSS observation uploads. Positioning still requires observation/navigation date overlap.

## Supported inputs

`RinexNavReader` accepts plain or gzip-compressed navigation files:

- **RINEX 3.x:** GPS broadcast ephemerides, including GPS records within mixed files.
- **RINEX 4.00–4.02:** `> EPH Gxx LNAV` records. The eight-line payload is decoded with
  the same GPS model as RINEX3. Records are separated by `>` boundaries.
- Non-GPS ephemerides, GPS CNAV/CNV2, ION, STO and EOP records are skipped in RINEX4.
  They are not converted into GPS LNAV or used in the current GPS-only SPP solver.
- RINEX2 and versions above4.02 fail with an explicit unsupported-version error.
- Truncated, mismatched-PRN, oversized, or nonfinite required GPS LNAV fields fail
  instead of creating a corrupted ephemeris. A file with no supported GPS LNAV fails.

Existing `spp --nav` automatically accepts these RINEX4 files; no manual conversion
or external package is needed. This is navigation compatibility, not RINEX4 observation
support, precise SP3/CLK support, or multi-constellation positioning.

## Test and verify

```sh
mvn -B -ntp package
```

`Rinex4NavReaderTest` checks RINEX3/4 equivalence of computed orbit/clock for a shared
GPS payload; interleaved unsupported records; malformed and unsupported input; and
all ten uploaded gzip files against the manifest's message counts and32PRNs per file.
Orbit norms and clocks are checked for finite, plausible values, not compared with
an independent precise orbit reference. These tests do not establish SPP or forecast accuracy.

Check original file integrity:

```sh
python3 - <<'PY'
import hashlib, json
from pathlib import Path
root = Path('data/gnss/navigation/2024')
for entry in json.loads((root / 'manifest.json').read_text())['files']:
    path = root / entry['file']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256'], path
print('All uploaded file hashes match.')
PY
```

Daily merged navigation products are retrospective archives. For strict as-issued
forecasting, transmission/availability must be established separately; a record's
orbit/clock reference epoch alone is not its publication time.
