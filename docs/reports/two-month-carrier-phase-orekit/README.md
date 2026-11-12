# Two months of Sentinel-3A: SPP fit vs carrier phase vs carrier phase on Orekit

**Campaign.**
* 61 daily cut-offs at 05:59:42 UTC, from 3 September to 2 November 2024.
* The GUI's Sentinel-3A showcase settings: 6 h arc, 7-day forecast, EGM96 70×70, NRLMSISE-00
  drag with cut-off-safe space weather.
* Input files picked per day by the data catalogue; broadcast products in cut-off-safe forecast
  mode.

Every error is the 3-D position difference against the ESA POEORB precise orbit. POEORB is used
for scoring only. Two cut-offs have no POEORB coverage and are not scored; five have none at +7 days.

| Label | Engine | Orbit determination |
|---|---|---|
| **Before** | previous in-house physics | batch fit to single-point (SPP) position fixes |
| **After** | previous in-house physics | carrier-phase batch fit (undifferenced iono-free code + phase) |
| **Final** | **Orekit 13.1.2** (the program as it is now) | Orekit `BatchLSEstimator` on `OneWayGNSSRange` + `OneWayGNSSPhase` |

| Run | Days | Successful | Median fit RMS | Median run time |
|---|---:|---:|---|---:|
| Before: SPP position fit (previous engine) | 61 | 61 | 2.702 m (positions) | 135 s |
| After: carrier-phase OD (in-house engine) | 61 | 61 | 0.069 m (carrier phase) | 191 s |
| Final: carrier-phase OD on the Orekit engine | 61 | 61 | 0.072 m (carrier phase) | 280 s |

**3-D position error against POEORB (m), median over the scored days** (95th percentile in brackets):

| Horizon | Before | After | Final | Change (final vs before) |
|---|---:|---:|---:|---:|
| At cut-off | 1.57 (3.21) | 0.78 (2.15) | 0.89 (2.22) | -44 % |
| +1 h | 1.93 (4.48) | 1.26 (4.52) | 1.43 (4.68) | -26 % |
| +6 h | 9.62 (712) | 8.80 (713) | 8.61 (714) | -10 % |
| +1 day | 94.91 (4,211) | 92.74 (4,212) | 93.06 (4,213) | -2 % |
| +3 days | 1,023 (16,355) | 1,020 (16,361) | 1,000 (16,404) | -2 % |
| +7 days | 8,208 (42,008) | 8,205 (42,014) | 8,131 (42,118) | -1 % |

## Reading the results

* **Carrier phase roughly halves the orbit error at the cut-off.**
  * Median at the cut-off: 1.57 m before, 0.78–0.89 m with carrier phase.
  * Median at +1 h: 1.93 m before, 1.26–1.43 m with carrier phase.
  * The fit to the measurements goes from 2.7 m (positions) to about 7 cm (phase).
* **Beyond a few hours the forecast is limited by atmospheric drag, not by the orbit
  determination.**
  * The +1-day and +7-day errors are the same for all three runs (within 1–2 %).
  * September 2024 was near solar maximum. The 7-day errors of several kilometres come from
    forecasting the thermospheric density with persisted space weather.
  * The few very large 1-day errors (up to 9 km) are days with geomagnetic storms after the
    cut-off.
* **The Orekit engine reproduces the in-house carrier-phase results.**
  * Medians agree within 0.1–0.2 m over the arc and within 1–2 % in the forecast.
  * Small differences are expected: different force-model implementations (Orekit's NRLMSISE-00,
    tides, albedo) and full IERS 2010 frames.
* All 61 cut-offs produced a forecast in every run.
* Run time per cut-off: 135 s (before), 191 s (after), 280 s (Orekit, with the four campaign
  processes sharing four cores).

## Daily results (m)

