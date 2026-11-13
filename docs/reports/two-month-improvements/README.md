# Sentinel-3A, two months: the five improvements

The five improvements were first measured on seven test cut-offs, then all together in a second
61-day campaign. The settings were the same as the first campaigns: the showcase settings,
cut-off-safe forecast mode, 3 September – 2 November 2024, scored against POEORB.

| Step | Change | Decision |
|---|---|---|
| 1 | Estimate the drag coefficient; 12 h arc (phase every 60 s) | adopted (Sentinel-3A profile) |
| 2 | JB2008 drag; box-wing + measured PROQUA attitude | tested, **not** adopted (worse) |
| 3 | GPS antenna phase centres (IGS ANTEX), phase wind-up | adopted |
| 4 | Orekit yaw-compensated nadir attitude, Orekit output frames, `run` command, issue draft | adopted |
| 5 | Parallel window runs (worker processes) | adopted |

## Step-by-step on the seven test cut-offs (median error, m)

Test cut-offs: 3, 11, 19 and 27 Sep; 6, 19 and 27 Oct 2024. Each column covers the days it ran;
"(n d)" gives the count. The box-wing and no-antenna runs were stopped after three days, once the
effect was clear.

| Median over test days | Orekit carrier phase (before steps) | Step 1: Cd, 6 h arc | Step 1: Cd, 12 h arc | Steps 1+4 (no GPS antennas) | Steps 1+3+4 (all) | Step 2: + JB2008 | Step 2: + box-wing, PROQUA |
|---|---:|---:|---:|---:|---:|---:|---:|
| Phase residual RMS | 6.5 cm | 6.4 cm | 13.0 cm | 9.4 cm | 12.3 cm | 12.3 cm | 16.8 cm |
| At cut-off | 0.73 (7 d) | 1.01 (7 d) | 1.75 (7 d) | 1.29 (3 d) | 1.73 (7 d) | 1.69 (7 d) | 2.08 (3 d) |
| +1 h | 1.91 | 1.40 | 1.46 | 1.44 | 1.42 | 1.35 | 5.15 |
| +6 h | 10.78 | 5.44 | 4.39 | 4.41 | 4.20 | 7.15 | 22.38 |
| +1 day | 86.10 | 51.40 | 44.64 | 44.49 | 42.50 | 85.41 | 172 |
| +3 days | 729 | 513 | 508 | 507 | 491 | 1,045 | 1,509 |
| +7 days | 5,832 | 4,956 | 3,442 | 2,907 | 3,436 | 5,629 | 3,608 |

## The 61-day campaign (median error, m; 95th percentile in brackets)

| Horizon | Before (SPP fit) | Carrier phase on Orekit | Final (steps 1–5) | Change vs before | Change vs Orekit rebuild |
|---|---:|---:|---:|---:|---:|
| At cut-off | 1.57 (3.21) | 0.89 (2.22) | **1.77** (3.19) | +12 % | +100 % |
| +1 h | 1.93 (4.48) | 1.43 (4.68) | **2.10** (4.06) | +9 % | +47 % |
| +6 h | 9.62 (712) | 8.61 (714) | **5.57** (723) | -42 % | -35 % |
| +1 day | 94.91 (4,211) | 93.06 (4,213) | **42.79** (4,286) | -55 % | -54 % |
| +3 days | 1,023 (16,355) | 1,000 (16,404) | **445** (17,245) | -57 % | -56 % |
| +7 days | 8,208 (42,008) | 8,131 (42,118) | **10,416** (47,805) | +27 % | +28 % |

Final run: 61 cut-offs, 61 successful; median phase residual 12.2 cm; median run time 732 s per cut-off (four in parallel).

Two cut-offs (6 and 21 Sep) did not converge with Cd estimated. The pipeline now refits them with
Cd fixed, and they are included above.

## Reading the results

* **Forecasts from 6 hours to 3 days improve by about half.** Estimating the drag coefficient
  absorbs most of the density-model error of the arc:
  * +1 day: 95 m → 43 m;
  * +3 days: 1.0 km → 0.45 km.
* **The position at the cut-off and one hour later gets slightly worse.** At the cut-off the
  median is 1.8 m, against 0.9 m with the 6 h arc and fixed Cd. A 12-hour strictly dynamic arc
  cannot follow the orbit as tightly at its end, and the phase residuals grow from about 6 cm to
  about 12 cm.
