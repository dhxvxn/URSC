# Using orekit-data data & models in URSC

URSC can consume the public data and gravity models distributed with
[orekit-data](https://gitlab.orekit.org/orekit/orekit-data). This cloud environment has
**no outbound access to gitlab.orekit.org** (the proxy returns HTTP 403), so the files are
brought in by **uploading** them (the same way `SpaceWeather-All-v1.2.txt` was supplied).
Once uploaded, they plug straight into the commands via the flags below — no code changes.

## What you can supply, and what each buys

| orekit-data file | flag | what it improves | reader |
|---|---|---|---|
| `finals2000A.all` (IERS EOP, IAU 2000A) | `--eop-file` | real **UT1-UTC + polar motion** in the GCRF↔ITRF transform (~a few metres of frame accuracy; zero polar motion otherwise) | `Finals2000AEopProvider` |
| `eopc04_IAU2000.62-now` (IERS EOP C04) | `--eop-file` | same as above, C04 layout (auto-detected) | `IersEopProvider` |
| a gravity model `.gfc` (EIGEN-6S, EGM2008, …) | `--gravity-file` | swap the bundled **EGM96** for a higher-fidelity field | `GravityFieldFactory.loadIcgem` |
| `SpaceWeather-All-v1.2.txt` (CSSI) | `--sw-file` | real F10.7 / Ap for NRLMSISE-00 drag (already in use) | `CssiTxtSpaceWeatherProvider` |

`--eop-file` auto-detects the finals2000A vs. C04 layout. Dates outside the uploaded file
fall back to the POEORB's own UT1 with zero polar motion, so a run is never worse than before.

## Example

```sh
leoprop validate <poeorb.eof> --sw-file SpaceWeather-All-v1.2.txt \
    --eop-file finals2000A.all --gravity-file EIGEN-6S.gfc

leoprop forecast <seed.eof> --days 7 --sw-file SpaceWeather-All-v1.2.txt --eop-file finals2000A.all
leoprop fit      <poeorb.eof> --estimate-cd --eop-file finals2000A.all
leoprop backtest <train.eof> <truth.eof> --sw-file SW.csv --eop-file finals2000A.all
```

The flags are available on `validate`, `fit`, `forecast`, and `backtest`.

## Measured effect (polar motion)

A quick `validate` on the Oct-7 S3A POEORB (2 h arc) with a finals2000A EOP file applied:

| EOP | RMS position error |
|---|---:|
| POEORB UT1, **zero polar motion** (default) | 16.7 m |
| real UT1 + **polar motion** (`--eop-file`) | **13.7 m** |

(~18 % lower; the real IERS `finals2000A.all` values will do at least as well.)

## Which files to upload (recommended)

1. **`finals2000A.all`** — the biggest single win; put it in for every POEORB run.
2. A gravity model `.gfc` only if you want to go beyond EGM96 (e.g. `EIGEN-6S.gfc` for a
   modern field); EGM96 to degree 70 is bundled and already more than enough for LEO.
3. Planetary ephemerides (DE440) and leap-second files are **not** needed — URSC computes
   Sun/Moon analytically and has leap seconds built in.

> Download these on a machine with internet from `https://gitlab.orekit.org/orekit/orekit-data`
> (the `finals2000A.all` and the gravity-model `.gfc` live at the repo root), then upload them here.
