# Input data

## How files are recognised
Files are identified from their **content**, not their extension: RINEX type letter (observation, navigation, clock), SP3 header, CCSDS markers, XML product type, IERS and CSSI headers. File names are used only for metadata such as coverage dates and the IGS product class. You can add one file, many files, a folder or many folders; folders are scanned recursively. Compressed `.gz` and Unix-compress `.Z` files and Sentinel `.tgz` archives are read directly.

## Watched folders
A watched folder is monitored continuously. When new files arrive (for example copied from approved removable media) they are indexed once their size stops changing, and you get a notification. A watched folder never starts a run by itself.

## Products
- **GNSSRD** (Sentinel AUX_GNSSRD): dual-frequency GPS code and phase measured on board. The ionosphere-free code combination is used for positioning.
- **IGS Final**: most accurate GPS orbits/clocks (~2.5 cm), published 12-18 days after the data.
- **IGS Rapid**: nearly as accurate, published about 17 hours after the day.
- **IGS Ultra-rapid**: published four times a day; the first half is observed, the second half predicted. Usable for near-real-time forecasts.
- **Broadcast navigation**: the message transmitted by the satellites themselves (~1 m orbits, ~2 m clocks). Always available, lowest priority.
- **POEORB** (Sentinel AUX_POEORB): precise reference orbit, used only for display and comparison.
- **AUX_PROQUA**: measured attitude quaternions for the box-wing model.
- **finals2000A / EOP C04**: Earth orientation (polar motion, UT1-UTC). Strongly recommended.
- **CSSI space weather**: F10.7 and Ap history for the density model.
- **JPL DE440** (lnxp1990.440): exact Sun/Moon positions.
- **ICGEM .gfc**: alternative gravity field models (EIGEN-6S, EGM2008...).

## Auto-select
*Auto-select from catalog* fills the run inputs for the chosen cutoff: observations and GPS products overlapping the fitting arc, POEORB over the forecast span, attitude over the arc, and the most recent EOP, weather, DE and gravity files.
