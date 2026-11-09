# GNSS orbit and clock product selection

Requested priority, highest first:

1. IGS Final
2. IGS Rapid
3. IGS Ultra-rapid
4. Broadcast navigation

This order applies to the GPS satellite orbit and clock inputs used by SPP to
initialize Sentinel-3A orbit determination. These are GNSS satellite products,
not Sentinel-3A POEORB reference orbits. GNSSRD remains the measurement input.
The agreed merged architecture remains unchanged; product selection belongs
before the GPS satellite position and clock correction block.

## Forecast eligibility comes before priority

For a forecast cutoff, a candidate product must have been issued and available
by that cutoff and cover the required measurement epochs. Only then is priority
applied. A Final or Rapid product issued after cutoff must not displace a
cutoff-eligible lower-priority product. File validity dates alone do not establish
publication availability. Missing or unverified issuance metadata must not be
silently treated as proof of forecast eligibility.

Ultra-rapid observed and predicted segments must be distinguished and their
issue epoch recorded. For any forecast-time GNSS queries, use only the segment
available from a product issued by cutoff. The spacecraft propagation window
must still use cutoff-safe weather, Earth orientation and attitude assumptions.
POEORB remains separate scoring truth.

A retrospective comparison using later Final products is useful, but must be
labelled retrospective and kept separate from cutoff-safe forecast results.

## Quality and provenance

Within the eligible set, select the highest-priority supported product covering
the satellite and epoch. Fall back when coverage or required valid orbit/clock
information is unavailable. Reject invalid or unhealthy data. Record the selected
source and any fallback, per satellite and epoch; do not silently mix incompatible
orbit/clock conventions. Check time system, reference frame, units, satellite
identifiers, antenna/clock conventions and orbit interpolation support. Keep the
existing broadcast GPS LNAV path as the last fallback.

Each run should record product class, provider, filenames, hashes, issue time,
coverage, observed/predicted status, selected orbit and clock sources, and fallback
reasons. Explicit comparison modes may force a product tier, while preserving
cutoff eligibility for forecast-labelled runs.

## Implementation status

Implemented in `org.ursc.trajectory.gnss.products` and used by `pipeline.OdForecastPipeline`:

- SP3-c/d orbits (11-point Lagrange interpolation) and RINEX CLK 2.x/3.x clocks, gzip or
  Unix-compress (`.Z`) archives, detected by content. SP3 and CLK files of the same class,
  provider and start are paired so orbit and clock always come from one product. IGS clocks
  receive the `-2 r.v/c^2` relativistic term so every source follows the broadcast convention.
- Product class, provider and span from long (`IGS0OPSFIN_...`) and legacy (`igs/igr/igu`)
  names. Unrecognised names are reported and not used.
- **Forecast mode**: eligibility before priority. A precise product is eligible only if its
  issue time is known and not after the cutoff; issue times are a documented nominal
  latency (Ultra-rapid: observed end + 3 h; Rapid: end of day + 17 h; Final: end + 18 days)
  unless verified metadata is supplied, and `REQUIRE_VERIFIED` rejects nominal times.
  Broadcast messages are limited to Toe at or before the cutoff.
- **Reconstruction mode**: best product present; runs and OEM headers are labelled
  retrospective.
- One source serves a whole epoch (the first eligible source covering at least four of the
  satellites in view), so orbit/clock conventions are never mixed inside one SPP solve.
  Exclusions, per-source usage and fallbacks are recorded in the run result and OEM header.
- A tier can be forced for comparison runs.

Not yet verified against real IGS files: when real Final/Rapid/Ultra-rapid products for a day
with GNSSRD coverage are available, rerun the daily cases and compare with the broadcast
baseline. Final products never enter forecast initialisation merely because they are present.
