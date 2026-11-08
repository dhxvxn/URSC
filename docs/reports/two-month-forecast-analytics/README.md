# Two-month GNSS-driven forecast accuracy baseline

This experiment uses the user-approved [merged architecture](../../MERGED_ARCHITECTURE.md).
Daily cases cover 3 September–2 November 2024, inclusive (61 cutoffs).
The results measure absolute three-dimensional position error in metres, not a percentage.

## Architecture and input routing

| Architecture block | Inputs and use | Saved output |
|---|---|---|
| Input validation and time alignment | Sentinel-3A GNSSRD archive; GPS-labelled epochs converted to UTC; retain GPS 00:00–06:00; broadcast navigation limited to cutoff | Input hashes in `forecast-manifest.json` |
| GPS satellite positions and clock corrections | Daily RINEX broadcast navigation, GPS LNAV records; satellite positions and clocks used to solve spacecraft pseudoranges | Internal SPP computations |
| SPP position and receiver clock | GNSS dual-frequency code observations and satellite states | `results/DATE/spp.csv` |
| Position fixes, covariance and solution status | Converged SPP solutions, range sigma 5 m, full position covariance | 73 expected fixes per six-hour arc; unsuccessful fixes are excluded |
| ITRF to GCRF | Positions and rotated covariance; completed-day Earth orientation limited to cutoff | Inertial position observations supplied to OD |
| Orbit Estimation and Prediction Engine | Covariance-weighted batch OD; gravity, drag, solar and Earth radiation, tides, relativity; numerical integration | `fit.properties`, then cutoff and future states in `forecast.csv` |
| Separate accuracy evaluation | Issued forecasts and POEORB truth, in a separate Java process | `endpoint-errors.csv`, `daily-errors.csv`, `daily-table.md`, `summary.json` |

The merged engine is a conceptual grouping of the existing OD, force-model and
propagation implementations. No sequential filtering, monitoring service, maneuver
investigation, recovery loop or planned-maneuver input is included. Internal fitting
residuals remain necessary for least squares.

The complete diagram also has formal state covariance outputs. This **endpoint
accuracy benchmark does not evaluate or export propagated covariance**; it uses
SPP covariance to weight OD and evaluates the resulting position forecasts.
It must not be presented as a validated uncertainty-calibration experiment.
PROQUA/NAVATT measured future attitudes are not used: this baseline uses fixed
effective drag and radiation areas. IGS final/ultra-rapid SP3/CLK are not required
by the broadcast-navigation path evaluated here.

## Frozen forecast protocol

Each cutoff is **05:59:42 UTC**, equivalent to 06:00:00 GPS in 2024. A six-hour
measurement arc precedes it, sampled every 300 seconds. Batch OD estimates the
six Cartesian initial-state components, with at most 12 iterations. Cd is fixed.
Only a converged OD solution issues a forecast. Nonconvergence is a reported
failure, never a zero error or a truth-based replacement state.

| Setting | Value |
|---|---|
| Mass | 1,130 kg |
| Drag coefficient / radiation coefficient | 2.2 / 1.3 |
| Effective drag / radiation area | 8 / 8 m² |
| Earth gravity | EGM96, degree and order 70 |
| Density model | NRLMSISE-00 |
| Other forces | Analytic Sun/Moon attraction, SRP with eclipse, Earth radiation, solid/ocean tides, relativity |
| Numerical integrator | Dormand–Prince 5(4), absolute tolerance 1e-10, relative tolerance 1e-13, max step 30 s |
| Forecast length / scoring endpoints | 7 days / exact +1 and +7 days |
| Solar flux | Persistence of cutoff-eligible completed-day input |
| Solar mean | Trailing past-only average, up to 81 days; no centered future average |
| Future geomagnetic activity | Nominal scenario, Ap = 5; no actual future storm input |
| Earth orientation | Cutoff-eligible completed-day row persisted into future |

These area and mass values describe the frozen baseline, not an implementation of
the guide's full attitude-driven eight-plate spacecraft model. No parameters have
been adjusted using the future POEORB scores in this experiment.

## Forecast integrity and limits

`DailyForecast.java` accepts observations, navigation, output directory and date;
it accepts no truth file. `ScoreForecasts.java` reads reference orbits separately.
Scoring completed independent forecasts can run while other daily forecasts are
still being computed. Scored results do not feed back into the forecast processes.

This is a **historical measurement-cutoff replay**. Product publication vintages
and actual broadcast receipt times are unverified. GNSSRD products were generated
after their measurement dates; historical finalized weather/EOP archives are
restricted by measurement dates but have not been proven to be the exact versions
available at issuance. Consequently these results do not establish fully
operational, as-issued real-time performance.

Reference coverage extends through 4 November. The +7-day endpoints for cutoffs
29 October–2 November require 5–9 November POEORB; those scores are unavailable.
The +1-day endpoints are covered. Error tables distinguish missing references,
OD failures and successful forecasts.

Exact irregular observation epochs initially caused repeated full-arc integrations.
The numerical propagator now samples these timestamps in one dense-output pass,
without rounding epochs or changing forces/tolerances. Independent sampling tests
are included in the 120 passing tests. See `sampling-optimization.json` for hashes.
One transient class-loading failure during rebuilding was retried with the same
settings; it is not a model-accuracy failure. No rebuild should run concurrently
with these jobs.

## Reproduction

The manifest records the input paths and SHA-256 hashes, source hashes, forcing-file
hashes and harness hash. The sampling optimization is recorded separately because
it followed the initial manifest freeze. Paths currently reference this workspace;
restore files by their recorded hashes before rerunning elsewhere.

From the repository root, with the configured JDK and Maven:

```bash
mvn -B -ntp package
javac -cp target/classes -d target/daily-forecast \
  docs/reports/two-month-forecast-analytics/DailyForecast.java \
  docs/reports/two-month-forecast-analytics/ScoreForecasts.java
python docs/reports/two-month-forecast-analytics/run_forecasts.py
# The coordinator excludes the September 3 pilot; run that date with DailyForecast
# using its observation and navigation paths from forecast-manifest.json.
java -Xmx512m -cp target/classes:target/daily-forecast \
  org.ursc.trajectory.app.command.ScoreForecasts
python docs/reports/two-month-forecast-analytics/render_tables.py
```

`run_extra_forecasts.py` was a supplementary fifth worker and controlled retry,
not a different forecast configuration. Re-running the coordinator skips already
saved forecasts. Clear or relocate outputs to perform a fresh experiment.

## Completed run

All 61 cases finished: 53 forecasts issued and 8 OD nonconvergences. See
[final results](RESULTS.md). The output verification passed with no missing daily
samples or unexpected missing scores. During overlapping queue completion, the
26 October forecast ran twice with identical settings; its two saved outputs
have identical SHA-256 hashes (`eccb068526de4869b97a1697e331fc947f5037fd07be1be78faced8a82104c44`).
Duplicate execution does not add an independent case to the statistics.

The September 3 GNSS archive was published in commit `423b7cb`; the selected
61-day GNSS/broadcast input window is complete on the remote branch. This report is archived in GitHub. The newly downloaded bulk POEORB references
remain local evaluation inputs; publication of every uploaded reference/attitude
file has not been verified. The Java harnesses reference implementation changes
in the analysis workspace that are not all published in this report commit;
this archive preserves results and provenance, not a standalone runnable release.

## Next comparison

The requested next input policy is [IGS Final → Rapid → Ultra-rapid → broadcast](../../GNSS_PRODUCT_PRIORITY.md), applied only after forecast-cutoff eligibility checks. This report remains the broadcast baseline.
