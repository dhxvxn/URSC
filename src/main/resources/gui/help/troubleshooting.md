# Troubleshooting

**"No GNSS observation epochs between ... and the cutoff"** – the GNSSRD files do not cover the fitting arc. Check the coverage column in the Data tab or move the cutoff.

**"No GPS product is eligible for this cutoff"** – in Forecast mode every product was published after the cutoff (for example only Final products for a recent cutoff). Add broadcast navigation or Ultra-rapid files, or switch to Reconstruction mode for a retrospective run.

**"Only N usable SPP fixes (need at least 6 to seed the orbit)"** – the single-point code positions that start the orbit were mostly rejected (they only start the carrier-phase orbit determination). The SPP table in Results shows why: too few satellites, PDOP above the limit, or large residuals. Loosen Max PDOP / Max SPP residual or check the GPS product covers the arc.

**"Batch OD did not converge"** – usually a manoeuvre inside the arc, bad fixes, or a strongly wrong drag setting. Shorten the arc, enable Estimate Cd, or inspect the OD residuals plot. No forecast is issued from a non-converged fit.

**File shown as Unrecognised** – the content did not match any supported product. IGS files must keep their standard names so the product class can be read.

**Large ITRF errors** – supply a finals2000A Earth orientation file; without it UT1-UTC and polar motion are taken as zero.

**Variance factor far from 1** – the pseudorange sigma does not match the data. Set it to about sigma x sqrt(variance factor).

**"Carrier-phase orbit determination is not usable"** – the Orekit carrier-phase fit kept fewer than 100 phase observations or its phase residuals exceeded 1 m. Check that the GNSSRD files hold dual-frequency phase (L1/L2), that the GPS products cover the arc, and that the spacecraft's centre of mass (Spacecraft settings) matches the antenna offset in the GNSSRD header.

**"The JPL DE440 ephemeris (lnxp1990.440) was not found"** – the Orekit engine needs DE440 for the Sun and Moon. Run the program from the project folder (it uses data/ephemeris/lnxp1990.440) or select the file as the DE440 input.
