# GPS product priority and the cutoff

## Priority
For every observation epoch the GPS orbits and clocks come from the best **eligible** product in this order: IGS Final, IGS Rapid, IGS Ultra-rapid, broadcast navigation. Within a class the most recently issued product wins. One product serves a whole epoch (the first that covers at least four of the satellites in view), so orbit and clock conventions are never mixed inside a single position fix. Every fallback is recorded with its reason.

## Forecast mode (cutoff-safe)
Eligibility comes before priority. A precise product is used only if it was available at the cutoff. Its issue time is the IGS nominal publication latency (Ultra-rapid: 3 h after its observed part; Rapid: 17 h after the day; Final: 18 days after) unless verified metadata is supplied; nominal times are reported as assumptions. Broadcast messages are limited to those with a reference time at or before the cutoff. Weather, Earth orientation and measured attitude are also cut off. A Final product present in the folder is therefore *not* used for a forecast issued before it existed.

## Reconstruction mode (retrospective)
The best product present is used regardless of publication time. Use it to see the best achievable orbit or to compare tiers. Runs and OEM files are labelled retrospective.

## Forcing a tier
*Force product tier* restricts a run to one class, e.g. run the same cutoff with Broadcast and with Rapid to measure the improvement from precise products.

## Precise-product details
SP3 orbits are interpolated with an 11-point Lagrange polynomial; clocks come from the matching RINEX CLK file when present (30 s or 5 min), otherwise from the SP3 file. IGS clocks omit the periodic relativistic term, which is added (-2 r.v / c^2) so every source follows the broadcast convention.
