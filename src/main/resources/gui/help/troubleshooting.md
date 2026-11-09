# Troubleshooting

**"No GNSS observation epochs between ... and the cutoff"** – the GNSSRD files do not cover the fitting arc. Check the coverage column in the Data tab or move the cutoff.

**"No GPS product is eligible for this cutoff"** – in Forecast mode every product was published after the cutoff (for example only Final products for a recent cutoff). Add broadcast navigation or Ultra-rapid files, or switch to Reconstruction mode for a retrospective run.

**"Only N usable SPP fixes"** – many epochs were rejected. The SPP table in Results shows why: too few satellites, PDOP above the limit, or large residuals. Loosen Max PDOP / Max SPP residual or check the GPS product covers the arc.

**"Batch OD did not converge"** – usually a manoeuvre inside the arc, bad fixes, or a strongly wrong drag setting. Shorten the arc, enable Estimate Cd, or inspect the OD residuals plot. No forecast is issued from a non-converged fit.

**File shown as Unrecognised** – the content did not match any supported product. IGS files must keep their standard names so the product class can be read.

**Large ITRF errors** – supply a finals2000A Earth orientation file; without it UT1-UTC and polar motion are taken as zero.

**Variance factor far from 1** – the pseudorange sigma does not match the data. Set it to about sigma x sqrt(variance factor).