* **At seven days the median is 27 % worse.**
  * The drag coefficient fitted over one half-day of atmosphere does not hold for a week near
    solar maximum.
  * For 7-day products, fixed Cd remains better. Turn off `od.estimateCd` (and use a 6 h arc) for
    that use.
* **Step 3 (GPS antennas, wind-up)** lowers the phase residuals (9.4 → 8.7 cm on the 3 paired days,
  4.9 vs 6.1 cm on 3 Sep), with forecast effects within a few per cent.
* **Step 2** made the forecast worse.
  * JB2008 doubled the +1-day error on the test days.
  * The box-wing model needs Sentinel-3A's real macro-model; its default panel areas tripled the
    +1-day error.
  * Neither is adopted.

## Daily results (m)

| Cut-off (2024) | Before: cut-off | Final: cut-off | Before: +1 day | Orekit: +1 day | Final: +1 day | Before: +7 days | Orekit: +7 days | Final: +7 days |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 09-03 | 0.61 | 1.23 | 70.57 | 68.98 | 42.50 | 1,880 | 1,896 | 2,995 |
| 09-04 | 1.15 | 1.20 | 96.82 | 97.72 | 83.33 | 8,525 | 8,542 | 9,084 |
| 09-05 | 2.81 | 2.36 | 78.08 | 77.09 | 16.02 | 16,749 | 16,756 | 20,586 |
| 09-06 | 1.53 | 2.69 | 85.20 | 81.93 | 98.70 | 21,581 | 21,592 | 21,479 |
| 09-07 | 0.82 | 2.73 | 92.03 | 92.46 | 15.62 | 28,443 | 28,468 | 32,801 |
| 09-08 | 2.62 | 1.55 | 150 | 149 | 65.54 | 32,796 | 32,837 | 36,340 |
| 09-09 | 3.19 | 1.63 | 5,842 | 5,847 | 5,999 | 39,798 | 39,830 | 45,922 |
| 09-10 | 1.85 | 1.26 | 105 | 105 | 63.65 | 6,056 | 6,054 | 827 |
| 09-11 | 0.99 | 2.74 | 94.91 | 93.06 | 21.26 | 7,014 | 6,968 | 4,090 |
| 09-12 | 1.49 | 1.19 | 361 | 357 | 338 | 4,067 | 4,009 | 3,464 |
| 09-13 | 4.15 | 1.44 | 197 | 193 | 24.69 | 5,341 | 5,372 | 13,582 |
| 09-14 | 1.72 | 1.55 | 77.84 | 73.98 | 12.41 | 11,918 | 11,951 | 15,280 |
| 09-15 | 0.47 | 2.13 | 80.53 | 80.70 | 59.05 | 17,615 | 17,649 | 18,551 |
| 09-16 | 1.95 | 1.52 | 102 | 101 | 80.60 | 24,243 | 24,282 | 25,121 |
| 09-17 | 3.32 | 3.06 | 210 | 206 | 166 | 30,938 | 30,966 | 32,516 |
| 09-18 | 0.84 | 1.41 | 2,857 | 2,860 | 2,879 | 38,571 | 38,604 | 39,380 |
| 09-19 | 0.86 | 1.25 | 48.88 | 48.10 | 45.63 | 1,148 | 1,097 | 840 |
| 09-20 | 2.09 | 1.80 | 9.00 | 8.50 | 12.11 | 1,466 | 1,423 | 552 |
| 09-21 | 1.87 | 2.38 | 4.77 | 3.22 | 8.08 | 2,856 | 2,830 | 2,874 |
| 09-22 | 0.40 | 2.02 | 4.22 | 3.49 | 42.79 | 3,611 | 3,569 | 1,884 |
| 09-23 | 0.45 | 2.36 | 44.28 | 43.23 | 27.61 | 4,470 | 4,406 | 3,820 |
| 09-24 | 2.60 | 1.24 | 79.03 | 75.57 | 19.11 | 5,146 | 5,073 | 2,862 |
| 09-25 | 1.90 | 1.08 | 133 | 127 | 46.64 | 5,526 | 5,465 | 2,521 |
| 09-26 | 1.43 | 2.73 | 81.87 | 78.58 | 45.24 | 5,412 | 5,342 | 495 |
| 09-27 | 0.98 | 1.73 | 85.28 | 84.16 | 26.05 | 5,923 | 5,832 | 3,436 |
| 09-28 | 2.29 | 1.52 | 84.82 | 80.95 | 10.44 | 6,973 | 6,883 | 4,012 |
| 09-29 | 1.70 | 1.39 | 126 | 122 | 8.43 | 7,754 | 7,676 | 3,401 |
| 09-30 | 1.65 | 2.23 | 144 | 142 | 40.36 | 1,090 | 1,191 | 8,599 |
| 10-01 | 2.44 | 1.33 | 139 | 138 | 1.31 | — | — | — |
| 10-02 | 3.07 | 1.86 | 99.20 | 93.10 | 51.79 | 22,941 | 23,111 | 28,940 |
| 10-03 | 1.06 | 1.98 | 78.52 | 70.74 | 33.73 | 34,118 | 34,311 | 38,305 |
| 10-04 | 1.07 | 3.46 | 99.94 | 93.59 | 33.14 | 46,112 | 46,368 | 51,302 |
| 10-05 | 2.58 | 1.03 | 83.40 | 77.56 | 45.78 | 54,525 | 54,776 | 59,836 |
| 10-06 | 1.70 | 1.23 | 9,236 | 9,242 | 9,263 | 62,697 | 62,908 | 63,641 |
| 10-07 | 2.47 | 3.13 | — | — | — | 8,826 | 8,612 | 94.35 |
| 10-08 | — | — | 221 | 213 | 71.86 | 5,245 | 4,994 | 231 |
| 10-09 | 1.97 | 2.15 | 71.64 | 69.81 | 113 | — | — | — |
| 10-10 | 0.97 | 0.71 | 262 | 255 | 366 | 7,891 | 7,721 | 11,748 |
| 10-11 | 8.12 | 5.36 | 502 | 496 | 180 | 1,532 | 1,685 | 24,468 |
| 10-12 | 0.55 | 1.99 | 51.93 | 55.99 | 56.60 | 10,346 | 10,515 | 6,238 |
| 10-13 | 1.47 | 1.21 | 66.56 | 69.19 | 3.45 | 14,592 | 14,758 | 12,145 |
| 10-14 | 1.22 | 2.01 | 40.99 | 43.66 | 15.81 | 17,071 | 17,189 | 16,034 |
| 10-15 | 0.60 | 1.75 | — | — | — | 20,115 | 20,224 | 21,556 |
| 10-16 | — | — | 65.14 | 62.39 | 30.42 | 24,004 | 24,127 | 25,404 |
| 10-17 | 2.63 | 1.60 | 4,029 | 4,031 | 4,096 | 28,333 | 28,449 | 31,102 |
| 10-18 | 1.36 | 1.20 | 36.36 | 32.58 | 35.08 | 2,499 | 2,396 | 107 |
| 10-19 | 1.54 | 1.93 | 93.09 | 86.10 | 6.31 | 3,120 | 3,000 | 29.28 |
| 10-20 | 1.24 | 1.77 | 35.93 | 34.61 | 32.81 | 3,197 | 3,080 | 306 |
| 10-21 | 2.10 | 1.17 | 37.85 | 35.43 | 13.76 | 4,509 | 4,392 | 2,410 |
| 10-22 | 1.62 | 1.24 | 87.62 | 82.98 | 12.31 | 6,106 | 6,005 | 3,312 |
| 10-23 | 1.05 | 3.15 | 62.58 | 57.28 | 78.72 | 6,427 | 6,295 | 1,070 |
| 10-24 | 1.02 | 2.34 | 102 | 99.50 | 30.01 | 877 | 1,033 | 3,724 |
| 10-25 | 2.72 | 1.93 | 46.32 | 41.84 | 31.67 | 8,910 | 9,079 | 12,121 |
| 10-26 | 1.16 | 1.08 | 138 | 133 | 67.71 | 16,191 | 16,349 | 18,712 |
| 10-27 | 1.57 | 5.10 | 177 | 171 | 145 | 26,458 | 26,681 | 38,864 |
| 10-28 | 1.14 | 2.83 | 185 | 179 | 88.28 | 35,598 | 35,856 | 39,252 |
| 10-29 | 3.13 | 1.81 | 125 | 116 | 13.51 | — | — | — |
| 10-30 | 1.50 | 1.37 | 8,106 | 8,111 | 8,237 | — | — | — |
| 10-31 | 0.89 | 2.88 | 135 | 128 | 27.48 | — | — | — |
| 11-01 | 2.89 | 1.32 | 179 | 175 | 49.54 | — | — | — |
| 11-02 | 2.35 | 1.92 | 284 | 275 | 209 | — | — | — |

## Files

* `final.csv`: the 61-day campaign with all improvements.
* `ablation-*.csv`: the test-day runs.
* `tools/`: the drivers and the table generator.
