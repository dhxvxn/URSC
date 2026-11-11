# Getting started

URSC determines a satellite's orbit from its own GPS measurements and forecasts it, writing the result as a CCSDS OEM file. Everything runs on this computer; no internet connection is used.

## The three things you must provide

1. **GNSS observations (GNSSRD)** – the spacecraft's GPS receiver data, e.g. Sentinel `AUX_GNSSRD` archives.
2. **A GPS orbit and clock product** – IGS Final, Rapid or Ultra-rapid (SP3 + CLK) and/or broadcast navigation (RINEX nav). Add all you have; the best eligible one is chosen automatically.
3. **The UTC cutoff** – when the forecast is issued. Only data up to the cutoff is fitted.

Everything else has a generic default (a 1000 kg cannonball spacecraft, bundled EGM96 gravity, constant space weather, analytic Sun/Moon). The run record and the OEM header list every default that was used.

## A first run

1. **Data** tab: click *Add files / folders* and select your data folders, or *Watch folder* for a folder that keeps receiving new files. Files are identified by their content.
2. **Run** tab: set the cutoff, then press *Auto-select from catalog* to fill the inputs that cover it. Check the red messages (missing inputs) disappear.
3. Optionally open the setting groups. Every setting has an ⓘ button explaining what it does and how to choose.
4. Press **Run**. Progress and log appear live.
5. **Results** tab: the orbit on the 3D globe and 2D map, diagnostics plots, uncertainty, and *Download OEM*.

## Workflows

- **OD + Forecast**: GNSS positioning (SPP) at each epoch of the arc, a covariance-weighted batch least-squares fit, then a numerical forecast with formal covariance.
- **Propagate from a state**: no fitting; start from a typed state or a state taken from an OEM or POEORB file. Useful to study the effect of each force model.

## Startup defaults: the tested Sentinel-3A window
URSC starts with the settings of the tested campaign: Sentinel-3A, cutoff 2024-09-03 05:59:42 UTC
(06:00:00 GPS), a six-hour arc sampled every 300 s, a seven-day forecast, 1,130 kg with 8 m² drag
and radiation areas, NRLMSISE-00 density, EGM96 70×70 gravity, relative tolerance 1e-13 and Ap = 5
after the cutoff. When you start URSC from the project folder for the first time, the project's
`data` folder and the tested Earth orientation and space-weather files are added to the catalog in
the background, and the inputs for the default cutoff are selected automatically. Every value can be
changed. **Reset** returns to these defaults; **Generic** loads the plain library defaults
(a 1,000 kg cannonball, no window).

## Window runs
A window run repeats orbit determination and a forecast for every day in a date range. Each day has
its own cutoff (same time of day) and its own inputs, picked from the catalog. Set the first date,
last date, cutoff time and step under **Run → Test window**, then press **Run whole window**. The
**Window** tab shows the number of forecasts issued, failures, days without data and warnings, trend
charts across the window, and a per-day table; click a row to open that day in Results. With the
default settings one cutoff takes about two minutes, so the 61-day window takes about two hours.
