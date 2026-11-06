# Uploaded GPS broadcast navigation

Original, compressed CDDIS downloads supplied by the user. No observation values or
navigation coefficients were modified. The manifest records SHA-256 hashes, file sizes,
GPS day, RINEX version, and independently counted GPS LNAV messages.

## Coverage

September 1–15, September 18–22 and September 24–28, 2024: twenty-five daily files.

| Date | DOY | GPS LNAV messages | GPS PRNs |
|---|---:|---:|---:|
| 2024-09-01 | 245 | 422 | 32 |
| 2024-09-02 | 246 | 417 | 32 |
| 2024-09-03 | 247 | 417 | 32 |
| 2024-09-04 | 248 | 355 | 32 |
| 2024-09-05 | 249 | 410 | 32 |
| 2024-09-06 | 250 | 426 | 32 |
| 2024-09-07 | 251 | 429 | 32 |
| 2024-09-08 | 252 | 430 | 32 |
| 2024-09-09 | 253 | 422 | 32 |
| 2024-09-10 | 254 | 429 | 32 |
| 2024-09-11 | 255 | 421 | 32 |
| 2024-09-12 | 256 | 426 | 32 |
| 2024-09-13 | 257 | 429 | 32 |
| 2024-09-14 | 258 | 422 | 32 |
| 2024-09-15 | 259 | 420 | 32 |
| 2024-09-18 | 262 | 442 | 32 |
| 2024-09-19 | 263 | 423 | 32 |
| 2024-09-20 | 264 | 426 | 32 |
| 2024-09-21 | 265 | 429 | 32 |
| 2024-09-22 | 266 | 420 | 32 |
| 2024-09-24 | 268 | 423 | 32 |
| 2024-09-25 | 269 | 420 | 32 |
| 2024-09-26 | 270 | 423 | 32 |
| 2024-09-27 | 271 | 429 | 32 |
| 2024-09-28 | 272 | 422 | 32 |

For August1–November1 inclusive, missing dates are August1–31, September16–17,
September23 and September29–November1 (68days). Positioning needs date-matched observations.

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
all twenty-five uploaded gzip files against the manifest's message counts and32PRNs per file.
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