| Cut-off (2024) | Before: cut-off | After: cut-off | Orekit: cut-off | Before: +1 day | After: +1 day | Orekit: +1 day | Before: +7 days | After: +7 days | Orekit: +7 days | Orekit phase RMS |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 09-03 | 0.61 | 0.68 | 0.93 | 70.57 | 68.86 | 68.98 | 1,880 | 1,885 | 1,896 | 5.9 cm |
| 09-04 | 1.15 | 0.46 | 0.57 | 96.82 | 96.09 | 97.72 | 8,525 | 8,523 | 8,542 | 6.7 cm |
| 09-05 | 2.81 | 1.74 | 1.93 | 78.08 | 77.74 | 77.09 | 16,749 | 16,747 | 16,756 | 6.0 cm |
| 09-06 | 1.53 | 0.37 | 0.42 | 85.20 | 84.33 | 81.93 | 21,581 | 21,581 | 21,592 | 6.4 cm |
| 09-07 | 0.82 | 0.33 | 0.50 | 92.03 | 92.32 | 92.46 | 28,443 | 28,434 | 28,468 | 6.6 cm |
| 09-08 | 2.62 | 1.56 | 1.66 | 150 | 148 | 149 | 32,796 | 32,807 | 32,837 | 6.7 cm |
| 09-09 | 3.19 | 2.01 | 2.18 | 5,842 | 5,845 | 5,847 | 39,798 | 39,811 | 39,830 | 6.4 cm |
| 09-10 | 1.85 | 1.33 | 1.24 | 105 | 107 | 105 | 6,056 | 6,076 | 6,054 | 8.0 cm |
| 09-11 | 0.99 | 0.53 | 0.73 | 94.91 | 92.74 | 93.06 | 7,014 | 7,007 | 6,968 | 6.3 cm |
| 09-12 | 1.49 | 0.50 | 0.59 | 361 | 357 | 357 | 4,067 | 4,052 | 4,009 | 7.1 cm |
| 09-13 | 4.15 | 3.00 | 3.28 | 197 | 195 | 193 | 5,341 | 5,352 | 5,372 | 9.6 cm |
| 09-14 | 1.72 | 0.51 | 0.46 | 77.84 | 76.30 | 73.98 | 11,918 | 11,925 | 11,951 | 6.7 cm |
| 09-15 | 0.47 | 0.78 | 0.96 | 80.53 | 80.05 | 80.70 | 17,615 | 17,611 | 17,649 | 6.9 cm |
| 09-16 | 1.95 | 0.87 | 1.00 | 102 | 100 | 101 | 24,243 | 24,248 | 24,282 | 6.4 cm |
| 09-17 | 3.32 | 2.12 | 2.19 | 210 | 209 | 206 | 30,938 | 30,943 | 30,966 | 7.1 cm |
| 09-18 | 0.84 | 0.34 | 0.55 | 2,857 | 2,858 | 2,860 | 38,571 | 38,572 | 38,604 | 6.9 cm |
| 09-19 | 0.86 | 0.56 | 0.73 | 48.88 | 47.38 | 48.10 | 1,148 | 1,144 | 1,097 | 7.2 cm |
| 09-20 | 2.09 | 0.88 | 1.09 | 9.00 | 7.50 | 8.50 | 1,466 | 1,461 | 1,423 | 6.4 cm |
| 09-21 | 1.87 | 0.58 | 0.74 | 4.77 | 4.32 | 3.22 | 2,856 | 2,858 | 2,830 | 6.3 cm |
| 09-22 | 0.40 | 0.54 | 0.74 | 4.22 | 3.71 | 3.49 | 3,611 | 3,616 | 3,569 | 6.8 cm |
| 09-23 | 0.45 | 0.91 | 1.07 | 44.28 | 42.88 | 43.23 | 4,470 | 4,470 | 4,406 | 7.2 cm |
| 09-24 | 2.60 | 0.87 | 1.00 | 79.03 | 76.20 | 75.57 | 5,146 | 5,131 | 5,073 | 6.3 cm |
| 09-25 | 1.90 | 0.72 | 0.75 | 133 | 131 | 127 | 5,526 | 5,520 | 5,465 | 7.3 cm |
| 09-26 | 1.43 | 0.40 | 0.26 | 81.87 | 81.08 | 78.58 | 5,412 | 5,414 | 5,342 | 7.8 cm |
| 09-27 | 0.98 | 0.40 | 0.51 | 85.28 | 84.11 | 84.16 | 5,923 | 5,920 | 5,832 | 6.2 cm |
| 09-28 | 2.29 | 1.11 | 1.24 | 84.82 | 82.56 | 80.95 | 6,973 | 6,966 | 6,883 | 6.8 cm |
| 09-29 | 1.70 | 0.83 | 0.88 | 126 | 126 | 122 | 7,754 | 7,763 | 7,676 | 7.2 cm |
| 09-30 | 1.65 | 1.14 | 0.90 | 144 | 144 | 142 | 1,090 | 1,083 | 1,191 | 10.2 cm |
| 10-01 | 2.44 | 1.24 | 1.25 | 139 | 139 | 138 | — | — | — | 8.6 cm |
| 10-02 | 3.07 | 2.02 | 2.12 | 99.20 | 97.11 | 93.10 | 22,941 | 22,950 | 23,111 | 7.3 cm |
| 10-03 | 1.06 | 0.41 | 0.52 | 78.52 | 77.98 | 70.74 | 34,118 | 34,113 | 34,311 | 7.3 cm |
| 10-04 | 1.07 | 0.40 | 0.46 | 99.94 | 99.60 | 93.59 | 46,112 | 46,107 | 46,368 | 8.6 cm |
| 10-05 | 2.58 | 0.93 | 0.99 | 83.40 | 80.76 | 77.56 | 54,525 | 54,536 | 54,776 | 6.9 cm |
| 10-06 | 1.70 | 0.37 | 0.48 | 9,236 | 9,236 | 9,242 | 62,697 | 62,692 | 62,908 | 6.5 cm |
| 10-07 | 2.47 | 1.41 | 1.26 | — | — | — | 8,826 | 8,819 | 8,612 | 9.1 cm |
| 10-08 | — | — | — | 221 | 218 | 213 | 5,245 | 5,235 | 4,994 | 9.9 cm |
| 10-09 | 1.97 | 0.70 | 0.89 | 71.64 | 71.26 | 69.81 | — | — | — | 6.9 cm |
| 10-10 | 0.97 | 2.47 | 2.49 | 262 | 260 | 255 | 7,891 | 7,886 | 7,721 | 10.4 cm |
| 10-11 | 8.12 | 8.31 | 8.58 | 502 | 500 | 496 | 1,532 | 1,540 | 1,685 | 27.4 cm |
| 10-12 | 0.55 | 1.89 | 2.06 | 51.93 | 52.96 | 55.99 | 10,346 | 10,342 | 10,515 | 8.0 cm |
| 10-13 | 1.47 | 0.48 | 0.41 | 66.56 | 67.43 | 69.19 | 14,592 | 14,591 | 14,758 | 7.3 cm |
| 10-14 | 1.22 | 0.35 | 0.49 | 40.99 | 39.31 | 43.66 | 17,071 | 17,057 | 17,189 | 7.0 cm |
| 10-15 | 0.60 | 0.64 | 0.81 | — | — | — | 20,115 | 20,102 | 20,224 | 7.8 cm |
| 10-16 | — | — | — | 65.14 | 64.16 | 62.39 | 24,004 | 24,002 | 24,127 | 7.2 cm |
| 10-17 | 2.63 | 1.31 | 1.43 | 4,029 | 4,030 | 4,031 | 28,333 | 28,334 | 28,449 | 7.0 cm |
| 10-18 | 1.36 | 0.68 | 0.80 | 36.36 | 36.92 | 32.58 | 2,499 | 2,509 | 2,396 | 7.4 cm |
| 10-19 | 1.54 | 0.61 | 0.55 | 93.09 | 90.55 | 86.10 | 3,120 | 3,111 | 3,000 | 7.7 cm |
| 10-20 | 1.24 | 0.25 | 0.34 | 35.93 | 35.64 | 34.61 | 3,197 | 3,201 | 3,080 | 7.1 cm |
| 10-21 | 2.10 | 0.82 | 0.93 | 37.85 | 36.76 | 35.43 | 4,509 | 4,508 | 4,392 | 6.2 cm |
| 10-22 | 1.62 | 0.90 | 0.99 | 87.62 | 87.56 | 82.98 | 6,106 | 6,113 | 6,005 | 6.9 cm |
| 10-23 | 1.05 | 0.57 | 0.64 | 62.58 | 62.19 | 57.28 | 6,427 | 6,431 | 6,295 | 7.4 cm |
| 10-24 | 1.02 | 0.49 | 0.65 | 102 | 102 | 99.50 | 877 | 873 | 1,033 | 6.6 cm |
| 10-25 | 2.72 | 1.45 | 1.56 | 46.32 | 44.86 | 41.84 | 8,910 | 8,915 | 9,079 | 6.8 cm |
| 10-26 | 1.16 | 0.56 | 0.67 | 138 | 139 | 133 | 16,191 | 16,176 | 16,349 | 7.7 cm |
| 10-27 | 1.57 | 1.15 | 0.86 | 177 | 178 | 171 | 26,458 | 26,448 | 26,681 | 9.0 cm |
| 10-28 | 1.14 | 0.63 | 0.78 | 185 | 184 | 179 | 35,598 | 35,602 | 35,856 | 7.3 cm |
| 10-29 | 3.13 | 1.68 | 1.78 | 125 | 122 | 116 | — | — | — | 7.2 cm |
| 10-30 | 1.50 | 1.25 | 1.26 | 8,106 | 8,102 | 8,111 | — | — | — | 8.1 cm |
| 10-31 | 0.89 | 0.51 | 0.62 | 135 | 136 | 128 | — | — | — | 7.8 cm |
| 11-01 | 2.89 | 1.98 | 1.92 | 179 | 180 | 175 | — | — | — | 9.8 cm |
| 11-02 | 2.35 | 1.50 | 1.60 | 284 | 281 | 275 | — | — | — | 7.4 cm |

## Files

* `before.csv`, `after.csv`, `orekit.csv`: one row per cut-off, with fit statistics and the errors
  at the cut-off, +1 h, +6 h, +1 d, +3 d and +7 d.
* `tools/`: the headless campaign drivers (the same configuration and catalogue selection as the
  GUI window run) and the table generator.
